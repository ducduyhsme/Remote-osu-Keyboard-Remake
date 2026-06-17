#pragma once
/*
 * TCP server for handshake, configuration exchange, and connection management.
 * Only used for initial connection setup — actual input goes over UDP.
 */

#include "../protocol.h"
#include <WinSock2.h>
#include <functional>
#include <thread>
#include <atomic>
#include <string>
#include <mutex>
#include <utility>

namespace rosk {

struct ClientInfo {
    SOCKET socket = INVALID_SOCKET;
    sockaddr_in address = {};
    std::string deviceName;
    bool connected = false;
};

class TcpServer {
public:
    using ConnectCallback = std::function<void(const ClientInfo& client)>;
    using DisconnectCallback = std::function<void()>;
    using MessageCallback = std::function<void(const std::string& json)>;
    using RawInputCallback = std::function<void(const uint8_t* data)>;

    TcpServer();
    ~TcpServer();

    bool start(uint16_t port);
    void stop();

    void onConnect(ConnectCallback cb) { connectCallback_ = std::move(cb); }
    void onDisconnect(DisconnectCallback cb) { disconnectCallback_ = std::move(cb); }
    void onMessage(MessageCallback cb) { messageCallback_ = std::move(cb); }
    void onRawInput(RawInputCallback cb) { rawInputCallback_ = std::move(cb); }

    // Send JSON message to connected client
    bool sendMessage(const std::string& json);

    // Disconnect current client
    void disconnectClient();

    bool isRunning() const { return running_.load(); }
    bool hasClient() const { return client_.connected; }
    const ClientInfo& getClient() const { return client_; }

private:
    void acceptLoop();
    void clientLoop();
    bool readMessage(SOCKET sock, std::string& outJson);
    bool writeMessage(SOCKET sock, const std::string& json);

    SOCKET listenSocket_ = INVALID_SOCKET;
    uint16_t port_ = 0;
    std::thread acceptThread_;
    std::thread clientThread_;
    std::atomic<bool> running_{ false };

    ClientInfo client_;
    std::mutex clientMutex_;

    ConnectCallback connectCallback_;
    DisconnectCallback disconnectCallback_;
    MessageCallback messageCallback_;
    RawInputCallback rawInputCallback_;
};

} // namespace rosk
