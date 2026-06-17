#include "logger.h"
#include <iostream>
#include <iomanip>
#include <sstream>
#include <Windows.h>

namespace rosk {

Logger& Logger::instance() {
    static Logger instance;
    return instance;
}

Logger::Logger() : startTime_(std::chrono::steady_clock::now()) {
    // Enable ANSI escape codes on Windows 10+
    HANDLE hOut = GetStdHandle(STD_OUTPUT_HANDLE);
    if (hOut != INVALID_HANDLE_VALUE) {
        DWORD mode = 0;
        if (GetConsoleMode(hOut, &mode)) {
            SetConsoleMode(hOut, mode | ENABLE_VIRTUAL_TERMINAL_PROCESSING);
        }
    }
}

void Logger::log(LogLevel level, const std::string& category, const std::string& message) {
    if (level < minLevel_) return;

    std::lock_guard<std::mutex> lock(mutex_);

    // Build log line
    std::ostringstream oss;

    // Color codes
    const char* color = "";
    const char* levelStr = "";
    switch (level) {
        case LogLevel::LVL_DEBUG: color = "\033[90m";   levelStr = "DEBUG"; break;  // Gray
        case LogLevel::LVL_INFO:  color = "\033[36m";   levelStr = "INFO ";  break;  // Cyan
        case LogLevel::LVL_WARN:  color = "\033[33m";   levelStr = "WARN ";  break;  // Yellow
        case LogLevel::LVL_ERROR: color = "\033[31;1m"; levelStr = "ERROR"; break;  // Bold Red
    }

    // Timestamp (ms since start)
    if (showTimestamp_) {
        auto now = std::chrono::steady_clock::now();
        auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(now - startTime_).count();
        oss << "\033[90m[" << std::setfill('0') << std::setw(8) << elapsed << "ms]\033[0m ";
    }

    // Level + Category + Message
    oss << color << "[" << levelStr << "]\033[0m "
        << "\033[35m[" << category << "]\033[0m "  // Magenta category
        << message;

    std::cout << oss.str() << std::endl;

    // Fire callback with plain-text version (no ANSI codes)
    if (callback_) {
        std::ostringstream plain;
        if (showTimestamp_) {
            auto now = std::chrono::steady_clock::now();
            auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(now - startTime_).count();
            plain << "[" << std::setfill('0') << std::setw(8) << elapsed << "ms] ";
        }
        plain << "[" << levelStr << "] [" << category << "] " << message;
        callback_(level, category, plain.str());
    }
}

void Logger::debug(const std::string& category, const std::string& message) {
    log(LogLevel::LVL_DEBUG, category, message);
}

void Logger::info(const std::string& category, const std::string& message) {
    log(LogLevel::LVL_INFO, category, message);
}

void Logger::warn(const std::string& category, const std::string& message) {
    log(LogLevel::LVL_WARN, category, message);
}

void Logger::error(const std::string& category, const std::string& message) {
    log(LogLevel::LVL_ERROR, category, message);
}

} // namespace rosk
