#pragma once
/*
 * Configuration management.
 * Stores settings in JSON format in %APPDATA%/RemoteOsuKeyboard/config.json
 */

#include <string>
#include <cstdint>

namespace rosk {

struct Config {
    // Key bindings (virtual key codes)
    uint16_t key1 = 0x5A;  // Z
    uint16_t key2 = 0x58;  // X

    // Network
    uint16_t udpPort = 7220;
    uint16_t tcpPort = 7221;

    // Bluetooth
    bool bluetoothEnabled = true;

    // Logging
    bool showKeyPresses = true;
    bool showLatency = true;

    // Load from file, returns false if file doesn't exist (uses defaults)
    bool load();

    // Save to file
    bool save() const;

    // Get the config file path
    static std::string getConfigPath();

private:
    static std::string getConfigDir();
};

} // namespace rosk
