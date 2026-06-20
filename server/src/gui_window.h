#pragma once
/*
 * Win32 GUI Window for Remote osu! Keyboard Server.
 * Dark-themed native window with status panels, log view,
 * key binding controls, and service indicators.
 */

#include <Windows.h>
#include <string>
#include <vector>
#include <mutex>
#include <functional>

namespace rosk {

// Forward declarations
class Server;

// ── Dark Theme Colors ──────────────────────────────────
struct GuiColors {
    static constexpr COLORREF Background    = RGB(18, 18, 24);
    static constexpr COLORREF Surface       = RGB(28, 28, 38);
    static constexpr COLORREF SurfaceLight  = RGB(38, 38, 52);
    static constexpr COLORREF Border        = RGB(55, 55, 75);
    static constexpr COLORREF TextPrimary   = RGB(230, 230, 240);
    static constexpr COLORREF TextSecondary = RGB(160, 160, 180);
    static constexpr COLORREF Accent        = RGB(0, 230, 230);    // Neon Cyan
    static constexpr COLORREF AccentPurple  = RGB(150, 100, 255);
    static constexpr COLORREF Green         = RGB(0, 200, 120);
    static constexpr COLORREF Red           = RGB(230, 60, 60);
    static constexpr COLORREF Yellow        = RGB(230, 200, 50);
    static constexpr COLORREF LogDebug      = RGB(120, 120, 140);
    static constexpr COLORREF LogInfo       = RGB(0, 200, 200);
    static constexpr COLORREF LogWarn       = RGB(230, 200, 50);
    static constexpr COLORREF LogError      = RGB(230, 60, 60);
};

// ── Control IDs ────────────────────────────────────────
enum ControlID {
    ID_BTN_STARTSTOP = 1001,
    ID_CMB_KEY1      = 1002,
    ID_CMB_KEY2      = 1003,
    ID_CHK_BLUETOOTH  = 1005,
    ID_TIMER_REFRESH  = 2001,
};

// Removed Log Entry struct

// ── GuiWindow Class ────────────────────────────────────
class GuiWindow {
public:
    GuiWindow();
    ~GuiWindow();

    // Create and show the window. Returns false on failure.
    bool create(HINSTANCE hInstance);

    // Run the Win32 message loop. Blocks until window is closed.
    int run();

    // Set the server reference (must be called before run())
    void setServer(Server* server) { server_ = server; }

    // Add a log entry (removed)
    // void addLog(const std::string& text, COLORREF color);

    // Get HWND
    HWND getHwnd() const { return hwnd_; }

private:
    // Window procedure
    static LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam);
    LRESULT handleMessage(UINT msg, WPARAM wParam, LPARAM lParam);

    // Painting
    void onPaint(HDC hdc);
    void drawPanel(HDC hdc, const RECT& rc, const std::wstring& title);
    void drawStatusDot(HDC hdc, int x, int y, bool running);
    void drawText(HDC hdc, int x, int y, const std::wstring& text, COLORREF color, bool bold = false, int size = 14);
    void drawTextRight(HDC hdc, int x, int y, int rightEdge, const std::wstring& text, COLORREF color, bool bold = false, int size = 14);

    // Control creation
    void createControls();
    void populateKeyComboBox(HWND combo, const std::string& currentKey);

    // Event handlers
    void onStartStop();
    void onKeyChange(int keyIndex);
    void onTimer();
    void onCommand(WPARAM wParam, LPARAM lParam);

    // Helpers
    void refreshStatus();
    std::wstring toWide(const std::string& str);

    // Window data
    HWND hwnd_ = nullptr;
    HINSTANCE hInstance_ = nullptr;
    HFONT fontRegular_ = nullptr;
    HFONT fontBold_ = nullptr;
    HFONT fontTitle_ = nullptr;
    HBRUSH bgBrush_ = nullptr;
    HBRUSH surfaceBrush_ = nullptr;

    // Controls
    HWND btnStartStop_ = nullptr;
    HWND cmbKey1_ = nullptr;
    HWND cmbKey2_ = nullptr;
    HWND chkBluetooth_ = nullptr;

    // State
    Server* server_ = nullptr;
    bool serverStarted_ = false;

    // Cached status strings (updated on timer)
    std::wstring statusIPs_;
    std::wstring statusClient_;
    std::wstring statusInputCount_;
    bool udpRunning_ = false;
    bool tcpRunning_ = false;
    bool discoveryRunning_ = false;
    bool btRunning_ = false;
    bool wifiDirectRunning_ = false;
    bool clientConnected_ = false;
    bool btClientConnected_ = false;
    bool wifiDirectClientConnected_ = false;
};

} // namespace rosk
