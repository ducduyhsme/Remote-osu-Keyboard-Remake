#pragma once
/*
 * Thread-safe logger with timestamp and category support.
 * Outputs to console with color coding.
 */

#include <string>
#include <mutex>
#include <chrono>
#include <functional>
#include <utility>

namespace rosk {

enum class LogLevel {
    LVL_DEBUG,
    LVL_INFO,
    LVL_WARN,
    LVL_ERROR
};

class Logger {
public:
    static Logger& instance();

    void log(LogLevel level, const std::string& category, const std::string& message);
    void debug(const std::string& category, const std::string& message);
    void info(const std::string& category, const std::string& message);
    void warn(const std::string& category, const std::string& message);
    void error(const std::string& category, const std::string& message);

    void setMinLevel(LogLevel lvl) { minLevel_ = lvl; }
    void setShowTimestamp(bool show) { showTimestamp_ = show; }

    // Callback for GUI integration (optional)
    using LogCallback = std::function<void(LogLevel, const std::string&, const std::string&)>;
    void setCallback(LogCallback cb) { callback_ = std::move(cb); }

private:
    Logger();
    ~Logger() = default;

    std::mutex mutex_;
    LogLevel minLevel_ = LogLevel::LVL_INFO;
    bool showTimestamp_ = true;
    LogCallback callback_;
    std::chrono::steady_clock::time_point startTime_;
};

// Convenience macros
#define LOG_DEBUG(cat, msg) rosk::Logger::instance().debug(cat, msg)
#define LOG_INFO(cat, msg)  rosk::Logger::instance().info(cat, msg)
#define LOG_WARN(cat, msg)  rosk::Logger::instance().warn(cat, msg)
#define LOG_ERR(cat, msg)   rosk::Logger::instance().error(cat, msg)

} // namespace rosk
