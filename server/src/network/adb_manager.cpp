#include "adb_manager.h"
#include "discovery.h"
#include "../logger.h"

#include <Windows.h>
#include <chrono>
#include <cstdlib>
#include <filesystem>
#include <sstream>

namespace rosk {

namespace {

constexpr const char* PLATFORM_TOOLS_URL =
    "https://dl.google.com/android/repository/platform-tools-latest-windows.zip";

std::string getenvString(const char* name) {
    char* value = nullptr;
    size_t len = 0;
    if (_dupenv_s(&value, &len, name) != 0 || value == nullptr) {
        return "";
    }
    std::string result(value);
    free(value);
    return result;
}

std::string trimToken(std::string value) {
    size_t start = value.find_first_not_of(" \t\r\n");
    if (start == std::string::npos) return "";
    size_t end = value.find_first_of(" \t\r\n", start);
    if (end == std::string::npos) return value.substr(start);
    return value.substr(start, end - start);
}

bool containsTetherInterface(const std::string& line) {
    return line.find("rndis") != std::string::npos || line.find("usb") != std::string::npos;
}

} // namespace

AdbManager::AdbManager() = default;

AdbManager::~AdbManager() {
    stop();
}

void AdbManager::start() {
    if (running_) return;
    running_ = true;
    workerThread_ = std::thread(&AdbManager::workerLoop, this);
    LOG_INFO("ADB", "ADB automation started");
}

void AdbManager::stop() {
    running_ = false;
    if (workerThread_.joinable()) {
        workerThread_.join();
    }
}

std::string AdbManager::getBundledAdbPath() const {
    std::string localAppData = getenvString("LOCALAPPDATA");
    if (localAppData.empty()) return "";
    return localAppData + "\\RemoteOsuKeyboard\\platform-tools\\adb.exe";
}

std::string AdbManager::quotePath(const std::string& path) const {
    return "\"" + path + "\"";
}

std::string AdbManager::getAdbPath() {
    char found[MAX_PATH] = {};
    DWORD len = SearchPathA(nullptr, "adb.exe", nullptr, MAX_PATH, found, nullptr);
    if (len > 0 && len < MAX_PATH && std::filesystem::exists(found)) {
        return found;
    }

    std::string localAdb = getBundledAdbPath();
    if (!localAdb.empty() && std::filesystem::exists(localAdb)) {
        return localAdb;
    }

    if (std::filesystem::exists("platform-tools\\adb.exe")) {
        return "platform-tools\\adb.exe";
    }

    return "";
}

bool AdbManager::downloadAdbIfNeeded() {
    if (!getAdbPath().empty()) return true;

    std::string localAppData = getenvString("LOCALAPPDATA");
    if (localAppData.empty()) {
        LOG_ERR("ADB", "LOCALAPPDATA is not available; cannot install platform-tools");
        return false;
    }

    LOG_INFO("ADB", "ADB not found. Downloading Android SDK Platform Tools...");

    std::string command =
        "powershell -NoProfile -ExecutionPolicy Bypass -Command "
        "\"$ErrorActionPreference='Stop'; "
        "$ProgressPreference='SilentlyContinue'; "
        "$root=Join-Path $env:LOCALAPPDATA 'RemoteOsuKeyboard'; "
        "New-Item -ItemType Directory -Force -Path $root | Out-Null; "
        "$zip=Join-Path $root 'platform-tools-latest-windows.zip'; "
        "Invoke-WebRequest -Uri '" + std::string(PLATFORM_TOOLS_URL) + "' -OutFile $zip; "
        "Expand-Archive -LiteralPath $zip -DestinationPath $root -Force; "
        "Remove-Item -LiteralPath $zip -Force\"";

    std::string output;
    if (!runCommand(command, output)) {
        LOG_ERR("ADB", "Failed to download platform-tools: " + output);
        return false;
    }

    if (getAdbPath().empty()) {
        LOG_ERR("ADB", "platform-tools download finished, but adb.exe was not found");
        return false;
    }

    LOG_INFO("ADB", "ADB installed successfully");
    return true;
}

bool AdbManager::runCommand(const std::string& cmd, std::string& outResult) {
    HANDLE hReadPipe = nullptr;
    HANDLE hWritePipe = nullptr;
    SECURITY_ATTRIBUTES sa = { sizeof(SECURITY_ATTRIBUTES), nullptr, TRUE };

    if (!CreatePipe(&hReadPipe, &hWritePipe, &sa, 0)) return false;

    STARTUPINFOA si = {};
    si.cb = sizeof(STARTUPINFOA);
    si.dwFlags = STARTF_USESTDHANDLES | STARTF_USESHOWWINDOW;
    si.hStdOutput = hWritePipe;
    si.hStdError = hWritePipe;
    si.wShowWindow = SW_HIDE;

    PROCESS_INFORMATION pi = {};

    std::string cmdStr = "cmd.exe /c " + cmd;
    std::vector<char> cmdBuf(cmdStr.begin(), cmdStr.end());
    cmdBuf.push_back(0);

    if (!CreateProcessA(nullptr, cmdBuf.data(), nullptr, nullptr, TRUE, CREATE_NO_WINDOW, nullptr, nullptr, &si, &pi)) {
        CloseHandle(hReadPipe);
        CloseHandle(hWritePipe);
        return false;
    }

    CloseHandle(hWritePipe);

    char buffer[1024];
    DWORD bytesRead = 0;
    outResult.clear();

    while (ReadFile(hReadPipe, buffer, sizeof(buffer) - 1, &bytesRead, nullptr) && bytesRead > 0) {
        buffer[bytesRead] = 0;
        outResult += buffer;
    }

    CloseHandle(hReadPipe);
    WaitForSingleObject(pi.hProcess, INFINITE);

    DWORD exitCode = 0;
    GetExitCodeProcess(pi.hProcess, &exitCode);

    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);

    return exitCode == 0;
}

std::string AdbManager::getLocalIpForSubnet(const std::string& targetIp) {
    if (targetIp.empty()) return "";

    size_t lastDot = targetIp.find_last_of('.');
    if (lastDot == std::string::npos) return "";

    std::string targetPrefix = targetIp.substr(0, lastDot + 1);
    auto ips = DiscoveryService::getLocalIPs();
    for (const auto& ip : ips) {
        if (ip.find(targetPrefix) == 0) {
            return ip;
        }
    }
    return "";
}

void AdbManager::detectTetheringAndBroadcast() {
    std::string adbPath = getAdbPath();
    if (adbPath.empty()) return;

    std::string output;
    if (!runCommand(quotePath(adbPath) + " shell ip route", output)) {
        return;
    }

    std::istringstream iss(output);
    std::string line;
    while (std::getline(iss, line)) {
        if (!containsTetherInterface(line)) continue;

        size_t srcPos = line.find("src ");
        if (srcPos == std::string::npos) continue;

        std::string phoneIp = trimToken(line.substr(srcPos + 4));
        std::string pcIp = getLocalIpForSubnet(phoneIp);
        if (pcIp.empty()) {
            LOG_WARN("ADB", "Detected tether phone IP " + phoneIp + ", but no matching PC adapter IP was found");
            continue;
        }

        std::string command = quotePath(adbPath)
            + " shell am broadcast -a com.rosk.remoteosukey.TETHER_READY"
            + " -p com.rosk.remoteosukey"
            + " --es pc_ip " + pcIp
            + " --es phone_ip " + phoneIp;

        std::string ignored;
        if (runCommand(command, ignored) && pcIp != lastBroadcastPcIp_) {
            lastBroadcastPcIp_ = pcIp;
            LOG_INFO("ADB", "USB tethering PC IP broadcast to Android: " + pcIp);
        }
    }
}

void AdbManager::workerLoop() {
    if (!downloadAdbIfNeeded()) {
        LOG_ERR("ADB", "ADB automation stopped because adb.exe is unavailable");
        running_ = false;
        return;
    }

    std::string adbPath = getAdbPath();
    std::string adb = quotePath(adbPath);
    std::string output;
    runCommand(adb + " start-server", output);

    while (running_) {
        output.clear();
        if (!runCommand(adb + " reverse tcp:7221 tcp:7221", output)) {
            if (output.find("unauthorized") != std::string::npos) {
                LOG_WARN("ADB", "Android device is unauthorized. Accept the USB debugging prompt on the phone.");
            }
        }

        detectTetheringAndBroadcast();

        for (int i = 0; i < 20 && running_; ++i) {
            std::this_thread::sleep_for(std::chrono::milliseconds(100));
        }
    }

    output.clear();
    runCommand(adb + " reverse --remove tcp:7221", output);
}

} // namespace rosk
