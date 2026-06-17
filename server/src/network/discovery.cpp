#include "discovery.h"
#include "../logger.h"

#include <WS2tcpip.h>
#include <iphlpapi.h>
#include <cstdlib>
#include <vector>

#pragma comment(lib, "iphlpapi.lib")

namespace rosk {

namespace {

std::string escapeJson(const std::string& value) {
    std::string escaped;
    escaped.reserve(value.size());
    for (char c : value) {
        if (c == '\\' || c == '"') {
            escaped.push_back('\\');
        }
        escaped.push_back(c);
    }
    return escaped;
}

bool isDiscoveryRequest(const std::string& message) {
    return message.find("\"type\"") != std::string::npos
        && message.find("rosk_discover") != std::string::npos;
}

} // namespace

DiscoveryService::DiscoveryService() = default;

DiscoveryService::~DiscoveryService() {
    stop();
}

bool DiscoveryService::start(uint16_t tcpPort, uint16_t udpPort) {
    socket_ = ::socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (socket_ == INVALID_SOCKET) {
        LOG_ERR("Discovery", "Failed to create socket: " + std::to_string(WSAGetLastError()));
        return false;
    }

    int optval = 1;
    setsockopt(socket_, SOL_SOCKET, SO_BROADCAST, reinterpret_cast<const char*>(&optval), sizeof(optval));
    setsockopt(socket_, SOL_SOCKET, SO_REUSEADDR, reinterpret_cast<const char*>(&optval), sizeof(optval));

    DWORD timeout = 500;
    setsockopt(socket_, SOL_SOCKET, SO_RCVTIMEO, reinterpret_cast<const char*>(&timeout), sizeof(timeout));

    sockaddr_in addr = {};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(protocol::UDP_DISCOVER_PORT);
    addr.sin_addr.s_addr = INADDR_ANY;

    if (::bind(socket_, reinterpret_cast<sockaddr*>(&addr), sizeof(addr)) == SOCKET_ERROR) {
        LOG_ERR("Discovery", "Failed to bind port " + std::to_string(protocol::UDP_DISCOVER_PORT)
                  + ": " + std::to_string(WSAGetLastError()));
        closesocket(socket_);
        socket_ = INVALID_SOCKET;
        return false;
    }

    running_ = true;
    listenThread_ = std::thread(&DiscoveryService::listenLoop, this, tcpPort, udpPort);

    LOG_INFO("Discovery", "WiFi discovery active on UDP port " + std::to_string(protocol::UDP_DISCOVER_PORT));
    return true;
}

void DiscoveryService::stop() {
    running_ = false;
    if (socket_ != INVALID_SOCKET) {
        closesocket(socket_);
        socket_ = INVALID_SOCKET;
    }
    if (listenThread_.joinable()) {
        listenThread_.join();
    }
}

void DiscoveryService::listenLoop(uint16_t tcpPort, uint16_t udpPort) {
    char buffer[512];
    sockaddr_in senderAddr = {};
    int senderLen = sizeof(senderAddr);
    const std::string pcName = escapeJson(getComputerName());

    while (running_) {
        int bytesReceived = recvfrom(socket_, buffer, sizeof(buffer) - 1, 0,
                                      reinterpret_cast<sockaddr*>(&senderAddr), &senderLen);

        if (bytesReceived <= 0) continue;

        buffer[bytesReceived] = '\0';
        std::string request(buffer, bytesReceived);
        if (!isDiscoveryRequest(request)) continue;

        char senderIP[INET_ADDRSTRLEN];
        inet_ntop(AF_INET, &senderAddr.sin_addr, senderIP, sizeof(senderIP));
        LOG_INFO("Discovery", "Discovery request from " + std::string(senderIP));

        std::string response = "{"
            "\"type\":\"rosk_server\","
            "\"version\":2,"
            "\"name\":\"" + pcName + "\","
            "\"tcp_port\":" + std::to_string(tcpPort) + ","
            "\"udp_port\":" + std::to_string(udpPort) +
            "}";

        sendto(socket_, response.c_str(), static_cast<int>(response.size()), 0,
               reinterpret_cast<sockaddr*>(&senderAddr), sizeof(senderAddr));
    }
}

std::string DiscoveryService::getComputerName() {
    wchar_t name[MAX_COMPUTERNAME_LENGTH + 1];
    DWORD size = MAX_COMPUTERNAME_LENGTH + 1;
    if (GetComputerNameW(name, &size)) {
        std::string result;
        result.reserve(size);
        for (DWORD i = 0; i < size; ++i) {
            result.push_back(static_cast<char>(name[i]));
        }
        return result;
    }
    return "Unknown-PC";
}

std::vector<std::string> DiscoveryService::getLocalIPs() {
    std::vector<std::string> ips;

    ULONG bufLen = 15000;
    auto* adapterAddresses = reinterpret_cast<IP_ADAPTER_ADDRESSES*>(malloc(bufLen));
    if (!adapterAddresses) return ips;

    ULONG result = GetAdaptersAddresses(AF_INET, GAA_FLAG_SKIP_DNS_SERVER | GAA_FLAG_SKIP_MULTICAST,
                                         nullptr, adapterAddresses, &bufLen);

    if (result == ERROR_BUFFER_OVERFLOW) {
        free(adapterAddresses);
        adapterAddresses = reinterpret_cast<IP_ADAPTER_ADDRESSES*>(malloc(bufLen));
        if (!adapterAddresses) return ips;
        result = GetAdaptersAddresses(AF_INET, GAA_FLAG_SKIP_DNS_SERVER | GAA_FLAG_SKIP_MULTICAST,
                                       nullptr, adapterAddresses, &bufLen);
    }

    if (result == NO_ERROR) {
        for (auto* adapter = adapterAddresses; adapter; adapter = adapter->Next) {
            if (adapter->IfType == IF_TYPE_SOFTWARE_LOOPBACK) continue;
            if (adapter->OperStatus != IfOperStatusUp) continue;

            for (auto* addr = adapter->FirstUnicastAddress; addr; addr = addr->Next) {
                auto* sa = reinterpret_cast<sockaddr_in*>(addr->Address.lpSockaddr);
                char ipStr[INET_ADDRSTRLEN];
                inet_ntop(AF_INET, &sa->sin_addr, ipStr, sizeof(ipStr));
                std::string ip(ipStr);

                if (ip.substr(0, 4) != "127." && ip.substr(0, 8) != "169.254.") {
                    ips.push_back(ip);
                }
            }
        }
    }

    free(adapterAddresses);
    return ips;
}

} // namespace rosk
