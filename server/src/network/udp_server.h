#pragma once
/*
 * High-performance UDP server for receiving input events.
 * Designed for absolute minimum latency:
 * - Runs on its own high-priority thread
 * - Non-blocking receive
 * - No memory allocation in hot path
 */

#include "../protocol.h"
#include <WinSock2.h>
#include <functional>
#include <thread>
#include <atomic>
#include <string>
#include <utility>

namespace rosk {

class UdpServer {
public:
    using InputCallback = std::function<void(const protocol::InputPacket& packet,
                                             const sockaddr_in& sender)>;
    using PingCallback = std::function<void(const protocol::PingPacket& packet,
                                            const sockaddr_in& sender)>;

    UdpServer();
    ~UdpServer();

    // Start listening on the specified port
    bool start(uint16_t port);

    // Stop the server
    void stop();

    // Set callbacks
    void onInput(InputCallback cb) { inputCallback_ = std::move(cb); }
    void onPing(PingCallback cb) { pingCallback_ = std::move(cb); }

    // Send a pong response back to a client
    void sendPong(const sockaddr_in& dest, uint16_t echoTimestamp);

    // Send raw data to a specific address
    void sendTo(const sockaddr_in& dest, const void* data, int len);

    bool isRunning() const { return running_.load(); }
    uint16_t getPort() const { return port_; }

    // Statistics
    uint64_t getPacketsReceived() const { return packetsReceived_.load(); }
    uint64_t getPacketsDropped() const { return packetsDropped_.load(); }

private:
    void receiveLoop();

    SOCKET socket_ = INVALID_SOCKET;
    uint16_t port_ = 0;
    std::thread receiveThread_;
    std::atomic<bool> running_{ false };

    InputCallback inputCallback_;
    PingCallback pingCallback_;

    // Stats
    std::atomic<uint64_t> packetsReceived_{ 0 };
    std::atomic<uint64_t> packetsDropped_{ 0 };
    uint8_t lastSequence_ = 0;
};

} // namespace rosk
