#include "input_simulator.h"
#include "logger.h"
#include <algorithm>

namespace rosk {

std::unordered_map<std::string, WORD> InputSimulator::nameToVK_;
std::unordered_map<WORD, std::string> InputSimulator::vkToName_;
bool InputSimulator::mapInitialized_ = false;

InputSimulator::InputSimulator() {
    initKeyMap();
}

void InputSimulator::keyDown(WORD virtualKeyCode) {
    INPUT input = {};
    input.type = INPUT_KEYBOARD;
    input.ki.wVk = virtualKeyCode;
    input.ki.wScan = static_cast<WORD>(MapVirtualKeyW(virtualKeyCode, MAPVK_VK_TO_VSC));
    input.ki.dwFlags = 0;  // KEYEVENTF_KEYDOWN is 0
    input.ki.time = 0;
    input.ki.dwExtraInfo = GetMessageExtraInfo();

    SendInput(1, &input, sizeof(INPUT));
}

void InputSimulator::keyUp(WORD virtualKeyCode) {
    INPUT input = {};
    input.type = INPUT_KEYBOARD;
    input.ki.wVk = virtualKeyCode;
    input.ki.wScan = static_cast<WORD>(MapVirtualKeyW(virtualKeyCode, MAPVK_VK_TO_VSC));
    input.ki.dwFlags = KEYEVENTF_KEYUP;
    input.ki.time = 0;
    input.ki.dwExtraInfo = GetMessageExtraInfo();

    SendInput(1, &input, sizeof(INPUT));
}

void InputSimulator::processInput(uint8_t keyIndex, bool down) {
    if (keyIndex >= 2) return;

    // Prevent duplicate key events
    if (keyStates_[keyIndex] == down) return;

    keyStates_[keyIndex] = down;

    if (down) {
        keyDown(keys_[keyIndex]);
    } else {
        keyUp(keys_[keyIndex]);
    }
}

bool InputSimulator::isKeyDown(uint8_t keyIndex) const {
    if (keyIndex >= 2) return false;
    return keyStates_[keyIndex];
}

void InputSimulator::setKey(uint8_t index, WORD vkCode) {
    if (index >= 2) return;
    keys_[index] = vkCode;
    LOG_INFO("Input", "Key " + std::to_string(index) + " set to: " + vkCodeToName(vkCode));
}

WORD InputSimulator::getKey(uint8_t index) const {
    if (index >= 2) return 0;
    return keys_[index];
}

std::string InputSimulator::getKeyName(uint8_t index) const {
    if (index >= 2) return "?";
    return vkCodeToName(keys_[index]);
}

void InputSimulator::releaseAll() {
    for (uint8_t i = 0; i < 2; ++i) {
        if (keyStates_[i]) {
            keyUp(keys_[i]);
            keyStates_[i] = false;
        }
    }
}

WORD InputSimulator::nameToVKCode(const std::string& name) {
    initKeyMap();
    std::string upper = name;
    std::transform(upper.begin(), upper.end(), upper.begin(), ::toupper);
    auto it = nameToVK_.find(upper);
    if (it != nameToVK_.end()) return it->second;
    return 0;
}

std::string InputSimulator::vkCodeToName(WORD vkCode) {
    initKeyMap();
    auto it = vkToName_.find(vkCode);
    if (it != vkToName_.end()) return it->second;
    return "0x" + std::to_string(vkCode);
}

std::vector<std::string> InputSimulator::getAllKeyNames() {
    initKeyMap();
    std::vector<std::string> names;
    names.reserve(vkToName_.size());
    for (const auto& [vk, name] : vkToName_) {
        names.push_back(name);
    }
    std::sort(names.begin(), names.end());
    return names;
}

void InputSimulator::initKeyMap() {
    if (mapInitialized_) return;
    mapInitialized_ = true;

    auto add = [](const std::string& name, WORD vk) {
        nameToVK_[name] = vk;
        vkToName_[vk] = name;
    };

    // Letters A-Z
    for (char c = 'A'; c <= 'Z'; ++c) {
        add(std::string(1, c), static_cast<WORD>(c));
    }

    // Numbers 0-9
    for (char c = '0'; c <= '9'; ++c) {
        add(std::string(1, c), static_cast<WORD>(c));
    }

    // Function keys F1-F12
    for (int i = 1; i <= 12; ++i) {
        add("F" + std::to_string(i), static_cast<WORD>(VK_F1 + i - 1));
    }

    // Numpad
    for (int i = 0; i <= 9; ++i) {
        add("NUMPAD" + std::to_string(i), static_cast<WORD>(VK_NUMPAD0 + i));
    }

    // Special keys
    add("SPACE",     VK_SPACE);
    add("ENTER",     VK_RETURN);
    add("TAB",       VK_TAB);
    add("ESCAPE",    VK_ESCAPE);
    add("ESC",       VK_ESCAPE);
    add("BACKSPACE", VK_BACK);
    add("DELETE",    VK_DELETE);
    add("INSERT",    VK_INSERT);
    add("HOME",      VK_HOME);
    add("END",       VK_END);
    add("PAGEUP",    VK_PRIOR);
    add("PAGEDOWN",  VK_NEXT);
    add("UP",        VK_UP);
    add("DOWN",      VK_DOWN);
    add("LEFT",      VK_LEFT);
    add("RIGHT",     VK_RIGHT);

    // Modifier keys
    add("SHIFT",     VK_SHIFT);
    add("LSHIFT",    VK_LSHIFT);
    add("RSHIFT",    VK_RSHIFT);
    add("CTRL",      VK_CONTROL);
    add("LCTRL",     VK_LCONTROL);
    add("RCTRL",     VK_RCONTROL);
    add("ALT",       VK_MENU);
    add("LALT",      VK_LMENU);
    add("RALT",      VK_RMENU);

    // Punctuation
    add("SEMICOLON",    VK_OEM_1);      // ;:
    add("EQUALS",       VK_OEM_PLUS);   // =+
    add("COMMA",        VK_OEM_COMMA);  // ,<
    add("MINUS",        VK_OEM_MINUS);  // -_
    add("PERIOD",       VK_OEM_PERIOD); // .>
    add("SLASH",        VK_OEM_2);      // /?
    add("BACKTICK",     VK_OEM_3);      // `~
    add("LBRACKET",     VK_OEM_4);      // [{
    add("BACKSLASH",    VK_OEM_5);      // \|
    add("RBRACKET",     VK_OEM_6);      // ]}
    add("QUOTE",        VK_OEM_7);      // '"

    // Numpad operators
    add("NUMPAD_MULTIPLY", VK_MULTIPLY);
    add("NUMPAD_ADD",      VK_ADD);
    add("NUMPAD_SUBTRACT", VK_SUBTRACT);
    add("NUMPAD_DECIMAL",  VK_DECIMAL);
    add("NUMPAD_DIVIDE",   VK_DIVIDE);

    // Other
    add("CAPSLOCK",   VK_CAPITAL);
    add("NUMLOCK",    VK_NUMLOCK);
    add("SCROLLLOCK", VK_SCROLL);
    add("PRINTSCREEN", VK_SNAPSHOT);
    add("PAUSE",      VK_PAUSE);
}

} // namespace rosk
