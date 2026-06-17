#pragma once
/*
 * Keyboard input simulation using Windows SendInput API.
 * No external DLLs needed — uses only standard Win32 API.
 * This significantly reduces antivirus false positive rates.
 */

#include <Windows.h>
#include <string>
#include <unordered_map>
#include <vector>

namespace rosk {

class InputSimulator {
public:
    InputSimulator();
    ~InputSimulator() = default;

    // Press a key down (non-blocking, immediate)
    void keyDown(WORD virtualKeyCode);

    // Release a key (non-blocking, immediate)
    void keyUp(WORD virtualKeyCode);

    // Get the virtual key code from a readable name (e.g., "Z" -> 0x5A)
    static WORD nameToVKCode(const std::string& name);

    // Get readable name from virtual key code
    static std::string vkCodeToName(WORD vkCode);

    // Get list of all supported key names
    static std::vector<std::string> getAllKeyNames();

    // Check if a key is currently pressed (tracked internally)
    bool isKeyDown(uint8_t keyIndex) const;

    // Set key bindings
    void setKey(uint8_t index, WORD vkCode);
    WORD getKey(uint8_t index) const;
    std::string getKeyName(uint8_t index) const;

    // Process an input event from the network
    void processInput(uint8_t keyIndex, bool down);

    // Release all currently held keys (safety)
    void releaseAll();

private:
    static void initKeyMap();
    static std::unordered_map<std::string, WORD> nameToVK_;
    static std::unordered_map<WORD, std::string> vkToName_;
    static bool mapInitialized_;

    // Key bindings: index -> virtual key code
    // Default: key[0] = Z, key[1] = X (standard osu! keys)
    WORD keys_[2] = { 0x5A, 0x58 };  // Z, X

    // Track key states to prevent duplicate press/release
    bool keyStates_[2] = { false, false };
};

} // namespace rosk
