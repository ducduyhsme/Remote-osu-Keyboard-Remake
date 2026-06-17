#pragma once
#include <string>
#include <vector>
#include <thread>
#include <atomic>

namespace rosk {

class AdbManager {
public:
    AdbManager();
    ~AdbManager();

    void start();
    void stop();

    bool isRunning() const { return running_.load(); }

private:
    std::atomic<bool> running_{false};
    std::thread workerThread_;
    std::string lastBroadcastPcIp_;

    std::string getAdbPath();
    std::string getBundledAdbPath() const;
    std::string quotePath(const std::string& path) const;
    bool downloadAdbIfNeeded();
    bool runCommand(const std::string& cmd, std::string& outResult);
    
    void workerLoop();
    void detectTetheringAndBroadcast();
    
    std::string getLocalIpForSubnet(const std::string& targetIp);
};

} // namespace rosk
