cmake_minimum_required(VERSION 3.20)
project(RemoteOsuKeyboard VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Windows-specific settings
if(WIN32)
    add_definitions(-D_WIN32_WINNT=0x0A00)  # Windows 10+
    add_definitions(-DWIN32_LEAN_AND_MEAN)
    add_definitions(-DNOMINMAX)
    add_definitions(-DUNICODE -D_UNICODE)
endif()

# Source files
set(SOURCES
    src/main.cpp
    src/server.cpp
    src/input_simulator.cpp
    src/config.cpp
    src/logger.cpp
    src/network/tcp_server.cpp
    src/network/udp_server.cpp
    src/network/discovery.cpp
    src/network/bluetooth_server.cpp
    src/network/adb_manager.cpp
    src/gui_window.cpp
)

set(HEADERS
    src/server.h
    src/input_simulator.h
    src/config.h
    src/logger.h
    src/protocol.h
    src/network/tcp_server.h
    src/network/udp_server.h
    src/network/discovery.h
    src/network/bluetooth_server.h
    src/network/adb_manager.h
    src/gui_window.h
)

add_executable(${PROJECT_NAME} ${SOURCES} ${HEADERS})

# Link Windows libraries
target_link_libraries(${PROJECT_NAME} PRIVATE
    ws2_32           # Winsock2
    Bthprops         # Bluetooth
    SetupAPI         # Device enumeration
    User32           # Input injection
    iphlpapi         # IP helpers
    Ole32
    Shell32
    Gdi32
    Comctl32
    Comdlg32
)

# Set as Windows GUI application because entry point is WinMain
set_target_properties(${PROJECT_NAME} PROPERTIES
    WIN32_EXECUTABLE TRUE
    VS_DEBUGGER_WORKING_DIRECTORY "${CMAKE_SOURCE_DIR}"
)

# Add application manifest for proper Windows integration
if(MSVC)
    target_sources(${PROJECT_NAME} PRIVATE resources/app.manifest)
    # Enable whole program optimization for release
    target_compile_options(${PROJECT_NAME} PRIVATE
        $<$<CONFIG:Release>:/O2 /GL>
    )
    target_link_options(${PROJECT_NAME} PRIVATE
        $<$<CONFIG:Release>:/LTCG>
    )
endif()

# Version info
target_compile_definitions(${PROJECT_NAME} PRIVATE
    APP_VERSION="${PROJECT_VERSION}"
    APP_NAME="Remote osu! Keyboard"
)
