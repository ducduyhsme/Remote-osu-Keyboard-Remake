#include "udp_server.h"
#include "../logger.h"
#include <WS2tcpip.h>

namespace rosk {

UdpServer::UdpServer() = default;

UdpServer::~UdpServer() {
    stop();
}

bool UdpServer::start(uint16_t port) {
    port_ = port;

    // Create UDP socket
    socket_ = ::socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (socket_ == INVALID_SOCKET) {
        LOG_ERR("UDP", "Failed to create socket: " + std::to_string(WSAGetLastError()));
        return false;
    }

    // Set socket options for low latency
    // Disable buffering
    int optval = 1;
    setsockopt(socket_, SOL_SOCKET, SO_REUSEADDR, (const char*)&optval, sizeof(optval));

    // Set receive buffer small to reduce latency (we only get 4-byte packets)
    int rcvbuf = 4096;
    setsockopt(socket_, SOL_SOCKET, SO_RCVBUF, (const char*)&rcvbuf, sizeof(rcvbuf));

    // Set non-blocking mode with a short timeout for clean shutdown
    DWORD timeout = 100;  // 100ms receive timeout
    setsockopt(socket_, SOL_SOCKET, SO_RCVTIMEO, (const char*)&timeout, sizeof(timeout));

    // Bind
    sockaddr_in addr = {};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port);
    addr.sin_addr.s_addr = INADDR_ANY;

    if (::bind(socket_, (sockaddr*)&addr, sizeof(addr)) == SOCKET_ERROR) {
        LOG_ERR("UDP", "Failed to bind port " + std::to_string(port) + ": " + std::to_string(WSAGetLastError()));
        closesocket(socket_);
        socket_ = INVALID_SOCKET;
        return false;
    }

    running_ = true;
    receiveThread_ = std::thread(&UdpServer::receiveLoop, this);

    // Set thread priority to highest for minimum latency
    SetThreadPriority(receiveThread_.native_handle(), THREAD_PRIORITY_HIGHEST);

    LOG_INFO("UDP", "Listening on port " + std::to_string(port));
    return true;
}

void UdpServer::stop() {
    running_ = false;
    if (socket_ != INVALID_SOCKET) {
        closesocket(socket_);
        socket_ = INVALID_SOCKET;
    }
    if (receiveThread_.joinable()) {
        receiveThread_.join();
    }
    LOG_INFO("UDP", "Server stopped");
}

void UdpServer::receiveLoop() {
    char buffer[64];  // Our packets are 4 bytes, this is more than enough
    sockaddr_in senderAddr = {};
    int senderLen = sizeof(senderAddr);

    LOG_INFO("UDP", "Receive loop started (high priority thread)");

    while (running_) {
        int bytesReceived = recvfrom(socket_, buffer, sizeof(buffer), 0,
                                      (sockaddr*)&senderAddr, &senderLen);

        if (bytesReceived == SOCKET_ERROR) {
            int err = WSAGetLastError();
            if (err == WSAETIMEDOUT || err == WSAEWOULDBLOCK) {
                continue;  // Normal timeout, check running_ flag
            }
            if (running_) {
                LOG_ERR("UDP", "Receive error: " + std::to_string(err));
            }
            continue;
        }

        if (bytesReceived < 4) {
            packetsDropped_++;
            continue;
        }

        packetsReceived_++;

        // Parse packet type
        auto packetType = static_cast<protocol::PacketType>(buffer[0]);

        switch (packetType) {
            case protocol::PacketType::INPUT: {
                if (bytesReceived >= static_cast<int>(sizeof(protocol::InputPacket))) {
                    auto* pkt = reinterpret_cast<const protocol::InputPacket*>(buffer);

                    // Check for dropped packets via sequence number
                    uint8_t expectedSeq = lastSequence_ + 1;
                    if (pkt->sequence != expectedSeq && packetsReceived_ > 1) {
                        int dropped = (pkt->sequence - expectedSeq) & 0xFF;
                        if (dropped > 0 && dropped < 128) {
                            packetsDropped_ += dropped;
                        }
                    }
                    lastSequence_ = pkt->sequence;

                    if (inputCallback_) {
                        inputCallback_(*pkt, senderAddr);
                    }
                }
                break;
            }

            case protocol::PacketType::PING: {
                if (bytesReceived >= static_cast<int>(sizeof(protocol::PingPacket))) {
                    auto* pkt = reinterpret_cast<const protocol::PingPacket*>(buffer);
                    if (pingCallback_) {
                        pingCallback_(*pkt, senderAddr);
                    }
                    // Auto-respond with pong
                    sendPong(senderAddr, pkt->timestamp);
                }
                break;
            }

            case protocol::PacketType::HEARTBEAT:
                // Just a keepalive, no action needed beyond updating "last seen"
                break;

            default:
                packetsDropped_++;
                break;
        }
    }
}

void UdpServer::sendPong(const sockaddr_in& dest, uint16_t echoTimestamp) {
    protocol::PongPacket pong;
    pong.type = protocol::PacketType::PONG;
    pong.padding = 0;
    pong.echoTimestamp = echoTimestamp;
    sendTo(dest, &pong, sizeof(pong));
}

void UdpServer::sendTo(const sockaddr_in& dest, const void* data, int len) {
    if (socket_ == INVALID_SOCKET) return;
    ::sendto(socket_, (const char*)data, len, 0, (const sockaddr*)&dest, sizeof(dest));
}

} // namespace rosk
