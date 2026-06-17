#include "config.h"
#include "logger.h"
#include "input_simulator.h"
#include <fstream>
#include <sstream>
#include <filesystem>
#include <Windows.h>
#include <ShlObj.h>

namespace rosk {

std::string Config::getConfigDir() {
    wchar_t* appData = nullptr;
    if (SUCCEEDED(SHGetKnownFolderPath(FOLDERID_RoamingAppData, 0, nullptr, &appData))) {
        std::wstring wpath(appData);
        CoTaskMemFree(appData);

        // Convert wchar_t to string
        std::string path;
        path.reserve(wpath.size());
        for (wchar_t c : wpath) {
            path.push_back(static_cast<char>(c));
        }
        return path + "\\RemoteOsuKeyboard";
    }
    return ".";
}

std::string Config::getConfigPath() {
    return getConfigDir() + "\\config.json";
}

bool Config::load() {
    std::string path = getConfigPath();
    std::ifstream file(path);
    if (!file.is_open()) {
        LOG_INFO("Config", "No config file found, using defaults");
        return false;
    }

    std::ostringstream ss;
    ss << file.rdbuf();
    std::string json = ss.str();

    // Simple JSON parsing (avoiding external dependency)
    auto readInt = [&](const std::string& key) -> int {
        size_t pos = json.find("\"" + key + "\"");
        if (pos == std::string::npos) return -1;
        pos = json.find(':', pos);
        if (pos == std::string::npos) return -1;
        pos++;
        while (pos < json.size() && (json[pos] == ' ' || json[pos] == '\t')) pos++;
        return std::stoi(json.substr(pos));
    };

    auto readStr = [&](const std::string& key) -> std::string {
        size_t pos = json.find("\"" + key + "\"");
        if (pos == std::string::npos) return "";
        size_t valStart = json.find('"', json.find(':', pos) + 1);
        if (valStart == std::string::npos) return "";
        valStart++;
        size_t valEnd = json.find('"', valStart);
        if (valEnd == std::string::npos) return "";
        return json.substr(valStart, valEnd - valStart);
    };

    auto readBool = [&](const std::string& key, bool defaultVal) -> bool {
        size_t pos = json.find("\"" + key + "\"");
        if (pos == std::string::npos) return defaultVal;
        pos = json.find(':', pos);
        if (pos == std::string::npos) return defaultVal;
        return json.find("true", pos) < json.find('\n', pos);
    };

    // Read key names and convert to VK codes
    std::string key1Name = readStr("key1");
    std::string key2Name = readStr("key2");
    if (!key1Name.empty()) key1 = InputSimulator::nameToVKCode(key1Name);
    if (!key2Name.empty()) key2 = InputSimulator::nameToVKCode(key2Name);

    int val;
    val = readInt("udpPort"); if (val > 0) udpPort = static_cast<uint16_t>(val);
    val = readInt("tcpPort"); if (val > 0) tcpPort = static_cast<uint16_t>(val);

    bluetoothEnabled = readBool("bluetoothEnabled", true);
    showKeyPresses = readBool("showKeyPresses", true);
    showLatency = readBool("showLatency", true);

    LOG_INFO("Config", "Loaded config from " + path);
    LOG_INFO("Config", "Key1: " + InputSimulator::vkCodeToName(key1)
                      + ", Key2: " + InputSimulator::vkCodeToName(key2));
    return true;
}

bool Config::save() const {
    std::string dir = getConfigDir();

    // Create directory if it doesn't exist
    try {
        std::filesystem::create_directories(dir);
    } catch (const std::exception& e) {
        LOG_ERR("Config", "Failed to create config directory: " + std::string(e.what()));
        return false;
    }

    std::string path = getConfigPath();
    std::ofstream file(path);
    if (!file.is_open()) {
        LOG_ERR("Config", "Failed to open config file for writing: " + path);
        return false;
    }

    file << "{\n"
         << "  \"key1\": \"" << InputSimulator::vkCodeToName(key1) << "\",\n"
         << "  \"key2\": \"" << InputSimulator::vkCodeToName(key2) << "\",\n"
         << "  \"udpPort\": " << udpPort << ",\n"
         << "  \"tcpPort\": " << tcpPort << ",\n"
         << "  \"bluetoothEnabled\": " << (bluetoothEnabled ? "true" : "false") << ",\n"
         << "  \"showKeyPresses\": " << (showKeyPresses ? "true" : "false") << ",\n"
         << "  \"showLatency\": " << (showLatency ? "true" : "false") << "\n"
         << "}\n";

    file.close();
    LOG_INFO("Config", "Saved config to " + path);
    return true;
}

} // namespace rosk
