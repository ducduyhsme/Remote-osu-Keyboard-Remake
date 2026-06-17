#pragma once
/*
 * UDP broadcast discovery service.
 * Allows Android clients to auto-detect the PC server on the local network.
 */

#include "../protocol.h"
#include <WinSock2.h>
#include <thread>
#include <atomic>
#include <string>
#include <vector>

namespace rosk {

class DiscoveryService {
public:
    DiscoveryService();
    ~DiscoveryService();

    // Start listening for discovery broadcasts
    bool start(uint16_t tcpPort, uint16_t udpPort);
    void stop();

    bool isRunning() const { return running_.load(); }

    // Get local IP addresses
    static std::vector<std::string> getLocalIPs();

private:
    void listenLoop(uint16_t tcpPort, uint16_t udpPort);
    std::string getComputerName();

    SOCKET socket_ = INVALID_SOCKET;
    std::thread listenThread_;
    std::atomic<bool> running_{ false };
};

} // namespace rosk
