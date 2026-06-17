#include "bluetooth_server.h"
#include "../logger.h"
#include <Windows.h>
#include <bluetoothapis.h>

// RFCOMM UUID for our service: 00001101-0000-1000-8000-00805F9B34FB (SPP)
// We use a custom UUID so only our app connects
static const GUID ROSK_BT_UUID = {
    0xa1b2c3d4, 0xe5f6, 0x7890,
    { 0xab, 0xcd, 0xef, 0x01, 0x23, 0x45, 0x67, 0x89 }
};

namespace rosk {

BluetoothServer::BluetoothServer() = default;

BluetoothServer::~BluetoothServer() {
    stop();
}

bool BluetoothServer::isBluetoothAvailable() {
    HANDLE hRadio = nullptr;
    BLUETOOTH_FIND_RADIO_PARAMS params = { sizeof(BLUETOOTH_FIND_RADIO_PARAMS) };
    HBLUETOOTH_RADIO_FIND hFind = BluetoothFindFirstRadio(&params, &hRadio);

    if (hFind) {
        CloseHandle(hRadio);
        BluetoothFindRadioClose(hFind);
        return true;
    }
    return false;
}

bool BluetoothServer::start() {
    if (!isBluetoothAvailable()) {
        LOG_WARN("Bluetooth", "No Bluetooth adapter found");
        return false;
    }

    listenSocket_ = ::socket(AF_BTH, SOCK_STREAM, BTHPROTO_RFCOMM);
    if (listenSocket_ == INVALID_SOCKET) {
        LOG_ERR("Bluetooth", "Failed to create BT socket: " + std::to_string(WSAGetLastError()));
        return false;
    }

    SOCKADDR_BTH addr = {};
    addr.addressFamily = AF_BTH;
    addr.btAddr = 0;
    addr.serviceClassId = ROSK_BT_UUID;
    addr.port = BT_PORT_ANY;

    if (::bind(listenSocket_, (sockaddr*)&addr, sizeof(addr)) == SOCKET_ERROR) {
        LOG_ERR("Bluetooth", "Failed to bind: " + std::to_string(WSAGetLastError()));
        closesocket(listenSocket_);
        listenSocket_ = INVALID_SOCKET;
        return false;
    }

    if (::listen(listenSocket_, 1) == SOCKET_ERROR) {
        LOG_ERR("Bluetooth", "Failed to listen: " + std::to_string(WSAGetLastError()));
        closesocket(listenSocket_);
        listenSocket_ = INVALID_SOCKET;
        return false;
    }

    // Register SDP service
    CSADDR_INFO csAddr = {};
    csAddr.LocalAddr.iSockaddrLength = sizeof(SOCKADDR_BTH);
    csAddr.LocalAddr.lpSockaddr = (LPSOCKADDR)&addr;
    csAddr.iSocketType = SOCK_STREAM;
    csAddr.iProtocol = BTHPROTO_RFCOMM;

    WSAQUERYSET querySet = {};
    querySet.dwSize = sizeof(WSAQUERYSET);
    querySet.lpszServiceInstanceName = const_cast<LPWSTR>(L"Remote osu! Keyboard");
    querySet.lpServiceClassId = const_cast<LPGUID>(&ROSK_BT_UUID);
    querySet.dwNameSpace = NS_BTH;
    querySet.dwNumberOfCsAddrs = 1;
    querySet.lpcsaBuffer = &csAddr;

    WSASetServiceW(&querySet, RNRSERVICE_REGISTER, 0);

    running_ = true;
    acceptThread_ = std::thread(&BluetoothServer::acceptLoop, this);

    LOG_INFO("Bluetooth", "RFCOMM server started");
    return true;
}

void BluetoothServer::stop() {
    running_ = false;
    clientConnected_ = false;

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

    LOG_INFO("Bluetooth", "Server stopped");
}

void BluetoothServer::acceptLoop() {
    while (running_) {
        fd_set readSet;
        FD_ZERO(&readSet);
        FD_SET(listenSocket_, &readSet);

        timeval timeout;
        timeout.tv_sec = 1;
        timeout.tv_usec = 0;

        int result = select(0, &readSet, nullptr, nullptr, &timeout);
        if (result <= 0) continue;

        SOCKADDR_BTH clientAddr = {};
        int addrLen = sizeof(clientAddr);
        SOCKET clientSocket = accept(listenSocket_, (sockaddr*)&clientAddr, &addrLen);

        if (clientSocket == INVALID_SOCKET) {
            if (running_) {
                LOG_ERR("Bluetooth", "Accept failed: " + std::to_string(WSAGetLastError()));
            }
            continue;
        }

        LOG_INFO("Bluetooth", "Client connected via Bluetooth");

        if (clientThread_.joinable()) {
            clientThread_.join();
        }

        clientConnected_ = true;
        clientThread_ = std::thread(&BluetoothServer::clientLoop, this, clientSocket);
    }
}

void BluetoothServer::clientLoop(SOCKET clientSocket) {
    if (connectCallback_) {
        connectCallback_("Bluetooth Device");
    }

    char buffer[64];
    while (running_ && clientConnected_) {
        // Set receive timeout
        DWORD timeout = 1000;
        setsockopt(clientSocket, SOL_SOCKET, SO_RCVTIMEO, (const char*)&timeout, sizeof(timeout));

        int bytesRead = recv(clientSocket, buffer, sizeof(buffer), 0);

        if (bytesRead <= 0) {
            int err = WSAGetLastError();
            if (err == WSAETIMEDOUT) continue;
            break;
        }

        // Process input packets (same 4-byte format as UDP)
        for (int i = 0; i + 4 <= bytesRead; i += 4) {
            auto* pkt = reinterpret_cast<const protocol::InputPacket*>(buffer + i);
            if (pkt->type == protocol::PacketType::INPUT && inputCallback_) {
                inputCallback_(pkt->keyIndex, pkt->action == protocol::KeyAction::KEY_DOWN);
            }
        }
    }

    closesocket(clientSocket);
    clientConnected_ = false;

    if (disconnectCallback_) {
        disconnectCallback_();
    }

    LOG_INFO("Bluetooth", "Client disconnected");
}

} // namespace rosk
