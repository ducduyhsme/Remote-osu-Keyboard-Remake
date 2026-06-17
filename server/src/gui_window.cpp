#include "gui_window.h"
#include "server.h"
#include "logger.h"
#include "input_simulator.h"
#include <CommCtrl.h>
#include <windowsx.h>
#include <sstream>
#include <iomanip>
#include <algorithm>

#pragma comment(lib, "Comctl32.lib")
#pragma comment(lib, "Gdi32.lib")

namespace rosk {

// ── Layout Constants ───────────────────────────────────
static constexpr int WIN_W = 720;
static constexpr int WIN_H = 600;
static constexpr int MARGIN = 20;
static constexpr int PANEL_PAD = 16;
static constexpr int ROW_H = 28;
static constexpr int PANEL_RADIUS = 8;

// ── GuiWindow Implementation ──────────────────────────

GuiWindow::GuiWindow() {}

GuiWindow::~GuiWindow() {
    if (fontRegular_) DeleteObject(fontRegular_);
    if (fontBold_)    DeleteObject(fontBold_);
    if (fontTitle_)   DeleteObject(fontTitle_);
    if (bgBrush_)     DeleteObject(bgBrush_);
    if (surfaceBrush_) DeleteObject(surfaceBrush_);
}

bool GuiWindow::create(HINSTANCE hInstance) {
    hInstance_ = hInstance;

    // Init common controls
    INITCOMMONCONTROLSEX icex;
    icex.dwSize = sizeof(icex);
    icex.dwICC = ICC_STANDARD_CLASSES;
    InitCommonControlsEx(&icex);

    // Create fonts
    fontRegular_ = CreateFontW(17, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
        DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
        DEFAULT_PITCH, L"Segoe UI");
    fontBold_ = CreateFontW(17, 0, 0, 0, FW_BOLD, FALSE, FALSE, FALSE,
        DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
        DEFAULT_PITCH, L"Segoe UI");
    fontTitle_ = CreateFontW(28, 0, 0, 0, FW_BOLD, FALSE, FALSE, FALSE,
        DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
        DEFAULT_PITCH, L"Segoe UI");

    bgBrush_ = CreateSolidBrush(GuiColors::Background);
    surfaceBrush_ = CreateSolidBrush(GuiColors::Surface);

    // Register window class
    WNDCLASSEXW wc = {};
    wc.cbSize = sizeof(wc);
    wc.style = CS_HREDRAW | CS_VREDRAW;
    wc.lpfnWndProc = WndProc;
    wc.hInstance = hInstance;
    wc.hCursor = LoadCursor(nullptr, IDC_ARROW);
    wc.hbrBackground = bgBrush_;
    wc.lpszClassName = L"ROSKWindowClass";
    wc.hIcon = LoadIcon(nullptr, IDI_APPLICATION);
    wc.hIconSm = LoadIcon(nullptr, IDI_APPLICATION);

    if (!RegisterClassExW(&wc)) return false;

    // Calculate centered window position
    int screenW = GetSystemMetrics(SM_CXSCREEN);
    int screenH = GetSystemMetrics(SM_CYSCREEN);
    int posX = (screenW - WIN_W) / 2;
    int posY = (screenH - WIN_H) / 2;

    // Create window
    hwnd_ = CreateWindowExW(
        0,
        L"ROSKWindowClass",
        L"Remote osu! Keyboard Server",
        WS_OVERLAPPEDWINDOW,
        posX, posY, WIN_W, WIN_H,
        nullptr, nullptr, hInstance, this
    );

    if (!hwnd_) return false;

    createControls();
    ShowWindow(hwnd_, SW_SHOW);
    UpdateWindow(hwnd_);

    // Start refresh timer (500ms)
    SetTimer(hwnd_, ID_TIMER_REFRESH, 500, nullptr);

    return true;
}

int GuiWindow::run() {
    MSG msg;
    while (GetMessageW(&msg, nullptr, 0, 0)) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }
    return (int)msg.wParam;
}

// Removed addLog

// ── Window Procedure ───────────────────────────────────

LRESULT CALLBACK GuiWindow::WndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    GuiWindow* self = nullptr;

    if (msg == WM_NCCREATE) {
        auto* cs = reinterpret_cast<CREATESTRUCT*>(lParam);
        self = reinterpret_cast<GuiWindow*>(cs->lpCreateParams);
        SetWindowLongPtrW(hwnd, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(self));
        self->hwnd_ = hwnd;
        return DefWindowProcW(hwnd, msg, wParam, lParam);
    }

    self = reinterpret_cast<GuiWindow*>(GetWindowLongPtrW(hwnd, GWLP_USERDATA));
    if (self) {
        return self->handleMessage(msg, wParam, lParam);
    }
    return DefWindowProcW(hwnd, msg, wParam, lParam);
}

LRESULT GuiWindow::handleMessage(UINT msg, WPARAM wParam, LPARAM lParam) {
    switch (msg) {
    case WM_PAINT: {
        PAINTSTRUCT ps;
        HDC hdc = BeginPaint(hwnd_, &ps);
        onPaint(hdc);
        EndPaint(hwnd_, &ps);
        return 0;
    }

    case WM_SIZE: {
        if (!hwnd_) break;
        RECT rc;
        GetClientRect(hwnd_, &rc);
        int panelW = rc.right - 2 * MARGIN;
        
        if (btnStartStop_) {
            SetWindowPos(btnStartStop_, nullptr, MARGIN + panelW - 140, 60, 140, 36, SWP_NOZORDER);
        }
        if (cmbKey1_) {
            SetWindowPos(cmbKey1_, nullptr, MARGIN + 100, 280, 140, 300, SWP_NOZORDER);
        }
        if (cmbKey2_) {
            SetWindowPos(cmbKey2_, nullptr, MARGIN + 380, 280, 140, 300, SWP_NOZORDER);
        }
        if (chkBluetooth_) {
            SetWindowPos(chkBluetooth_, nullptr, MARGIN, 530, 180, 28, SWP_NOZORDER);
        }
        
        InvalidateRect(hwnd_, nullptr, TRUE);
        return 0;
    }

    case WM_ERASEBKGND:
        return 1; // We handle background in WM_PAINT

    case WM_CTLCOLORSTATIC:
    case WM_CTLCOLORBTN: {
        if (!surfaceBrush_) break;
        HDC hdcCtrl = (HDC)wParam;
        SetBkColor(hdcCtrl, GuiColors::Surface);
        SetTextColor(hdcCtrl, GuiColors::TextPrimary);
        return (LRESULT)surfaceBrush_;
    }

    case WM_CTLCOLORLISTBOX: {
        if (!bgBrush_) break;
        HDC hdcCtrl = (HDC)wParam;
        SetBkColor(hdcCtrl, GuiColors::Background);
        SetTextColor(hdcCtrl, GuiColors::TextPrimary);
        return (LRESULT)bgBrush_;
    }

    case WM_DRAWITEM: {
        auto* dis = reinterpret_cast<DRAWITEMSTRUCT*>(lParam);
        if (!dis) break;

// Removed ID_LOG_LIST custom draw

        if (dis->CtlID == ID_BTN_STARTSTOP) {
            bool isStartStop = (dis->CtlID == ID_BTN_STARTSTOP);
            COLORREF btnColor = isStartStop ?
                (serverStarted_ ? GuiColors::Red : GuiColors::Green) : GuiColors::SurfaceLight;
            COLORREF textColor = isStartStop ? RGB(255, 255, 255) : GuiColors::TextPrimary;

            HBRUSH btnBrush = CreateSolidBrush(btnColor);
            
            // Draw rounded rect
            HPEN pen = CreatePen(PS_SOLID, 1, btnColor);
            HGDIOBJ oldBrush = SelectObject(dis->hDC, btnBrush);
            HGDIOBJ oldPen = SelectObject(dis->hDC, pen);
            RoundRect(dis->hDC, dis->rcItem.left, dis->rcItem.top,
                      dis->rcItem.right, dis->rcItem.bottom, 8, 8);
            SelectObject(dis->hDC, oldBrush);
            SelectObject(dis->hDC, oldPen);
            DeleteObject(pen);
            DeleteObject(btnBrush);

            // Draw text
            SetBkMode(dis->hDC, TRANSPARENT);
            SetTextColor(dis->hDC, textColor);
            HFONT drawFont = fontBold_ ? fontBold_ : (HFONT)GetStockObject(SYSTEM_FONT);
            HFONT oldFont = (HFONT)SelectObject(dis->hDC, drawFont);

            wchar_t btnText[64] = {};
            GetWindowTextW(dis->hwndItem, btnText, 64);
            DrawTextW(dis->hDC, btnText, -1, &dis->rcItem,
                      DT_CENTER | DT_VCENTER | DT_SINGLELINE);
            SelectObject(dis->hDC, oldFont);
            return TRUE;
        }
        break;
    }

// Removed WM_MEASUREITEM

    case WM_COMMAND:
        onCommand(wParam, lParam);
        return 0;

    case WM_TIMER:
        if (wParam == ID_TIMER_REFRESH) {
            onTimer();
        }
        return 0;

// Removed WM_APP+1

    case WM_DESTROY:
        KillTimer(hwnd_, ID_TIMER_REFRESH);
        PostQuitMessage(0);
        return 0;

    case WM_CLOSE:
        if (server_ && server_->isRunning()) {
            server_->stop();
        }
        DestroyWindow(hwnd_);
        return 0;
    }

    return DefWindowProcW(hwnd_, msg, wParam, lParam);
}

// ── Control Creation ───────────────────────────────────

void GuiWindow::createControls() {
    int x = MARGIN;
    int y = 85; // After title and status line
    int panelW = WIN_W - 2 * MARGIN - 16; // Account for window borders

    // Start/Stop button
    btnStartStop_ = CreateWindowExW(0, L"BUTTON", L"Stop Server",
        WS_CHILD | WS_VISIBLE | BS_OWNERDRAW,
        panelW - 100, 52, 120, 30,
        hwnd_, (HMENU)ID_BTN_STARTSTOP, hInstance_, nullptr);

    // ── Key Bindings ──
    int keyY = 215;
    // Key 1 combo
    cmbKey1_ = CreateWindowExW(0, L"COMBOBOX", nullptr,
        WS_CHILD | WS_VISIBLE | CBS_DROPDOWNLIST | WS_VSCROLL,
        x + 80, keyY + 30, 120, 300,
        hwnd_, (HMENU)ID_CMB_KEY1, hInstance_, nullptr);
    if (cmbKey1_) SendMessageW(cmbKey1_, WM_SETFONT, (WPARAM)fontRegular_, TRUE);

    // Key 2 combo
    cmbKey2_ = CreateWindowExW(0, L"COMBOBOX", nullptr,
        WS_CHILD | WS_VISIBLE | CBS_DROPDOWNLIST | WS_VSCROLL,
        x + 300, keyY + 30, 120, 300,
        hwnd_, (HMENU)ID_CMB_KEY2, hInstance_, nullptr);
    if (cmbKey2_) SendMessageW(cmbKey2_, WM_SETFONT, (WPARAM)fontRegular_, TRUE);

    // Populate combos
    if (server_ && cmbKey1_ && cmbKey2_) {
        populateKeyComboBox(cmbKey1_, server_->getKeyName(0));
        populateKeyComboBox(cmbKey2_, server_->getKeyName(1));
    }

    // ── Settings Checkboxes ──
    int settY = 430;

    chkBluetooth_ = CreateWindowExW(0, L"BUTTON", L"Bluetooth Enabled",
        WS_CHILD | WS_VISIBLE | BS_AUTOCHECKBOX,
        x, settY, 160, 24,
        hwnd_, (HMENU)ID_CHK_BLUETOOTH, hInstance_, nullptr);
    if (chkBluetooth_) SendMessageW(chkBluetooth_, WM_SETFONT, (WPARAM)fontRegular_, TRUE);

    // Set initial checkbox states
    if (server_) {
        auto& cfg = server_->getConfig();
        if (chkBluetooth_) SendMessageW(chkBluetooth_, BM_SETCHECK, cfg.bluetoothEnabled ? BST_CHECKED : BST_UNCHECKED, 0);
    }

    serverStarted_ = server_ && server_->isRunning();
    if (btnStartStop_) SetWindowTextW(btnStartStop_, serverStarted_ ? L"Stop Server" : L"Start Server");
}

void GuiWindow::populateKeyComboBox(HWND combo, const std::string& currentKey) {
    if (!combo) return;
    auto keys = InputSimulator::getAllKeyNames();
    int selectIdx = 0;
    for (int i = 0; i < (int)keys.size(); i++) {
        std::wstring wkey = toWide(keys[i]);
        SendMessageW(combo, CB_ADDSTRING, 0, (LPARAM)wkey.c_str());
        if (keys[i] == currentKey) selectIdx = i;
    }
    SendMessageW(combo, CB_SETCURSEL, selectIdx, 0);
}

// ── Painting ───────────────────────────────────────────

void GuiWindow::onPaint(HDC hdc) {
    RECT clientRc;
    GetClientRect(hwnd_, &clientRc);
    if (clientRc.right <= 0 || clientRc.bottom <= 0) return;

    // Double buffer
    HDC memDC = CreateCompatibleDC(hdc);
    if (!memDC) return;
    HBITMAP memBmp = CreateCompatibleBitmap(hdc, clientRc.right, clientRc.bottom);
    if (!memBmp) { DeleteDC(memDC); return; }
    HBITMAP oldBmp = (HBITMAP)SelectObject(memDC, memBmp);

    // Fill background
    FillRect(memDC, &clientRc, bgBrush_);

    int x = MARGIN;
    int panelW = clientRc.right - 2 * MARGIN;

    // ── Title ──
    drawText(memDC, x, 12, L"Remote osu! Keyboard", GuiColors::Accent, true, 24);
    drawText(memDC, x, 42, L"Server v" L"1.0.0", GuiColors::TextSecondary, false, 14);

    // ── Server Status ──
    int statusY = 66;
    drawStatusDot(memDC, x, statusY + 6, serverStarted_);
    drawText(memDC, x + 20, statusY, 
             serverStarted_ ? L"Server Running" : L"Server Stopped",
             serverStarted_ ? GuiColors::Green : GuiColors::Red, true, 17);

    // ── Connection Panel ──
    int connY = 110;
    RECT connRc = { x, connY, x + panelW, connY + 125 };
    drawPanel(memDC, connRc, L"Connection");

    drawText(memDC, x + PANEL_PAD, connY + 32, L"IP Addresses:", GuiColors::TextSecondary, false, 15);
    drawText(memDC, x + PANEL_PAD + 105, connY + 32, statusIPs_.empty() ? L"Detecting..." : statusIPs_, GuiColors::TextPrimary, false, 15);

    drawText(memDC, x + PANEL_PAD, connY + 56, L"UDP Port:", GuiColors::TextSecondary, false, 15);
    drawText(memDC, x + PANEL_PAD + 80, connY + 56, L"7220", GuiColors::TextPrimary, false, 15);
    drawText(memDC, x + PANEL_PAD + 175, connY + 56, L"TCP Port:", GuiColors::TextSecondary, false, 15);
    drawText(memDC, x + PANEL_PAD + 250, connY + 56, L"7221", GuiColors::TextPrimary, false, 15);

    drawText(memDC, x + PANEL_PAD, connY + 80, L"Client:", GuiColors::TextSecondary, false, 15);
    if (clientConnected_) {
        drawText(memDC, x + PANEL_PAD + 65, connY + 80, statusClient_, GuiColors::Green, true, 15);
    } else {
        drawText(memDC, x + PANEL_PAD + 65, connY + 80, L"No client connected", GuiColors::TextSecondary, false, 15);
    }

    if (btClientConnected_) {
        drawText(memDC, x + PANEL_PAD, connY + 104, L"Bluetooth:", GuiColors::TextSecondary, false, 15);
        drawText(memDC, x + PANEL_PAD + 85, connY + 104, L"Connected", GuiColors::Green, true, 15);
    }

    // ── Key Bindings Panel ──
    int keyY = 245;
    RECT keyRc = { x, keyY, x + panelW, keyY + 80 };
    drawPanel(memDC, keyRc, L"Key Bindings");
    drawText(memDC, x + PANEL_PAD, keyY + 39, L"Key 1:", GuiColors::TextSecondary, false, 15);
    drawText(memDC, x + PANEL_PAD + 280, keyY + 39, L"Key 2:", GuiColors::TextSecondary, false, 15);

    // ── Services Panel ──
    int svcY = 335;
    RECT svcRc = { x, svcY, x + panelW, svcY + 160 };
    drawPanel(memDC, svcRc, L"Services");

    int svcRow = svcY + 32;
    drawStatusDot(memDC, x + PANEL_PAD, svcRow + 6, udpRunning_);
    drawText(memDC, x + PANEL_PAD + 20, svcRow, L"UDP Input Server", GuiColors::TextPrimary, false, 15);
    drawText(memDC, x + panelW - 130, svcRow, udpRunning_ ? L"Running" : L"Stopped",
             udpRunning_ ? GuiColors::Green : GuiColors::Red, false, 15);

    svcRow += ROW_H;
    drawStatusDot(memDC, x + PANEL_PAD, svcRow + 6, tcpRunning_);
    drawText(memDC, x + PANEL_PAD + 20, svcRow, L"TCP Control Server", GuiColors::TextPrimary, false, 15);
    std::wstring tcpStatus = tcpRunning_ ? (clientConnected_ ? L"Running (1 client)" : L"Running") : L"Stopped";
    drawText(memDC, x + panelW - 130, svcRow, tcpStatus,
             tcpRunning_ ? GuiColors::Green : GuiColors::Red, false, 15);

    svcRow += ROW_H;
    drawStatusDot(memDC, x + PANEL_PAD, svcRow + 6, discoveryRunning_);
    drawText(memDC, x + PANEL_PAD + 20, svcRow, L"Discovery Service", GuiColors::TextPrimary, false, 15);
    drawText(memDC, x + panelW - 130, svcRow, discoveryRunning_ ? L"Active" : L"Inactive",
             discoveryRunning_ ? GuiColors::Green : GuiColors::Red, false, 15);

    svcRow += ROW_H;
    drawStatusDot(memDC, x + PANEL_PAD, svcRow + 6, btRunning_);
    drawText(memDC, x + PANEL_PAD + 20, svcRow, L"Bluetooth Server", GuiColors::TextPrimary, false, 15);
    drawText(memDC, x + panelW - 130, svcRow, btRunning_ ? L"Running" : L"Off",
             btRunning_ ? GuiColors::Green : GuiColors::TextSecondary, false, 15);



    // ── Settings label ──
    int settY = 530;
    drawText(memDC, x, settY - 18, L"Settings", GuiColors::TextPrimary, true, 16);

    // Blit
    BitBlt(hdc, 0, 0, clientRc.right, clientRc.bottom, memDC, 0, 0, SRCCOPY);
    SelectObject(memDC, oldBmp);
    DeleteObject(memBmp);
    DeleteDC(memDC);
}

void GuiWindow::drawPanel(HDC hdc, const RECT& rc, const std::wstring& title) {
    HBRUSH brush = CreateSolidBrush(GuiColors::Surface);
    HPEN pen = CreatePen(PS_SOLID, 1, GuiColors::Border);
    HGDIOBJ oldBrush = SelectObject(hdc, brush);
    HGDIOBJ oldPen = SelectObject(hdc, pen);
    RoundRect(hdc, rc.left, rc.top, rc.right, rc.bottom, PANEL_RADIUS * 2, PANEL_RADIUS * 2);
    SelectObject(hdc, oldBrush);
    SelectObject(hdc, oldPen);
    DeleteObject(brush);
    DeleteObject(pen);

    // Panel title
    drawText(hdc, rc.left + PANEL_PAD, rc.top + 8, title, GuiColors::AccentPurple, true, 15);
}

void GuiWindow::drawStatusDot(HDC hdc, int x, int y, bool running) {
    COLORREF dotColor = running ? GuiColors::Green : GuiColors::Red;
    HBRUSH brush = CreateSolidBrush(dotColor);
    HPEN pen = CreatePen(PS_SOLID, 1, dotColor);
    HGDIOBJ oldBrush = SelectObject(hdc, brush);
    HGDIOBJ oldPen = SelectObject(hdc, pen);
    Ellipse(hdc, x, y, x + 10, y + 10);
    SelectObject(hdc, oldBrush);
    SelectObject(hdc, oldPen);
    DeleteObject(brush);
    DeleteObject(pen);
}

void GuiWindow::drawText(HDC hdc, int x, int y, const std::wstring& text, COLORREF color, bool bold, int size) {
    HFONT font = CreateFontW(size, 0, 0, 0, bold ? FW_BOLD : FW_NORMAL, FALSE, FALSE, FALSE,
        DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
        DEFAULT_PITCH, L"Segoe UI");
    if (!font) return;
    HGDIOBJ oldFont = SelectObject(hdc, font);
    SetBkMode(hdc, TRANSPARENT);
    SetTextColor(hdc, color);
    TextOutW(hdc, x, y, text.c_str(), (int)text.size());
    SelectObject(hdc, oldFont);
    DeleteObject(font);
}

void GuiWindow::drawTextRight(HDC hdc, int x, int y, int rightEdge, const std::wstring& text, COLORREF color, bool bold, int size) {
    HFONT font = CreateFontW(size, 0, 0, 0, bold ? FW_BOLD : FW_NORMAL, FALSE, FALSE, FALSE,
        DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
        DEFAULT_PITCH, L"Segoe UI");
    if (!font) return;
    HGDIOBJ oldFont = SelectObject(hdc, font);
    SetBkMode(hdc, TRANSPARENT);
    SetTextColor(hdc, color);
    RECT rc = { x, y, rightEdge, y + size + 4 };
    DrawTextW(hdc, text.c_str(), (int)text.size(), &rc, DT_RIGHT | DT_SINGLELINE);
    SelectObject(hdc, oldFont);
    DeleteObject(font);
}

// ── Event Handlers ─────────────────────────────────────

void GuiWindow::onCommand(WPARAM wParam, LPARAM lParam) {
    int id = LOWORD(wParam);
    int notif = HIWORD(wParam);

    switch (id) {
    case ID_BTN_STARTSTOP:
        onStartStop();
        break;

    case ID_CMB_KEY1:
        if (notif == CBN_SELCHANGE) onKeyChange(0);
        break;

    case ID_CMB_KEY2:
        if (notif == CBN_SELCHANGE) onKeyChange(1);
        break;

    case ID_CHK_BLUETOOTH:
        if (server_) {
            bool checked = chkBluetooth_ && (SendMessageW(chkBluetooth_, BM_GETCHECK, 0, 0) == BST_CHECKED);
            server_->setBluetoothEnabled(checked);
        }
        break;
    }
}

void GuiWindow::onStartStop() {
    if (!server_) return;

    if (serverStarted_) {
        server_->stop();
        serverStarted_ = false;
        if (btnStartStop_) SetWindowTextW(btnStartStop_, L"Start Server");
    } else {
        if (server_->start()) {
            serverStarted_ = true;
            if (btnStartStop_) SetWindowTextW(btnStartStop_, L"Stop Server");
            // Refresh key combos after start (config loaded)
            if (cmbKey1_) {
                SendMessageW(cmbKey1_, CB_RESETCONTENT, 0, 0);
                populateKeyComboBox(cmbKey1_, server_->getKeyName(0));
            }
            if (cmbKey2_) {
                SendMessageW(cmbKey2_, CB_RESETCONTENT, 0, 0);
                populateKeyComboBox(cmbKey2_, server_->getKeyName(1));
            }
        }
    }
    InvalidateRect(hwnd_, nullptr, FALSE);
}

void GuiWindow::onKeyChange(int keyIndex) {
    if (!server_) return;

    HWND combo = (keyIndex == 0) ? cmbKey1_ : cmbKey2_;
    if (!combo) return;
    int sel = (int)SendMessageW(combo, CB_GETCURSEL, 0, 0);
    if (sel == CB_ERR) return;

    wchar_t buf[64] = {};
    SendMessageW(combo, CB_GETLBTEXT, sel, (LPARAM)buf);

    // Convert to narrow string
    std::string keyName;
    for (int i = 0; buf[i]; i++) keyName += (char)buf[i];

    server_->setKeyBinding(keyIndex, keyName);
}

void GuiWindow::onTimer() {
    refreshStatus();
    InvalidateRect(hwnd_, nullptr, FALSE);
}

void GuiWindow::refreshStatus() {
    if (!server_) return;

    serverStarted_ = server_->isRunning();
    if (btnStartStop_) SetWindowTextW(btnStartStop_, serverStarted_ ? L"Stop Server" : L"Start Server");

    // IPs
    auto ips = DiscoveryService::getLocalIPs();
    std::wstring ipStr;
    for (size_t i = 0; i < ips.size(); i++) {
        if (i > 0) ipStr += L", ";
        ipStr += toWide(ips[i]);
    }
    statusIPs_ = ipStr;

    // Service states
    udpRunning_ = server_->isUdpRunning();
    tcpRunning_ = server_->isTcpRunning();
    discoveryRunning_ = server_->isDiscoveryRunning();
    btRunning_ = server_->isBtRunning();
    clientConnected_ = server_->hasTcpClient();
    btClientConnected_ = server_->hasBtClient();

    if (clientConnected_) {
        statusClient_ = toWide(server_->getClientName());
    }


}

std::wstring GuiWindow::toWide(const std::string& str) {
    if (str.empty()) return L"";
    int len = MultiByteToWideChar(CP_UTF8, 0, str.c_str(), (int)str.size(), nullptr, 0);
    if (len <= 0) return L"";
    std::wstring wstr(len, L'\0');
    MultiByteToWideChar(CP_UTF8, 0, str.c_str(), (int)str.size(), &wstr[0], len);
    return wstr;
}

} // namespace rosk
