#include "server.h"
#include "logger.h"

#include <WS2tcpip.h>
#include <algorithm>
#include <chrono>
#include <iostream>
#include <sstream>
#include <thread>

namespace rosk {

Server::Server() = default;

Server::~Server() {
    stop();
}

bool Server::start() {
    if (running_) return true;

    LOG_INFO("Server", "===========================================");
    LOG_INFO("Server", "  Remote osu! Keyboard v" APP_VERSION);
    LOG_INFO("Server", "===========================================");

    config_.load();
    input_.setKey(0, config_.key1);
    input_.setKey(1, config_.key2);

    auto ips = DiscoveryService::getLocalIPs();
    if (ips.empty()) {
        LOG_WARN("Server", "Could not detect local IP address");
    } else {
        LOG_INFO("Server", "Local IP addresses:");
        for (const auto& ip : ips) {
            LOG_INFO("Server", "  -> " + ip);
        }
    }

    LOG_INFO("Server", "Key bindings: [" + input_.getKeyName(0) + "] [" + input_.getKeyName(1) + "]");

    udpServer_.onInput([this](const protocol::InputPacket& pkt, const sockaddr_in&) {
        input_.processInput(pkt.keyIndex, pkt.action == protocol::KeyAction::KEY_DOWN);
        inputsProcessed_++;

        if (config_.showKeyPresses) {
            std::string action = (pkt.action == protocol::KeyAction::KEY_DOWN) ? "DOWN" : "UP  ";
            LOG_DEBUG("Input", input_.getKeyName(pkt.keyIndex) + " " + action
                     + " [seq:" + std::to_string(pkt.sequence) + "]");
        }
    });

    tcpServer_.onConnect([this](const ClientInfo& client) {
        LOG_INFO("Server", "Client connected: " + client.deviceName);

        std::string welcome;
        buildWelcomeJson(welcome);
        tcpServer_.sendMessage(welcome);
    });

    tcpServer_.onDisconnect([this]() {
        LOG_INFO("Server", "Client disconnected");
        input_.releaseAll();
    });

    tcpServer_.onRawInput([this](const uint8_t* data) {
        if (data[0] != 0x01) return;

        auto* pkt = reinterpret_cast<const protocol::InputPacket*>(data);
        input_.processInput(pkt->keyIndex, pkt->action == protocol::KeyAction::KEY_DOWN);
        inputsProcessed_++;

        if (config_.showKeyPresses) {
            std::string action = (pkt->action == protocol::KeyAction::KEY_DOWN) ? "DOWN" : "UP  ";
            LOG_DEBUG("Input", input_.getKeyName(pkt->keyIndex) + " " + action
                     + " [seq:" + std::to_string(pkt->sequence) + "] (TCP)");
        }
    });

    running_ = true;

    if (!udpServer_.start(config_.udpPort)) {
        LOG_ERR("Server", "Failed to start UDP server");
        running_ = false;
        return false;
    }

    if (!tcpServer_.start(config_.tcpPort)) {
        LOG_ERR("Server", "Failed to start TCP server");
        stop();
        return false;
    }

    if (!discovery_.start(config_.tcpPort, config_.udpPort)) {
        LOG_WARN("Server", "Discovery service failed to start (WiFi auto-detect will not work)");
    }

    adbManager_.start();

    if (config_.bluetoothEnabled) {
        if (BluetoothServer::isBluetoothAvailable()) {
            if (btServer_.start()) {
                btServer_.onInput([this](uint8_t keyIndex, bool down) {
                    input_.processInput(keyIndex, down);
                    inputsProcessed_++;
                });
                btServer_.onConnect([](const std::string& device) {
                    LOG_INFO("Bluetooth", "Device connected: " + device);
                });
                btServer_.onDisconnect([this]() {
                    LOG_INFO("Bluetooth", "Device disconnected");
                    input_.releaseAll();
                });
            }
        } else {
            LOG_INFO("Server", "Bluetooth not available on this PC");
        }
    }

    LOG_INFO("Server", "All services started. Waiting for connections...");
    LOG_INFO("Server", "Type 'help' for available commands");
    return true;
}

void Server::stop() {
    if (!running_) return;
    running_ = false;

    LOG_INFO("Server", "Shutting down...");

    input_.releaseAll();
    btServer_.stop();
    adbManager_.stop();
    discovery_.stop();
    tcpServer_.stop();
    udpServer_.stop();

    config_.save();

    LOG_INFO("Server", "Server stopped");
}

void Server::runCommandLoop() {
    std::string line;
    while (running_ && std::getline(std::cin, line)) {
        line.erase(0, line.find_first_not_of(" \t\r\n"));
        line.erase(line.find_last_not_of(" \t\r\n") + 1);

        if (line.empty()) continue;

        std::string cmd = line;
        std::transform(cmd.begin(), cmd.end(), cmd.begin(), ::tolower);

        if (cmd == "quit" || cmd == "exit" || cmd == "q") {
            stop();
            break;
        } else if (cmd == "help" || cmd == "h" || cmd == "?") {
            printHelp();
        } else if (cmd == "status" || cmd == "s") {
            printStatus();
        } else if (cmd.substr(0, 3) == "key" || cmd.substr(0, 6) == "setkey") {
            handleSetKey(line);
        } else if (cmd == "stats") {
            std::cout << "Inputs processed: " << inputsProcessed_.load() << std::endl;
            std::cout << "UDP packets received: " << udpServer_.getPacketsReceived() << std::endl;
            std::cout << "UDP packets dropped: " << udpServer_.getPacketsDropped() << std::endl;
        } else {
            std::cout << "Unknown command. Type 'help' for available commands." << std::endl;
        }
    }
}

void Server::printHelp() {
    std::cout << "\n"
              << "Available commands:\n"
              << "  help, h, ?         - Show this help\n"
              << "  status, s          - Show server status\n"
              << "  key1 <keyname>     - Set key 1 (e.g., key1 Z)\n"
              << "  key2 <keyname>     - Set key 2 (e.g., key2 X)\n"
              << "  stats              - Show input statistics\n"
              << "  quit, exit, q      - Stop server\n"
              << "\n"
              << "Example key names: A-Z, 0-9, F1-F12, SPACE, ENTER, SHIFT, CTRL, etc.\n"
              << std::endl;
}

void Server::printStatus() {
    std::cout << "\n=== Server Status ===" << std::endl;
    std::cout << "Keys: [" << input_.getKeyName(0) << "] [" << input_.getKeyName(1) << "]" << std::endl;
    std::cout << "UDP: " << (udpServer_.isRunning() ? "Running" : "Stopped")
              << " (port " << config_.udpPort << ")" << std::endl;
    std::cout << "TCP: " << (tcpServer_.isRunning() ? "Running" : "Stopped")
              << " (port " << config_.tcpPort << ")"
              << (tcpServer_.hasClient() ? " [CLIENT CONNECTED]" : "") << std::endl;
    std::cout << "Discovery: " << (discovery_.isRunning() ? "Active" : "Inactive") << std::endl;
    std::cout << "ADB: " << (adbManager_.isRunning() ? "Active" : "Inactive") << std::endl;
    std::cout << "Bluetooth: " << (btServer_.isRunning() ? "Running" : "Not running")
              << (btServer_.hasClient() ? " [CLIENT CONNECTED]" : "") << std::endl;

    auto ips = DiscoveryService::getLocalIPs();
    std::cout << "IP Addresses:";
    for (const auto& ip : ips) {
        std::cout << " " << ip;
    }
    std::cout << "\n" << std::endl;
}

void Server::handleSetKey(const std::string& args) {
    std::istringstream iss(args);
    std::string cmd, keyName;
    iss >> cmd >> keyName;

    int keyIndex = -1;
    if (cmd == "key1" || cmd == "Key1") {
        keyIndex = 0;
    } else if (cmd == "key2" || cmd == "Key2") {
        keyIndex = 1;
    } else if (cmd == "setkey" || cmd == "SetKey") {
        std::string idxStr;
        iss >> idxStr;
        keyName = idxStr;
        iss >> keyName;
        if (idxStr == "1") keyIndex = 0;
        else if (idxStr == "2") keyIndex = 1;
    }

    if (keyIndex < 0 || keyName.empty()) {
        std::cout << "Usage: key1 <keyname> or key2 <keyname>" << std::endl;
        return;
    }

    std::transform(keyName.begin(), keyName.end(), keyName.begin(), ::toupper);
    WORD vk = InputSimulator::nameToVKCode(keyName);
    if (vk == 0) {
        std::cout << "Unknown key: " << keyName << std::endl;
        std::cout << "Try: A-Z, 0-9, F1-F12, SPACE, ENTER, SHIFT, etc." << std::endl;
        return;
    }

    if (input_.isKeyDown(static_cast<uint8_t>(keyIndex))) {
        input_.processInput(static_cast<uint8_t>(keyIndex), false);
    }

    input_.setKey(static_cast<uint8_t>(keyIndex), vk);
    if (keyIndex == 0) config_.key1 = vk;
    else config_.key2 = vk;
    config_.save();

    std::string configJson;
    buildWelcomeJson(configJson);
    tcpServer_.sendMessage(configJson);

    std::cout << "Key " << (keyIndex + 1) << " set to: " << InputSimulator::vkCodeToName(vk) << std::endl;
}

void Server::buildWelcomeJson(std::string& out) {
    out = "{"
          "\"type\":\"welcome\","
          "\"version\":" + std::to_string(protocol::PROTOCOL_VERSION) + ","
          "\"tcp_port\":" + std::to_string(config_.tcpPort) + ","
          "\"udp_port\":" + std::to_string(config_.udpPort) + ","
          "\"keys\":[\"" + input_.getKeyName(0) + "\",\"" + input_.getKeyName(1) + "\"]"
          "}";
}

std::string Server::getClientName() const {
    if (tcpServer_.hasClient()) {
        return tcpServer_.getClient().deviceName;
    }
    return "";
}

void Server::setKeyBinding(int index, const std::string& keyName) {
    if (index < 0 || index > 1) return;

    std::string upperName = keyName;
    std::transform(upperName.begin(), upperName.end(), upperName.begin(), ::toupper);
    WORD vk = InputSimulator::nameToVKCode(upperName);
    if (vk == 0) return;

    if (input_.isKeyDown(static_cast<uint8_t>(index))) {
        input_.processInput(static_cast<uint8_t>(index), false);
    }

    input_.setKey(static_cast<uint8_t>(index), vk);
    if (index == 0) config_.key1 = vk;
    else config_.key2 = vk;
    config_.save();

    std::string configJson;
    buildWelcomeJson(configJson);
    tcpServer_.sendMessage(configJson);

    LOG_INFO("Server", "Key " + std::to_string(index + 1) + " set to: " + InputSimulator::vkCodeToName(vk));
}

} // namespace rosk
