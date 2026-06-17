#include "tcp_server.h"
#include "../logger.h"
#include <WS2tcpip.h>

namespace rosk {

TcpServer::TcpServer() = default;

TcpServer::~TcpServer() {
    stop();
}

bool TcpServer::start(uint16_t port) {
    port_ = port;

    listenSocket_ = ::socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (listenSocket_ == INVALID_SOCKET) {
        LOG_ERR("TCP", "Failed to create socket: " + std::to_string(WSAGetLastError()));
        return false;
    }

    // Prevent child processes (like adb.exe) from inheriting the socket and keeping the port open
    SetHandleInformation((HANDLE)listenSocket_, HANDLE_FLAG_INHERIT, 0);

    // Allow port reuse
    int optval = 1;
    setsockopt(listenSocket_, SOL_SOCKET, SO_REUSEADDR, (const char*)&optval, sizeof(optval));

    // Disable Nagle's algorithm for low latency
    setsockopt(listenSocket_, IPPROTO_TCP, TCP_NODELAY, (const char*)&optval, sizeof(optval));

    sockaddr_in addr = {};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port);
    addr.sin_addr.s_addr = INADDR_ANY;

    if (::bind(listenSocket_, (sockaddr*)&addr, sizeof(addr)) == SOCKET_ERROR) {
        LOG_ERR("TCP", "Failed to bind port " + std::to_string(port) + ": " + std::to_string(WSAGetLastError()));
        closesocket(listenSocket_);
        listenSocket_ = INVALID_SOCKET;
        return false;
    }

    if (::listen(listenSocket_, 1) == SOCKET_ERROR) {
        LOG_ERR("TCP", "Failed to listen: " + std::to_string(WSAGetLastError()));
        closesocket(listenSocket_);
        listenSocket_ = INVALID_SOCKET;
        return false;
    }

    running_ = true;
    acceptThread_ = std::thread(&TcpServer::acceptLoop, this);

    LOG_INFO("TCP", "Listening on port " + std::to_string(port));
    return true;
}

void TcpServer::stop() {
    running_ = false;
    disconnectClient();

    if (listenSocket_ != INVALID_SOCKET) {
        closesocket(listenSocket_);
        listenSocket_ = INVALID_SOCKET;
    }
    if (acceptThread_.joinable()) {
        acceptThread_.join();
    }
    if (clientThread_.joinable()) {
        clientThread_.join();
    }
    LOG_INFO("TCP", "Server stopped");
}

void TcpServer::acceptLoop() {
    LOG_INFO("TCP", "Waiting for client...");

    while (running_) {
        // Use select() with timeout so we can check running_ flag
        fd_set readSet;
        FD_ZERO(&readSet);
        FD_SET(listenSocket_, &readSet);

        timeval timeout;
        timeout.tv_sec = 0;
        timeout.tv_usec = 500000;  // 500ms

        int result = select(0, &readSet, nullptr, nullptr, &timeout);
        if (result <= 0) continue;

        sockaddr_in clientAddr = {};
        int addrLen = sizeof(clientAddr);
        SOCKET clientSocket = accept(listenSocket_, (sockaddr*)&clientAddr, &addrLen);

        if (clientSocket == INVALID_SOCKET) {
            if (running_) {
                LOG_ERR("TCP", "Accept failed: " + std::to_string(WSAGetLastError()));
            }
            continue;
        }

        // Prevent inheritance for client socket too
        SetHandleInformation((HANDLE)clientSocket, HANDLE_FLAG_INHERIT, 0);

        // Disconnect existing client if any, then wait until its worker exits
        // before publishing the new socket into client_.
        disconnectClient();
        if (clientThread_.joinable()) {
            clientThread_.join();
        }

        // Disable Nagle's for the client socket too
        int optval = 1;
        setsockopt(clientSocket, IPPROTO_TCP, TCP_NODELAY, (const char*)&optval, sizeof(optval));

        // Set receive timeout
        DWORD rcvTimeout = 5000;
        setsockopt(clientSocket, SOL_SOCKET, SO_RCVTIMEO, (const char*)&rcvTimeout, sizeof(rcvTimeout));

        char ipStr[INET_ADDRSTRLEN];
        inet_ntop(AF_INET, &clientAddr.sin_addr, ipStr, sizeof(ipStr));
        LOG_INFO("TCP", "New connection from " + std::string(ipStr));

        {
            std::lock_guard<std::mutex> lock(clientMutex_);
            client_.socket = clientSocket;
            client_.address = clientAddr;
            client_.connected = true;
            client_.deviceName = "Unknown";
        }

        clientThread_ = std::thread(&TcpServer::clientLoop, this);
    }
}

void TcpServer::clientLoop() {
    SOCKET clientSocket = INVALID_SOCKET;
    {
        std::lock_guard<std::mutex> lock(clientMutex_);
        clientSocket = client_.socket;
    }

    if (clientSocket == INVALID_SOCKET) {
        return;
    }

    // Read handshake (hello message)
    std::string helloJson;
    if (!readMessage(clientSocket, helloJson)) {
        LOG_ERR("TCP", "Failed to read handshake");
        disconnectClient();
        return;
    }

    LOG_INFO("TCP", "Received handshake: " + helloJson);

    // Simple JSON parsing for device name (avoid external dependency)
    // Look for "device":"..."
    size_t devPos = helloJson.find("\"device\"");
    if (devPos != std::string::npos) {
        size_t valStart = helloJson.find('"', devPos + 8);
        if (valStart != std::string::npos) {
            valStart++;
            size_t valEnd = helloJson.find('"', valStart);
            if (valEnd != std::string::npos) {
                std::lock_guard<std::mutex> lock(clientMutex_);
                client_.deviceName = helloJson.substr(valStart, valEnd - valStart);
            }
        }
    }

    LOG_INFO("TCP", "Device connected: " + client_.deviceName);

    if (connectCallback_) {
        connectCallback_(client_);
    }

    // Read loop for control messages
    while (running_) {
        {
            std::lock_guard<std::mutex> lock(clientMutex_);
            if (!client_.connected || client_.socket != clientSocket) {
                break;
            }
        }

        std::string json;
        if (!readMessage(clientSocket, json)) {
            break;
        }

        if (json.empty()) {
            // Was a raw packet, already handled by rawInputCallback_
            continue;
        }

        // Check for disconnect message
        if (json.find("\"disconnect\"") != std::string::npos) {
            LOG_INFO("TCP", "Client requested disconnect");
            break;
        }

        if (messageCallback_) {
            messageCallback_(json);
        }
    }

    LOG_INFO("TCP", "Client disconnected: " + client_.deviceName);
    disconnectClient();
}

void TcpServer::disconnectClient() {
    std::lock_guard<std::mutex> lock(clientMutex_);
    if (client_.socket != INVALID_SOCKET) {
        closesocket(client_.socket);
        client_.socket = INVALID_SOCKET;
    }
    bool wasConnected = client_.connected;
    client_.connected = false;
    client_.deviceName.clear();

    if (wasConnected && disconnectCallback_) {
        disconnectCallback_();
    }
}

bool TcpServer::sendMessage(const std::string& json) {
    std::lock_guard<std::mutex> lock(clientMutex_);
    if (!client_.connected || client_.socket == INVALID_SOCKET) return false;
    return writeMessage(client_.socket, json);
}

bool TcpServer::readMessage(SOCKET sock, std::string& outJson) {
    // Protocol: [4 bytes: length (LE)] [N bytes: JSON] OR [4 bytes: Raw packet starting with 0x01, 0x02, or 0x04]
    uint8_t typeByte = 0;
    int nPeek = recv(sock, (char*)&typeByte, 1, MSG_PEEK);
    if (nPeek <= 0) return false;

    if (typeByte == 0x01 || typeByte == 0x02 || typeByte == 0x04) {
        // Raw 4-byte packet
        uint8_t buf[4];
        int totalRead = 0;
        while (totalRead < 4) {
            int n = recv(sock, (char*)buf + totalRead, 4 - totalRead, 0);
            if (n <= 0) return false;
            totalRead += n;
        }
        if (rawInputCallback_) {
            rawInputCallback_(buf);
        }
        outJson.clear();
        return true;
    }

    uint32_t length = 0;
    int totalRead = 0;

    // Read length prefix
    while (totalRead < 4) {
        int n = recv(sock, ((char*)&length) + totalRead, 4 - totalRead, 0);
        if (n <= 0) return false;
        totalRead += n;
    }

    if (length > protocol::MAX_TCP_MESSAGE_SIZE) {
        LOG_ERR("TCP", "Message too large: " + std::to_string(length));
        return false;
    }

    // Read JSON payload
    outJson.resize(length);
    totalRead = 0;
    while (totalRead < static_cast<int>(length)) {
        int n = recv(sock, outJson.data() + totalRead, length - totalRead, 0);
        if (n <= 0) return false;
        totalRead += n;
    }

    return true;
}

bool TcpServer::writeMessage(SOCKET sock, const std::string& json) {
    uint32_t length = static_cast<uint32_t>(json.size());

    // Send length prefix
    int totalSent = 0;
    while (totalSent < 4) {
        int n = send(sock, reinterpret_cast<const char*>(&length) + totalSent, 4 - totalSent, 0);
        if (n <= 0) return false;
        totalSent += n;
    }

    // Send JSON payload
    totalSent = 0;
    while (totalSent < static_cast<int>(length)) {
        int n = send(sock, json.data() + totalSent, length - totalSent, 0);
        if (n <= 0) return false;
        totalSent += n;
    }

    return true;
}

} // namespace rosk
