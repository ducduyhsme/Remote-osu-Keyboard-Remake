#include "wifi_direct_server.h"
#include "../logger.h"

// WinRT headers
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Foundation.Collections.h>
#include <winrt/Windows.Devices.WiFiDirect.h>
#include <winrt/Windows.Devices.Enumeration.h>
#include <winrt/Windows.Networking.h>
#include <winrt/Windows.Networking.Sockets.h>
#include <winrt/Windows.Storage.Streams.h>
#include <winrt/Windows.Security.Credentials.h>

#include <WS2tcpip.h>
#include <fstream>
#include <sstream>
#include <random>
#include <CommCtrl.h>

#pragma comment(lib, "windowsapp.lib")

using namespace winrt;
using namespace Windows::Devices::WiFiDirect;
using namespace Windows::Devices::Enumeration;
using namespace Windows::Networking;
using namespace Windows::Foundation;

namespace rosk {

// ── Trusted devices file ──
static const char* TRUSTED_DEVICES_FILE = "wifi_direct_trusted.txt";

WifiDirectServer::WifiDirectServer() {
    loadTrustedDevices();
}

WifiDirectServer::~WifiDirectServer() {
    stop();
}

void WifiDirectServer::loadTrustedDevices() {
    std::lock_guard<std::mutex> lock(trustedMutex_);
    trustedDevices_.clear();
    std::ifstream f(TRUSTED_DEVICES_FILE);
    std::string line;
    while (std::getline(f, line)) {
        if (!line.empty()) {
            trustedDevices_.insert(line);
        }
    }
    if (!trustedDevices_.empty()) {
        LOG_INFO("WifiDirect", "Loaded " + std::to_string(trustedDevices_.size()) + " trusted device(s)");
    }
}

void WifiDirectServer::saveTrustedDevices() {
    std::lock_guard<std::mutex> lock(trustedMutex_);
    std::ofstream f(TRUSTED_DEVICES_FILE);
    for (const auto& id : trustedDevices_) {
        f << id << "\n";
    }
}

void WifiDirectServer::addTrustedDevice(const std::string& deviceId) {
    {
        std::lock_guard<std::mutex> lock(trustedMutex_);
        trustedDevices_.insert(deviceId);
    }
    saveTrustedDevices();
    LOG_INFO("WifiDirect", "Trusted device added: " + deviceId);
}

bool WifiDirectServer::isTrustedDevice(const std::string& deviceId) const {
    std::lock_guard<std::mutex> lock(trustedMutex_);
    return trustedDevices_.count(deviceId) > 0;
}

std::string WifiDirectServer::getGoAddress() const {
    std::lock_guard<std::mutex> lock(goAddrMutex_);
    return goAddress_;
}

// ── Generate 6-digit pairing code ──
static std::string generatePairingCode() {
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<int> dist(100000, 999999);
    return std::to_string(dist(gen));
}

// ── Win32 Pairing Dialog ──
struct PairingDialogData {
    std::string deviceName;
    std::string pairingCode;
    bool accepted = false;
    bool trusted = false;
};

static INT_PTR CALLBACK PairingDlgProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    static PairingDialogData* data = nullptr;

    switch (msg) {
    case WM_INITDIALOG: {
        data = reinterpret_cast<PairingDialogData*>(lParam);
        
        // Center dialog
        RECT rc;
        GetWindowRect(hwnd, &rc);
        int w = rc.right - rc.left;
        int h = rc.bottom - rc.top;
        int sx = GetSystemMetrics(SM_CXSCREEN);
        int sy = GetSystemMetrics(SM_CYSCREEN);
        SetWindowPos(hwnd, HWND_TOPMOST, (sx - w) / 2, (sy - h) / 2, 0, 0, SWP_NOSIZE);

        // Set text
        std::wstring deviceW(data->deviceName.begin(), data->deviceName.end());
        SetDlgItemTextW(hwnd, 101, deviceW.c_str());

        std::wstring codeW(data->pairingCode.begin(), data->pairingCode.end());
        SetDlgItemTextW(hwnd, 102, codeW.c_str());

        return TRUE;
    }
    case WM_COMMAND:
        switch (LOWORD(wParam)) {
        case IDOK: // Accept
            data->accepted = true;
            data->trusted = (IsDlgButtonChecked(hwnd, 103) == BST_CHECKED);
            EndDialog(hwnd, IDOK);
            return TRUE;
        case IDCANCEL: // Cancel
            data->accepted = false;
            EndDialog(hwnd, IDCANCEL);
            return TRUE;
        }
        break;
    case WM_CLOSE:
        data->accepted = false;
        EndDialog(hwnd, IDCANCEL);
        return TRUE;
    }
    return FALSE;
}

// Helper struct for building dialog template in memory (must be at namespace scope for templates)
struct MemDialogTemplate {
    std::vector<BYTE> buf;
    
    void align(int boundary) {
        while (buf.size() % boundary != 0) buf.push_back(0);
    }
    
    template<typename T>
    void write(T val) {
        auto* p = reinterpret_cast<const BYTE*>(&val);
        buf.insert(buf.end(), p, p + sizeof(T));
    }
    
    void writeWString(const wchar_t* str) {
        while (*str) {
            write<WORD>(*str);
            str++;
        }
        write<WORD>(0);
    }

    void addControl(DWORD style, DWORD exStyle, short x, short y, short cx, short cy,
                    WORD id, const wchar_t* className, const wchar_t* text) {
        align(4);
        write<DWORD>(style);
        write<DWORD>(exStyle);
        write<short>(x);
        write<short>(y);
        write<short>(cx);
        write<short>(cy);
        write<WORD>(id);
        writeWString(className);
        writeWString(text);
        write<WORD>(0); // creation data
    }
};

// Build a dialog template in memory
static std::vector<BYTE> buildPairingDialogTemplate() {
    MemDialogTemplate t;

    // DLGTEMPLATE
    t.write<DWORD>(DS_MODALFRAME | DS_CENTER | WS_POPUP | WS_CAPTION | WS_SYSMENU | DS_SETFONT); // style
    t.write<DWORD>(WS_EX_TOPMOST); // dwExtendedStyle
    t.write<WORD>(6);  // cdit (number of controls)
    t.write<short>(0);  // x
    t.write<short>(0);  // y
    t.write<short>(220); // cx
    t.write<short>(140); // cy
    t.write<WORD>(0);   // menu
    t.write<WORD>(0);   // class
    t.writeWString(L"Wi-Fi Direct Pairing Request"); // title
    t.write<WORD>(9);   // font size
    t.writeWString(L"Segoe UI"); // font name

    // "Device:" label
    t.addControl(WS_CHILD | WS_VISIBLE | SS_LEFT, 0,
               10, 10, 200, 12, 0xFFFF, L"STATIC", L"Connection request from:");

    // Device name (id=101)
    t.addControl(WS_CHILD | WS_VISIBLE | SS_CENTER | SS_SUNKEN, 0,
               10, 25, 200, 14, 101, L"STATIC", L"");

    // "Pairing Code:" label
    t.addControl(WS_CHILD | WS_VISIBLE | SS_LEFT, 0,
               10, 50, 200, 12, 0xFFFF, L"STATIC", L"Pairing Code (verify on your phone):");

    // Code display (id=102)
    t.addControl(WS_CHILD | WS_VISIBLE | SS_CENTER, 0,
               50, 65, 120, 20, 102, L"STATIC", L"000000");

    // Trust checkbox (id=103)
    t.addControl(WS_CHILD | WS_VISIBLE | BS_AUTOCHECKBOX, 0,
               10, 95, 200, 14, 103, L"BUTTON", L"Trust this device (skip code next time)");

    // Accept button (IDOK)
    t.addControl(WS_CHILD | WS_VISIBLE | BS_DEFPUSHBUTTON, 0,
               40, 115, 60, 18, IDOK, L"BUTTON", L"Accept");

    // Cancel button (IDCANCEL)
    t.addControl(WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON, 0,
               120, 115, 60, 18, IDCANCEL, L"BUTTON", L"Cancel");

    return t.buf;
}

WifiDirectServer::PairingResult WifiDirectServer::showPairingDialog(
    const std::string& deviceName, const std::string& pairingCode) 
{
    PairingDialogData data;
    data.deviceName = deviceName;
    data.pairingCode = pairingCode;

    auto tmpl = buildPairingDialogTemplate();

    DialogBoxIndirectParamW(
        GetModuleHandle(nullptr),
        reinterpret_cast<LPCDLGTEMPLATEW>(tmpl.data()),
        nullptr, // No parent
        PairingDlgProc,
        reinterpret_cast<LPARAM>(&data)
    );

    PairingResult result;
    result.accepted = data.accepted;
    result.trusted = data.trusted;
    return result;
}

// ── Main Wi-Fi Direct advertisement loop ──
bool WifiDirectServer::start() {
    if (running_) return true;
    running_ = true;

    LOG_INFO("WifiDirect", "Starting Wi-Fi Direct server...");

    advertiseThread_ = std::thread(&WifiDirectServer::advertiseLoop, this);
    return true;
}

void WifiDirectServer::stop() {
    if (!running_) return;
    running_ = false;
    clientConnected_ = false;
    groupFormed_ = false;

    if (listenSocket_ != INVALID_SOCKET) {
        closesocket(listenSocket_);
        listenSocket_ = INVALID_SOCKET;
    }

    if (advertiseThread_.joinable()) advertiseThread_.join();
    if (tcpAcceptThread_.joinable()) tcpAcceptThread_.join();
    if (tcpClientThread_.joinable()) tcpClientThread_.join();

    LOG_INFO("WifiDirect", "Wi-Fi Direct server stopped");
}

void WifiDirectServer::advertiseLoop() {
    try {
        winrt::init_apartment(winrt::apartment_type::multi_threaded);
    } catch (...) {
        // Already initialized
    }

    while (running_) {
        try {
            LOG_INFO("WifiDirect", "Creating WiFiDirectAdvertisementPublisher...");

            WiFiDirectAdvertisementPublisher publisher;
            publisher.Advertisement().IsAutonomousGroupOwnerEnabled(true);
            publisher.Advertisement().ListenStateDiscoverability(
                WiFiDirectAdvertisementListenStateDiscoverability::Normal);

            LOG_INFO("WifiDirect", "Creating WiFiDirectConnectionListener...");

            WiFiDirectConnectionListener listener;

            // Handle connection requests
            auto connectionToken = listener.ConnectionRequested(
                [this](WiFiDirectConnectionListener const&,
                       WiFiDirectConnectionRequestedEventArgs const& args) {

                auto request = args.GetConnectionRequest();
                auto deviceInfo = request.DeviceInformation();

                // Convert device name
                std::string deviceName;
                auto nameHstr = deviceInfo.Name();
                for (auto c : nameHstr) {
                    deviceName += static_cast<char>(c);
                }

                std::string deviceId;
                auto idHstr = deviceInfo.Id();
                for (auto c : idHstr) {
                    deviceId += static_cast<char>(c);
                }

                LOG_INFO("WifiDirect", "Connection request from: " + deviceName + " (ID: " + deviceId + ")");

                bool autoAccept = isTrustedDevice(deviceId);
                std::string pairingCode = generatePairingCode();

                if (!autoAccept) {
                    LOG_INFO("WifiDirect", "Showing pairing dialog with code: " + pairingCode);
                    auto result = showPairingDialog(deviceName, pairingCode);

                    if (!result.accepted) {
                        LOG_INFO("WifiDirect", "Pairing rejected by user");
                        return;
                    }

                    if (result.trusted) {
                        addTrustedDevice(deviceId);
                    }

                    LOG_INFO("WifiDirect", "Pairing accepted by user");
                } else {
                    LOG_INFO("WifiDirect", "Auto-accepting trusted device: " + deviceName);
                }

                // Accept the connection
                try {
                    LOG_INFO("WifiDirect", "Calling WiFiDirectDevice::FromIdAsync...");

                    // Set up custom pairing to auto-accept
                    auto connectionParams = WiFiDirectConnectionParameters();
                    auto pairingKinds = DevicePairingKinds::ConfirmOnly | 
                                       DevicePairingKinds::ProvidePin |
                                       DevicePairingKinds::ConfirmPinMatch |
                                       DevicePairingKinds::DisplayPin;

                    connectionParams.PreferredPairingProcedure(
                        WiFiDirectPairingProcedure::GroupOwnerNegotiation);

                    auto deviceAccessStatus = DeviceAccessInformation::CreateFromId(idHstr).CurrentStatus();
                    LOG_INFO("WifiDirect", "Device access status: " + std::to_string(static_cast<int>(deviceAccessStatus)));

                    auto customPairing = deviceInfo.Pairing().Custom();
                    auto pairingToken = customPairing.PairingRequested(
                        [this, pairingCode](DeviceInformationCustomPairing const&,
                                            DevicePairingRequestedEventArgs const& pairingArgs) {
                        LOG_INFO("WifiDirect", "PairingRequested event, kind: " + 
                                 std::to_string(static_cast<int>(pairingArgs.PairingKind())));

                        if (pairingArgs.PairingKind() == DevicePairingKinds::ConfirmOnly) {
                            pairingArgs.Accept();
                            LOG_INFO("WifiDirect", "ConfirmOnly pairing accepted");
                        } else if (pairingArgs.PairingKind() == DevicePairingKinds::DisplayPin) {
                            pairingArgs.Accept();
                            LOG_INFO("WifiDirect", "DisplayPin pairing accepted");
                        } else if (pairingArgs.PairingKind() == DevicePairingKinds::ProvidePin) {
                            winrt::hstring pin(std::wstring(pairingCode.begin(), pairingCode.end()));
                            pairingArgs.Accept(pin);
                            LOG_INFO("WifiDirect", "ProvidePin pairing accepted with code");
                        } else if (pairingArgs.PairingKind() == DevicePairingKinds::ConfirmPinMatch) {
                            pairingArgs.Accept();
                            LOG_INFO("WifiDirect", "ConfirmPinMatch pairing accepted");
                        }
                    });

                    auto pairResult = customPairing.PairAsync(pairingKinds).get();
                    LOG_INFO("WifiDirect", "Pairing result: " + 
                             std::to_string(static_cast<int>(pairResult.Status())));

                    customPairing.PairingRequested(pairingToken);

                    if (pairResult.Status() != DevicePairingResultStatus::Paired &&
                        pairResult.Status() != DevicePairingResultStatus::AlreadyPaired) {
                        LOG_WARN("WifiDirect", "Pairing failed with status: " +
                                 std::to_string(static_cast<int>(pairResult.Status())));
                        return;
                    }

                    // Get the WiFiDirectDevice
                    auto wifiDirectDevice = WiFiDirectDevice::FromIdAsync(idHstr).get();
                    auto endpoints = wifiDirectDevice.GetConnectionEndpointPairs();

                    if (endpoints.Size() > 0) {
                        auto localHost = endpoints.GetAt(0).LocalHostName().DisplayName();
                        std::string goAddr;
                        for (auto c : localHost) goAddr += static_cast<char>(c);

                        {
                            std::lock_guard<std::mutex> lock(goAddrMutex_);
                            goAddress_ = goAddr;
                        }

                        LOG_INFO("WifiDirect", "Group Owner IP: " + goAddr);
                        groupFormed_ = true;

                        // Start TCP server if not already running
                        if (listenSocket_ == INVALID_SOCKET) {
                            if (tcpAcceptThread_.joinable()) tcpAcceptThread_.join();
                            tcpAcceptThread_ = std::thread(&WifiDirectServer::tcpAcceptLoop, this);
                        }
                    } else {
                        LOG_ERR("WifiDirect", "No endpoint pairs available after connection");
                    }

                } catch (const winrt::hresult_error& ex) {
                    std::string errMsg;
                    auto msg = ex.message();
                    for (auto c : msg) errMsg += static_cast<char>(c);
                    LOG_ERR("WifiDirect", "FromIdAsync failed: " + errMsg + 
                            " (HRESULT: 0x" + ([&]() {
                                std::ostringstream oss;
                                oss << std::hex << static_cast<uint32_t>(ex.code());
                                return oss.str();
                            })() + ")");
                }
            });

            // Start publishing
            publisher.Start();
            LOG_INFO("WifiDirect", "Wi-Fi Direct advertisement started (Group Owner mode)");

            // Wait for stop signal
            while (running_) {
                std::this_thread::sleep_for(std::chrono::milliseconds(500));
                
                auto status = publisher.Status();
                if (status == WiFiDirectAdvertisementPublisherStatus::Aborted) {
                    LOG_WARN("WifiDirect", "Advertisement aborted, restarting...");
                    break;
                }
            }

            // Clean up
            listener.ConnectionRequested(connectionToken);
            if (publisher.Status() == WiFiDirectAdvertisementPublisherStatus::Started) {
                publisher.Stop();
            }
            LOG_INFO("WifiDirect", "Wi-Fi Direct advertisement stopped");

        } catch (const winrt::hresult_error& ex) {
            std::string errMsg;
            auto msg = ex.message();
            for (auto c : msg) errMsg += static_cast<char>(c);
            LOG_ERR("WifiDirect", "WinRT error: " + errMsg);
            std::this_thread::sleep_for(std::chrono::seconds(3));
        } catch (const std::exception& ex) {
            LOG_ERR("WifiDirect", "Error: " + std::string(ex.what()));
            std::this_thread::sleep_for(std::chrono::seconds(3));
        }
    }
}

// ── TCP Server on GO interface ──
void WifiDirectServer::tcpAcceptLoop() {
    std::string goAddr = getGoAddress();
    if (goAddr.empty()) {
        LOG_ERR("WifiDirect", "Cannot start TCP server: GO address unknown");
        return;
    }

    listenSocket_ = ::socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (listenSocket_ == INVALID_SOCKET) {
        LOG_ERR("WifiDirect", "Failed to create TCP socket: " + std::to_string(WSAGetLastError()));
        return;
    }

    // Reuse address
    int optval = 1;
    setsockopt(listenSocket_, SOL_SOCKET, SO_REUSEADDR, (const char*)&optval, sizeof(optval));

    sockaddr_in addr = {};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(TCP_PORT);
    // Bind to all interfaces so the client can connect via GO address
    addr.sin_addr.s_addr = INADDR_ANY;

    if (::bind(listenSocket_, (sockaddr*)&addr, sizeof(addr)) == SOCKET_ERROR) {
        LOG_ERR("WifiDirect", "TCP bind failed: " + std::to_string(WSAGetLastError()));
        closesocket(listenSocket_);
        listenSocket_ = INVALID_SOCKET;
        return;
    }

    if (::listen(listenSocket_, 1) == SOCKET_ERROR) {
        LOG_ERR("WifiDirect", "TCP listen failed: " + std::to_string(WSAGetLastError()));
        closesocket(listenSocket_);
        listenSocket_ = INVALID_SOCKET;
        return;
    }

    LOG_INFO("WifiDirect", "TCP server listening on " + goAddr + ":" + std::to_string(TCP_PORT));

    while (running_ && groupFormed_) {
        fd_set readSet;
        FD_ZERO(&readSet);
        FD_SET(listenSocket_, &readSet);

        timeval timeout;
        timeout.tv_sec = 1;
        timeout.tv_usec = 0;

        int result = select(0, &readSet, nullptr, nullptr, &timeout);
        if (result <= 0) continue;

        sockaddr_in clientAddr = {};
        int clientAddrLen = sizeof(clientAddr);
        SOCKET clientSocket = accept(listenSocket_, (sockaddr*)&clientAddr, &clientAddrLen);
        if (clientSocket == INVALID_SOCKET) {
            if (running_) {
                LOG_ERR("WifiDirect", "TCP accept failed: " + std::to_string(WSAGetLastError()));
            }
            continue;
        }

        // Set TCP_NODELAY for ultra-low latency
        int noDelay = 1;
        setsockopt(clientSocket, IPPROTO_TCP, TCP_NODELAY, (const char*)&noDelay, sizeof(noDelay));

        char clientIp[INET_ADDRSTRLEN];
        inet_ntop(AF_INET, &clientAddr.sin_addr, clientIp, sizeof(clientIp));
        LOG_INFO("WifiDirect", "TCP client connected from " + std::string(clientIp));

        if (tcpClientThread_.joinable()) tcpClientThread_.join();
        clientConnected_ = true;
        tcpClientThread_ = std::thread(&WifiDirectServer::tcpClientLoop, this, clientSocket);
    }
}

void WifiDirectServer::tcpClientLoop(SOCKET clientSocket) {
    if (connectCallback_) {
        connectCallback_("Wi-Fi Direct Device");
    }

    // Read 2-byte packets: [action][keyIndex]
    uint8_t buffer[64];
    while (running_ && clientConnected_) {
        DWORD timeout = 1000;
        setsockopt(clientSocket, SOL_SOCKET, SO_RCVTIMEO, (const char*)&timeout, sizeof(timeout));

        int bytesRead = recv(clientSocket, reinterpret_cast<char*>(buffer), sizeof(buffer), 0);
        if (bytesRead <= 0) {
            int err = WSAGetLastError();
            if (err == WSAETIMEDOUT) continue;
            LOG_INFO("WifiDirect", "TCP client disconnected (recv returned " + 
                     std::to_string(bytesRead) + ", error: " + std::to_string(err) + ")");
            break;
        }

        // Process 2-byte packets
        for (int i = 0; i + 2 <= bytesRead; i += 2) {
            uint8_t action = buffer[i];      // 0 = up, 1 = down
            uint8_t keyIndex = buffer[i + 1]; // 0 = key1, 1 = key2

            bool isDown = (action == 0x01);

            if (inputCallback_) {
                inputCallback_(keyIndex, isDown);
            }
        }
    }

    closesocket(clientSocket);
    clientConnected_ = false;

    if (disconnectCallback_) {
        disconnectCallback_();
    }

    LOG_INFO("WifiDirect", "TCP client loop ended");
}

} // namespace rosk
