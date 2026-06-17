#pragma once
/*
 * Remote osu! Keyboard — Wire Protocol
 * 
 * Designed for absolute minimum latency:
 * - UDP input packets are only 4 bytes
 * - No acknowledgment needed for input (fire-and-forget)
 * - TCP is used only for handshake and configuration
 * - Discovery uses UDP broadcast
 */

#include <cstdint>

namespace rosk {
namespace protocol {

// ============================================================
// Port Configuration
// ============================================================
constexpr uint16_t UDP_INPUT_PORT    = 7220;  // UDP: input relay (key events)
constexpr uint16_t TCP_CONTROL_PORT  = 7221;  // TCP: handshake, config, keepalive
constexpr uint16_t UDP_DISCOVER_PORT = 7222;  // UDP: auto-discovery broadcast

// ============================================================
// UDP Input Packet (4 bytes — absolute minimum)
// ============================================================
// Sent from Android client to PC server for every touch event.
// Using UDP to avoid TCP head-of-line blocking.
// If a packet is lost, the next one makes it obsolete.

enum class PacketType : uint8_t {
    INPUT       = 0x01,  // Key press/release event
    PING        = 0x02,  // Latency measurement
    PONG        = 0x03,  // Latency response
    HEARTBEAT   = 0x04,  // Keep connection alive
};

enum class KeyAction : uint8_t {
    KEY_UP   = 0x00,  // Key released
    KEY_DOWN = 0x01,  // Key pressed
};

// Input packet layout (4 bytes total):
// [0] PacketType  (1 byte) — always INPUT (0x01)
// [1] KeyIndex    (1 byte) — 0 = left/key1, 1 = right/key2
// [2] KeyAction   (1 byte) — 0 = up, 1 = down
// [3] SequenceNum (1 byte) — wrapping counter for ordering
#pragma pack(push, 1)
struct InputPacket {
    PacketType type;
    uint8_t    keyIndex;
    KeyAction  action;
    uint8_t    sequence;
};

struct PingPacket {
    PacketType type;
    uint8_t    padding;
    uint16_t   timestamp;  // milliseconds since connection (wrapping)
};

struct PongPacket {
    PacketType type;
    uint8_t    padding;
    uint16_t   echoTimestamp;  // echo the ping timestamp back
};

struct HeartbeatPacket {
    PacketType type;
    uint8_t    padding[3];
};
#pragma pack(pop)

static_assert(sizeof(InputPacket) == 4, "InputPacket must be 4 bytes");
static_assert(sizeof(PingPacket) == 4, "PingPacket must be 4 bytes");
static_assert(sizeof(PongPacket) == 4, "PongPacket must be 4 bytes");

// ============================================================
// TCP Handshake Messages (JSON over TCP)
// ============================================================
// These are sent once during connection setup.
// Format: [4 bytes: message length (uint32_t LE)] [N bytes: JSON payload]
//
// Client → Server (Hello):
// {
//   "type": "hello",
//   "version": 1,
//   "device": "Samsung Galaxy S24"
// }
//
// Server → Client (Welcome):
// {
//   "type": "welcome",
//   "version": 1,
//   "name": "PC-NAME",
//   "udp_port": 7220,
//   "keys": ["Z", "X"]
// }
//
// Server → Client (Config Update):
// {
//   "type": "config",
//   "keys": ["Z", "X"]
// }
//
// Either → Either (Disconnect):
// {
//   "type": "disconnect"
// }

constexpr uint32_t PROTOCOL_VERSION = 1;
constexpr uint32_t MAX_TCP_MESSAGE_SIZE = 4096;

// ============================================================
// UDP Discovery Protocol
// ============================================================
// Client broadcasts JSON to UDP_DISCOVER_PORT:
//   {"type":"rosk_discover","version":2}
//
// Server responds unicast JSON:
//   {"type":"rosk_server","version":2,"name":"MyPC","tcp_port":7221,"udp_port":7220}

// ============================================================
// Connection Timeout
// ============================================================
constexpr int HEARTBEAT_INTERVAL_MS = 1000;   // Send heartbeat every 1s
constexpr int CONNECTION_TIMEOUT_MS = 5000;    // Disconnect after 5s no data
constexpr int DISCOVERY_TIMEOUT_MS  = 3000;    // Wait 3s for discovery responses

} // namespace protocol
} // namespace rosk
