package com.rosk.remoteosukey.network

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Connection states
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

class ConnectionManager {

    // Protocol constants
    companion object {
        private const val UDP_INPUT_PORT = 7220
        private const val TCP_CONTROL_PORT = 7221
        private const val PROTOCOL_VERSION = 1
        private const val TCP_CONNECT_TIMEOUT_MS = 3000
        private const val TCP_READ_TIMEOUT_MS = 5000

        // Packet types
        private const val PACKET_INPUT: Byte = 0x01
        private const val PACKET_PING: Byte = 0x02
        private const val PACKET_PONG: Byte = 0x03
        private const val PACKET_HEARTBEAT: Byte = 0x04

        // Key actions
        private const val KEY_UP: Byte = 0x00
        private const val KEY_DOWN: Byte = 0x01
    }

    // Connection state
    private var udpSocket: DatagramSocket? = null
    private var tcpSocket: Socket? = null
    private var btSocket: BluetoothSocket? = null
    private var serverAddress: InetAddress? = null
    private var udpPort: Int = UDP_INPUT_PORT
    private var tcpPort: Int = TCP_CONTROL_PORT
    private var connectionType: String = "wifi"

    private val connected = AtomicBoolean(false)
    private val sequence = AtomicInteger(0)

    // Packet format must match server/src/protocol.h:
    // [TYPE] [KEY_INDEX] [ACTION] [SEQUENCE]

    // Heartbeat job
    private var heartbeatJob: Job? = null
    private var latencyJob: Job? = null

    private val sendScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Connect to the PC server.
     */
    suspend fun connect(type: String, address: String): Boolean = withContext(Dispatchers.IO) {
        disconnect()

        connectionType = type
        
        try {
            when (type) {
                "wifi", "usb_tethering" -> connectTcp(address, useUdpInput = true)
                "usb_adb" -> connectTcp(address, useUdpInput = false)
                "bluetooth" -> connectBluetooth(address)
                else -> false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            disconnect()
            false
        }
    }

    private suspend fun connectTcp(address: String, useUdpInput: Boolean): Boolean {
        val endpoint = parseEndpoint(address)
        serverAddress = InetAddress.getByName(endpoint.host)
        tcpPort = endpoint.port

        // 1. Establish TCP connection for control/handshake
        val tcp = Socket()
        tcp.tcpNoDelay = true
        tcp.soTimeout = TCP_READ_TIMEOUT_MS
        tcp.connect(InetSocketAddress(serverAddress, tcpPort), TCP_CONNECT_TIMEOUT_MS)
        tcpSocket = tcp

        // Handshake
        val deviceName = escapeJson(android.os.Build.MODEL)
        sendTcpMessage(
            tcp.getOutputStream(),
            """{"type":"hello","version":$PROTOCOL_VERSION,"device":"$deviceName"}"""
        )
        val response = readTcpMessage(tcp.getInputStream())
        
        if (response == null || !response.contains("welcome")) {
            tcp.close()
            return false
        }

        udpPort = parseUdpPort(response)

        // 2. Setup UDP socket for low-latency input when the transport supports it.
        if (useUdpInput) {
            udpSocket = DatagramSocket().apply {
                soTimeout = 1000
                sendBufferSize = 1024
            }
        } else {
            udpSocket = null
        }
        
        connected.set(true)
        startHeartbeat()
        
        return true
    }

    private suspend fun connectBluetooth(address: String): Boolean {
        var adapter: BluetoothAdapter? = null
        try {
            adapter = BluetoothAdapter.getDefaultAdapter()
        } catch (e: Exception) { e.printStackTrace() }
        
        if (adapter == null) return false

        val serverUuid = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef0123456789")
        
        try {
            // Stop discovery before connecting, as it slows down connection
            try {
                if (adapter.isDiscovering) {
                    adapter.cancelDiscovery()
                }
            } catch (e: SecurityException) { }

            val device = adapter.getRemoteDevice(address)
            btSocket = device.createRfcommSocketToServiceRecord(serverUuid)
            btSocket?.connect()
            connected.set(true)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            try { btSocket?.close() } catch (ignored: Exception) {}
            btSocket = null
        }
        return false
    }

    /**
     * Sends a key event to the PC.
     */
    fun sendKeyEvent(keyIndex: Int, isDown: Boolean) {
        if (!connected.get()) return

        // 4-byte packet format: [TYPE] [KEY_INDEX] [ACTION] [SEQUENCE]
        val packetBytes = ByteArray(4)
        packetBytes[0] = PACKET_INPUT
        packetBytes[1] = keyIndex.toByte()
        packetBytes[2] = if (isDown) KEY_DOWN else KEY_UP
        packetBytes[3] = sequence.incrementAndGet().toByte()

        sendScope.launch {
            when (connectionType) {
                "wifi", "usb_tethering" -> {
                    try {
                        val address = serverAddress ?: return@launch
                        val socket = udpSocket ?: return@launch
                        socket.send(DatagramPacket(packetBytes, packetBytes.size, address, udpPort))
                    } catch (e: Exception) { }
                }
                "usb_adb" -> {
                    // Send via TCP (ADB reverse only supports TCP)
                    try {
                        synchronized(this@ConnectionManager) {
                            val out = tcpSocket?.outputStream
                            if (out != null) {
                                out.write(packetBytes)
                                out.flush()
                            }
                        }
                    } catch (_: Exception) {
                        tcpSocket?.close()
                        tcpSocket = null
                    }
                }
                "bluetooth" -> {
                    try {
                        btSocket?.outputStream?.write(packetBytes)
                    } catch (_: Exception) { }
                }
            }
        }
    }

    /**
     * Start a UDP ping to measure latency.
     */
    fun startLatencyMonitor(onLatency: (Int) -> Unit) {
        latencyJob?.cancel()
        latencyJob = sendScope.launch {
            if (connectionType != "wifi" && connectionType != "usb_tethering") return@launch

            val address = serverAddress ?: return@launch
            val socket = udpSocket ?: return@launch
            val pingBuffer = ByteArray(4)
            pingBuffer[0] = PACKET_PING
            val pingPacket = DatagramPacket(pingBuffer, 4, address, udpPort)
            
            while (connected.get() && isActive) {
                try {
                    val sendTime = System.currentTimeMillis()
                    socket.send(pingPacket)
                    
                    withTimeoutOrNull(1000) {
                        val pongBuffer = ByteArray(4)
                        val pongPacket = DatagramPacket(pongBuffer, 4)
                        socket.receive(pongPacket)
                        
                        if (pongBuffer[0] == PACKET_PONG) {
                            val latency = (System.currentTimeMillis() - sendTime).toInt()
                            withContext(Dispatchers.Main) {
                                onLatency(latency)
                            }
                        }
                    }
                } catch (e: Exception) { }
                delay(2000)
            }
        }
    }

    /**
     * Send periodic heartbeats so the PC knows we're alive
     * and NAT UDP port mappings are kept open.
     */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = sendScope.launch {
            val hbBuffer = ByteArray(4)
            hbBuffer[0] = PACKET_HEARTBEAT
            
            while (connected.get() && isActive) {
                try {
                    // ALWAYS send TCP heartbeat to keep the control connection alive
                    synchronized(this@ConnectionManager) {
                        tcpSocket?.outputStream?.write(hbBuffer)
                        tcpSocket?.outputStream?.flush()
                    }

                    // For Wifi/Tethering, also send UDP heartbeat to keep UDP NAT alive.
                    if (connectionType == "wifi" || connectionType == "usb_tethering") {
                        val address = serverAddress
                        val socket = udpSocket
                        if (address != null && socket != null) {
                            val hbPacket = DatagramPacket(hbBuffer, 4, address, udpPort)
                            socket.send(hbPacket)
                        }
                    }
                } catch (e: Exception) { }
                delay(1000)
            }
        }
    }

    /**
     * Disconnect from the server.
     */
    fun disconnect() {
        connected.set(false)
        heartbeatJob?.cancel()
        latencyJob?.cancel()

        // Send disconnect message via TCP
        try {
            tcpSocket?.let { sock ->
                if (!sock.isClosed) {
                    synchronized(this@ConnectionManager) {
                        sendTcpMessage(sock.getOutputStream(), """{"type":"disconnect"}""")
                    }
                }
            }
        } catch (_: Exception) { }

        udpSocket?.close()
        tcpSocket?.close()
        btSocket?.close()

        udpSocket = null
        tcpSocket = null
        btSocket = null
        serverAddress = null
        udpPort = UDP_INPUT_PORT
        tcpPort = TCP_CONTROL_PORT
    }

    private data class Endpoint(val host: String, val port: Int)

    private fun parseEndpoint(value: String): Endpoint {
        val trimmed = value.trim()
        val separator = trimmed.lastIndexOf(':')
        if (separator > 0 && separator < trimmed.lastIndex - 1) {
            val host = trimmed.substring(0, separator)
            val port = trimmed.substring(separator + 1).toIntOrNull()
            if (port != null && port in 1..65535) {
                return Endpoint(host, port)
            }
        }
        return Endpoint(trimmed, TCP_CONTROL_PORT)
    }

    private fun parseUdpPort(json: String): Int {
        return Regex("\"udp_port\"\\s*:\\s*(\\d+)")
            .find(json)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: UDP_INPUT_PORT
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    // TCP message helpers (length-prefixed JSON)
    private fun sendTcpMessage(output: OutputStream, json: String) {
        val data = json.toByteArray()
        val lengthBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(data.size).array()
        output.write(lengthBytes)
        output.write(data)
        output.flush()
    }

    private fun readTcpMessage(input: InputStream): String? {
        val lengthBytes = ByteArray(4)
        var read = 0
        while (read < 4) {
            val n = input.read(lengthBytes, read, 4 - read)
            if (n < 0) return null
            read += n
        }
        val length = ByteBuffer.wrap(lengthBytes).order(ByteOrder.LITTLE_ENDIAN).int
        if (length > 4096 || length <= 0) return null

        val data = ByteArray(length)
        read = 0
        while (read < length) {
            val n = input.read(data, read, length - read)
            if (n < 0) return null
            read += n
        }
        return String(data)
    }
}
