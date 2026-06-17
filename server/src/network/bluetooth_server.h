#pragma once
/*
 * Bluetooth RFCOMM (Serial Port Profile) server.
 * Provides Bluetooth connectivity as an alternative to WiFi.
 * Note: Bluetooth has inherently higher latency (~20-50ms+) than WiFi/USB.
 */

#include "../protocol.h"
#include <WinSock2.h>
#include <ws2bth.h>
#include <thread>
#include <atomic>
#include <functional>
#include <string>
#include <utility>

namespace rosk {

class BluetoothServer {
public:
    using InputCallback = std::function<void(uint8_t keyIndex, bool down)>;
    using ConnectCallback = std::function<void(const std::string& deviceName)>;
    using DisconnectCallback = std::function<void()>;

    BluetoothServer();
    ~BluetoothServer();

    bool start();
    void stop();

    void onInput(InputCallback cb) { inputCallback_ = std::move(cb); }
    void onConnect(ConnectCallback cb) { connectCallback_ = std::move(cb); }
    void onDisconnect(DisconnectCallback cb) { disconnectCallback_ = std::move(cb); }

    bool isRunning() const { return running_.load(); }
    bool hasClient() const { return clientConnected_.load(); }

    // Check if Bluetooth is available on this PC
    static bool isBluetoothAvailable();

private:
    void acceptLoop();
    void clientLoop(SOCKET clientSocket);

    SOCKET listenSocket_ = INVALID_SOCKET;
    std::thread acceptThread_;
    std::thread clientThread_;
    std::atomic<bool> running_{ false };
    std::atomic<bool> clientConnected_{ false };

    InputCallback inputCallback_;
    ConnectCallback connectCallback_;
    DisconnectCallback disconnectCallback_;
};

} // namespace rosk
