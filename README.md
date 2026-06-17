# Remote osu! Keyboard

A high-performance, ultra-low latency touch-to-keyboard input relay for [osu!](https://osu.ppy.sh/) players. Use your Android phone as a wireless (or wired) keyboard for osu!.

## ✨ Features

- **Ultra-low latency**: ~5-15ms over WiFi, ~1-5ms over USB
- **Zero dropped inputs**: UDP fire-and-forget protocol with no head-of-line blocking
- **3 connection modes**: WiFi, USB (ADB), and Bluetooth (plus USB Tethering)
- **Zero Configuration USB**: The PC server automatically downloads ADB, forwards ports, and detects Tethering IPs. No manual command-line typing required!
- **Floating Full Screen Mode**: Place your two fingers anywhere on the screen! The app automatically tracks them as Key 1 and Key 2.
- **Auto-discovery**: Server is automatically found on the local network
- **Configurable keys**: Change key bindings to any key
- **Real-time latency display**: See your current latency while playing
- **No antivirus issues**: Uses standard Windows SendInput API (no suspicious DLLs)

## 📦 Project Structure

```
├── server/          # PC Server (C++ / Windows)
│   ├── src/
│   │   ├── main.cpp
│   │   ├── server.cpp/.h
│   │   ├── input_simulator.cpp/.h
│   │   ├── config.cpp/.h
│   │   ├── protocol.h
│   │   ├── logger.cpp/.h
│   │   └── network/
│   │       ├── udp_server.cpp/.h
│   │       ├── tcp_server.cpp/.h
│   │       ├── discovery.cpp/.h
│   │       └── bluetooth_server.cpp/.h
│   ├── resources/
│   │   └── app.manifest
│   └── CMakeLists.txt
│
└── android/         # Android Client (Kotlin / Jetpack Compose)
    └── app/src/main/java/com/rosk/remoteosukey/
        ├── MainActivity.kt
        ├── ui/
        │   ├── HomeScreen.kt
        │   ├── PlayScreen.kt
        │   ├── SettingsScreen.kt
        │   └── theme/Theme.kt
        ├── network/
        │   ├── ConnectionManager.kt
        │   └── ServerDiscovery.kt
        └── input/
            └── TouchProcessor.kt
```

## 🚀 Installation

### PC Server (Windows)

#### Prerequisites
- Windows 10 or later
- [CMake](https://cmake.org/download/) 3.20+
- [Visual Studio 2022](https://visualstudio.microsoft.com/) with C++ desktop development workload
  - Or any C++20 compatible compiler (MSVC, MinGW, Clang)

#### Build from Source

```bash
cd server
mkdir build
cd build
cmake ..
cmake --build . --config Release
```

The executable `RemoteOsuKeyboard.exe` will be in `build/Release/`.

#### Running the Server

```bash
RemoteOsuKeyboard.exe
```

**Command-line options:**
```
--debug, -d          Enable debug logging
--key1 <key>, -k1    Set key 1 (default: Z)
--key2 <key>, -k2    Set key 2 (default: X)
--help, -h           Show help
```

**Interactive commands (while running):**
```
help        Show available commands
status      Show server status and IP addresses
key1 <key>  Change key 1 (e.g., key1 A)
key2 <key>  Change key 2 (e.g., key2 S)
stats       Show input statistics
quit        Stop server
```

#### Firewall Setup

On first run, Windows Firewall may ask to allow the app. Click **"Allow"** for both private and public networks. If you have issues:

1. Open **Windows Defender Firewall**
2. Click **"Allow an app through firewall"**
3. Add `RemoteOsuKeyboard.exe`
4. Check both **Private** and **Public** boxes

### Android Client

#### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- Android device running Android 8.0 (API 26) or later
- USB Debugging enabled (for USB mode)

#### Build from Source

1. Open the `android/` folder in Android Studio
2. Sync Gradle
3. Build and run on your device

#### Install from APK

Download the latest APK from the [Releases](../../releases) page and install it on your Android device.

---

## 📡 Connection Modes

### WiFi (Recommended)

1. Connect both your PC and phone to the **same WiFi network**
2. Start the PC server → it will show your IP address
3. Open the Android app → select **WiFi**
4. The server should be **auto-discovered** in the list
5. Tap the detected server to connect

### USB (Lowest Latency)

1. Enable **USB Debugging** on your Android phone:
   - Go to Settings → About Phone → tap "Build Number" 7 times
   - Go to Settings → Developer Options → enable "USB Debugging"
2. Connect your phone to PC via USB cable
3. Start the PC server. **The server will automatically download `adb` in the background and set up port forwarding for you!** (No need to type `adb reverse` manually).
4. Open the Android app → select **USB** → tap **Connect via USB**.

### USB Tethering (Alternative Low Latency)

1. Enable both **USB Debugging** and **USB Tethering** on your Android phone.
2. Connect your phone to PC via USB cable.
3. Start the PC server. It will automatically detect your tethering network IP!
4. Open the Android app → select **WiFi**. The server will be auto-discovered over the tethered network just like regular WiFi.

### Bluetooth (Not Recommended)

> ⚠️ Bluetooth has inherently higher latency (20-50ms+) and is **not recommended** for competitive play.

1. Pair your phone with your PC via Bluetooth
2. Start the PC server (Bluetooth must be enabled on PC)
3. Open the Android app → select **Bluetooth** → tap **Scan & Connect**

---

## 🎮 How to Play

1. Connect to the server using any connection mode
2. The play screen shows a **split-screen layout**:
   - **Left half** = Key 1 (default: Z)
   - **Right half** = Key 2 (default: X)
3. Tap the left side to press Key 1, right side for Key 2
4. **Accidental touches from extra fingers are automatically ignored!**

### Touch Modes

- **Split Screen** (default): Left/right halves are separate keys. Simple and reliable.
- **Full Screen (Floating)**: Two fingers can float freely anywhere on the screen! The first finger to touch is automatically assigned as Key 1, and the second is Key 2.

### Prevent Third Finger (Experimental)

In Settings, you can enable the experimental "Prevent Third Finger" mode. When enabled, you can calibrate 5 fingers and select your **Finger Pair** (e.g. Index + Ring) to help the app intelligently reject accidental touches from other fingers.

---

## 🛡️ Antivirus

This app uses the standard Windows `SendInput` API to simulate keyboard presses. Unlike the original Ro!KS which used a custom `VirtualKeyPress.dll`, this approach:

- Uses only standard Windows API calls
- Includes a proper application manifest
- Does not use code obfuscation or packing

If your antivirus still flags it:
1. Add `RemoteOsuKeyboard.exe` to your antivirus exclusion list
2. For Windows Defender: Settings → Virus & threat protection → Exclusions → Add an exclusion

---

## 🔧 Troubleshooting

| Issue | Solution |
|-------|----------|
| Server not found (WiFi) | Check firewall, make sure both devices are on same network |
| High latency | Try USB mode, close other network-heavy applications |
| Dropped inputs | Switch from Bluetooth to WiFi or USB |
| USB mode not working | Run `adb devices` to verify connection, re-run `adb reverse` |
| App crashes on phone | Make sure you're running Android 8.0 or later |

## 📊 Protocol Overview

| Protocol | Port | Purpose |
|----------|------|---------|
| UDP | 7220 | Input relay (4-byte packets) |
| TCP | 7221 | Handshake & configuration |
| UDP | 7222 | Auto-discovery broadcast |

---

## 📄 License

MIT License - see [LICENSE](LICENSE) for details.
