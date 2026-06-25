@echo off
REM ============================================================
REM  Remote osu! Keyboard Server — Build Script
REM  Requires: Visual Studio 2022 with C++ Desktop Development
REM            AND Windows 10/11 SDK
REM ============================================================

REM Find VS installation
set "VSCMD_START_DIR=%CD%"

if exist "C:\Program Files\Microsoft Visual Studio\2022\Community\Common7\Tools\VsDevCmd.bat" (
    call "C:\Program Files\Microsoft Visual Studio\2022\Community\Common7\Tools\VsDevCmd.bat" -arch=amd64 -host_arch=amd64 >nul 2>&1
) else if exist "C:\Program Files\Microsoft Visual Studio\2022\Professional\Common7\Tools\VsDevCmd.bat" (
    call "C:\Program Files\Microsoft Visual Studio\2022\Professional\Common7\Tools\VsDevCmd.bat" -arch=amd64 -host_arch=amd64 >nul 2>&1
) else if exist "C:\Program Files\Microsoft Visual Studio\2022\Enterprise\Common7\Tools\VsDevCmd.bat" (
    call "C:\Program Files\Microsoft Visual Studio\2022\Enterprise\Common7\Tools\VsDevCmd.bat" -arch=amd64 -host_arch=amd64 >nul 2>&1
) else if exist "C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools\Common7\Tools\VsDevCmd.bat" (
    call "C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools\Common7\Tools\VsDevCmd.bat" -arch=amd64 -host_arch=amd64 >nul 2>&1
) else (
    echo ERROR: Visual Studio 2022 not found!
    echo Please install Visual Studio 2022 with "Desktop development with C++" workload.
    exit /b 1
)

echo.
echo ====================================
echo  Building Remote osu! Keyboard Server
echo ====================================
echo.

REM Create build output directory
if not exist "build" mkdir build

REM Compile
cl.exe /std:c++20 /EHsc /O2 /W3 ^
    /DWIN32_LEAN_AND_MEAN /DNOMINMAX /DUNICODE /D_UNICODE /D_WIN32_WINNT=0x0A00 ^
    /DAPP_VERSION="\"1.0.0\"" /DAPP_NAME="\"Remote osu! Keyboard\"" ^
    /Fe:build\RemoteOsuKeyboard.exe ^
    /Fo:build\ ^
    src\main.cpp ^
    src\server.cpp ^
    src\input_simulator.cpp ^
    src\config.cpp ^
    src\logger.cpp ^
    src\gui_window.cpp ^
    src\network\tcp_server.cpp ^
    src\network\udp_server.cpp ^
    src\network\discovery.cpp ^
    src\network\bluetooth_server.cpp ^
    src\network\adb_manager.cpp ^
    /I src ^
    /link /SUBSYSTEM:WINDOWS ^
    ws2_32.lib Bthprops.lib iphlpapi.lib Ole32.lib Shell32.lib SetupAPI.lib User32.lib ^
    Gdi32.lib Comctl32.lib Comdlg32.lib

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ====================================
    echo  BUILD SUCCESSFUL!
    echo  Output: build\RemoteOsuKeyboard.exe
    echo ====================================
) else (
    echo.
    echo ====================================
    echo  BUILD FAILED!
    echo  Make sure Windows SDK is installed.
    echo  In Visual Studio Installer, check:
    echo   - Desktop development with C++
    echo   - Windows 10/11 SDK
    echo ====================================
    exit /b 1
)
