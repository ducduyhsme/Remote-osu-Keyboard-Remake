#pragma once
/*
 * Wi-Fi Direct Server (Group Owner)
 * Uses Windows::Devices::WiFiDirect to advertise, pair, and form a group.
 * Then runs a TCP server on the GO interface for ultra-low latency input.
 */

#include <string>
#include <vector>
#include <thread>
#include <atomic>
#include <functional>
#include <mutex>
#include <set>
#include <WinSock2.h>

namespace rosk {

class WifiDirectServer {
public:
    WifiDirectServer();
    ~WifiDirectServer();

    bool start();
    void stop();

    bool isRunning() const { return running_.load(); }
    bool hasClient() const { return clientConnected_.load(); }

    // Callbacks
    using InputCallback = std::function<void(uint8_t keyIndex, bool down)>;
    using ConnectCallback = std::function<void(const std::string& deviceName)>;
    using DisconnectCallback = std::function<void()>;

    void onInput(InputCallback cb) { inputCallback_ = std::move(cb); }
    void onConnect(ConnectCallback cb) { connectCallback_ = std::move(cb); }
    void onDisconnect(DisconnectCallback cb) { disconnectCallback_ = std::move(cb); }

    // Trusted device management
    void addTrustedDevice(const std::string& deviceId);
    bool isTrustedDevice(const std::string& deviceId) const;
    void loadTrustedDevices();
    void saveTrustedDevices();

    // Get the GO IP address
    std::string getGoAddress() const;

    static constexpr uint16_t TCP_PORT = 7230;

private:
    // WinRT advertisement and connection handling (runs on background thread)
    void advertiseLoop();

    // TCP server on GO interface
    void tcpAcceptLoop();
    void tcpClientLoop(SOCKET clientSocket);

    // Pairing dialog (Win32)
    struct PairingResult {
        bool accepted = false;
        bool trusted = false;
    };
    PairingResult showPairingDialog(const std::string& deviceName, const std::string& pairingCode);

    // State
    std::atomic<bool> running_{ false };
    std::atomic<bool> clientConnected_{ false };
    std::atomic<bool> groupFormed_{ false };

    // Threads
    std::thread advertiseThread_;
    std::thread tcpAcceptThread_;
    std::thread tcpClientThread_;

    // TCP
    SOCKET listenSocket_ = INVALID_SOCKET;

    // GO Address
    mutable std::mutex goAddrMutex_;
    std::string goAddress_;

    // Trusted devices
    mutable std::mutex trustedMutex_;
    std::set<std::string> trustedDevices_;

    // Callbacks
    InputCallback inputCallback_;
    ConnectCallback connectCallback_;
    DisconnectCallback disconnectCallback_;
};

} // namespace rosk
