/*
 * Remote osu! Keyboard Server — Entry Point
 *
 * A high-performance input relay server for osu! players.
 * Receives touch inputs from Android devices over WiFi/USB/Bluetooth
 * and simulates keyboard presses using the Windows SendInput API.
 *
 * Now with a native Win32 GUI!
 */

#include "server.h"
#include "logger.h"
#include "gui_window.h"
#include <WinSock2.h>
#include <iostream>
#include <string>
#include <csignal>
#include <thread>

#pragma comment(lib, "ws2_32.lib")
#pragma comment(lib, "Bthprops.lib")
#pragma comment(lib, "iphlpapi.lib")

static rosk::Server* g_server = nullptr;

BOOL WINAPI consoleHandler(DWORD event) {
    if (event == CTRL_C_EVENT || event == CTRL_CLOSE_EVENT) {
        if (g_server) {
            g_server->stop();
        }
        return TRUE;
    }
    return FALSE;
}

static void setupConsole() {
    if (!AllocConsole()) return;
    SetConsoleTitleW(L"Remote osu! Keyboard — Logs");
    
    FILE* fp = nullptr;
    freopen_s(&fp, "CONOUT$", "w", stdout);
    freopen_s(&fp, "CONOUT$", "w", stderr);
    freopen_s(&fp, "CONIN$", "r", stdin);
    
    // Sync C++ streams with C stdio
    std::ios::sync_with_stdio(true);
}

int WINAPI WinMain(HINSTANCE hInstance, HINSTANCE, LPSTR lpCmdLine, int nCmdShow) {
    // Allocate a console for log output
    setupConsole();

    // Initialize WinSock
    WSADATA wsaData;
    int result = WSAStartup(MAKEWORD(2, 2), &wsaData);
    if (result != 0) {
        MessageBoxW(nullptr, L"WSAStartup failed!", L"Error", MB_ICONERROR);
        return 1;
    }

    // Parse command line (check for --console flag for legacy mode)
    std::string cmdLine(lpCmdLine ? lpCmdLine : "");
    bool consoleMode = (cmdLine.find("--console") != std::string::npos);
    bool debugMode = (cmdLine.find("--debug") != std::string::npos || cmdLine.find("-d") != std::string::npos);

    if (debugMode) {
        rosk::Logger::instance().setMinLevel(rosk::LogLevel::LVL_DEBUG);
    }

    // Set console handler
    SetConsoleCtrlHandler(consoleHandler, TRUE);

    // Create server
    rosk::Server server;
    g_server = &server;

    if (consoleMode) {
        // Legacy console mode
        std::cout << R"(
  ____                      _          ___            _   _  __          _                          _
 |  _ \ ___ _ __ ___   ___ | |_ ___   / _ \ ___ _   _| | | |/ /___  ___| |__   ___   __ _ _ __ __| |
 | |_) / _ \ '_ ` _ \ / _ \| __/ _ \ | | | / __| | | | |_| ' // _ \/ _ \ '_ \ / _ \ / _` | '__/ _` |
 |  _ <  __/ | | | | | (_) | ||  __/ | |_| \__ \ |_| |  _| . \  __/  __/ |_) | (_) | (_| | | | (_| |
 |_| \_\___|_| |_| |_|\___/ \__\___|  \___/|___/\__,_|_| |_|\_\___|\___| _.__/ \___/ \__,_|_|  \__,_|
)" << std::endl;
        std::cout << "  Version " APP_VERSION " | Console Mode" << std::endl << std::endl;

        if (!server.start()) {
            LOG_ERR("Main", "Failed to start server");
            WSACleanup();
            return 1;
        }
        server.runCommandLoop();
    } else {
        // GUI mode
        rosk::GuiWindow gui;
        gui.setServer(&server);

        // Create GUI first before wiring logger
        if (!gui.create(hInstance)) {
            WSACleanup();
            return 1;
        }

        // Logger is already logging to the console, so we don't need a callback for GUI anymore

        // Auto-start server
        if (!server.start()) {
            // Log the error but still show GUI
            LOG_ERR("Main", "Failed to start server — check configuration");
        }

        // Run GUI message loop (blocks until window closes)
        int exitCode = gui.run();

        // Clear logger callback before gui goes out of scope
        rosk::Logger::instance().setCallback(nullptr);

        // Cleanup
        if (server.isRunning()) {
            server.stop();
        }

        g_server = nullptr;
        WSACleanup();
        return exitCode;
    }

    // Cleanup (console mode)
    g_server = nullptr;
    WSACleanup();
    return 0;
}
