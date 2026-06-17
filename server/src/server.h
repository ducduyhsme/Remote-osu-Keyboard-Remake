#pragma once
/*
 * Main server class that orchestrates all components:
 * - UDP input server
 * - TCP control server
 * - Discovery service
 * - Bluetooth server
 * - Input simulator
 * - Configuration
 */

#include "config.h"
#include "input_simulator.h"
#include "network/udp_server.h"
#include "network/tcp_server.h"
#include "network/discovery.h"
#include "network/bluetooth_server.h"
#include "network/adb_manager.h"
#include <atomic>
#include <string>

namespace rosk {

class Server {
public:
    Server();
    ~Server();

    // Initialize and start all services
    bool start();

    // Stop all services
    void stop();

    // Run the interactive command loop (blocking) — for console mode
    void runCommandLoop();

    bool isRunning() const { return running_.load(); }

    // ── GUI Accessors ──────────────────────────────────
    const Config& getConfig() const { return config_; }
    uint64_t getInputsProcessed() const { return inputsProcessed_.load(); }

    bool isUdpRunning() const { return udpServer_.isRunning(); }
    bool isTcpRunning() const { return tcpServer_.isRunning(); }
    bool isDiscoveryRunning() const { return discovery_.isRunning(); }
    bool isBtRunning() const { return btServer_.isRunning(); }

    bool hasTcpClient() const { return tcpServer_.hasClient(); }
    bool hasBtClient() const { return btServer_.hasClient(); }
    std::string getClientName() const;

    std::string getKeyName(int index) const { return input_.getKeyName(static_cast<uint8_t>(index)); }

    // GUI setters
    void setKeyBinding(int index, const std::string& keyName);
    void setShowKeyPresses(bool val) { config_.showKeyPresses = val; config_.save(); }
    void setBluetoothEnabled(bool val) { config_.bluetoothEnabled = val; config_.save(); }

private:
    void printStatus();
    void printHelp();
    void handleSetKey(const std::string& args);
    void buildWelcomeJson(std::string& out);

    Config config_;
    InputSimulator input_;
    UdpServer udpServer_;
    TcpServer tcpServer_;
    DiscoveryService discovery_;
    BluetoothServer btServer_;
    AdbManager adbManager_;

    std::atomic<bool> running_{ false };

    // Stats
    std::atomic<uint64_t> inputsProcessed_{ 0 };
};

} // namespace rosk
