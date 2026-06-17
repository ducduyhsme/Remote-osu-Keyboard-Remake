package com.rosk.remoteosukey.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

data class DiscoveredServer(
    val name: String,
    val address: String,
    val tcpPort: Int,
    val udpPort: Int
) {
    val endpoint: String
        get() = "$address:$tcpPort"
}

object ServerDiscovery {

    private const val DISCOVER_PORT = 7222
    private const val DISCOVER_TIMEOUT_MS = 2500
    private const val DISCOVER_MESSAGE = """{"type":"rosk_discover","version":2}"""

    suspend fun discover(): List<DiscoveredServer> = withContext(Dispatchers.IO) {
        val servers = linkedMapOf<String, DiscoveredServer>()

        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.reuseAddress = true
                socket.soTimeout = DISCOVER_TIMEOUT_MS

                val payload = DISCOVER_MESSAGE.toByteArray(Charsets.UTF_8)
                val targets = buildBroadcastTargets()
                for (target in targets) {
                    try {
                        socket.send(DatagramPacket(payload, payload.size, target, DISCOVER_PORT))
                    } catch (_: Exception) {
                    }
                }

                val buffer = ByteArray(1024)
                val deadline = System.currentTimeMillis() + DISCOVER_TIMEOUT_MS

                while (System.currentTimeMillis() < deadline) {
                    val remaining = (deadline - System.currentTimeMillis()).toInt()
                    if (remaining <= 0) break
                    socket.soTimeout = remaining

                    try {
                        val response = DatagramPacket(buffer, buffer.size)
                        socket.receive(response)

                        val json = String(response.data, 0, response.length, Charsets.UTF_8)
                        val server = parseServer(json, response.address.hostAddress ?: continue)
                        if (server != null) {
                            servers["${server.address}:${server.tcpPort}"] = server
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        break
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        servers.values.toList()
    }

    private fun buildBroadcastTargets(): List<InetAddress> {
        val targets = linkedSetOf<InetAddress>()
        targets.add(InetAddress.getByName("255.255.255.255"))

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            for (networkInterface in interfaces) {
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                for (ifAddr in networkInterface.interfaceAddresses) {
                    val address = ifAddr.address
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        ifAddr.broadcast?.let { targets.add(it) }
                    }
                }
            }
        } catch (_: Exception) {
        }

        return targets.toList()
    }

    private fun parseServer(json: String, sourceAddress: String): DiscoveredServer? {
        if (!json.contains("\"type\"") || !json.contains("rosk_server")) return null

        val name = readString(json, "name") ?: "Remote osu! Server"
        val tcpPort = readInt(json, "tcp_port") ?: 7221
        val udpPort = readInt(json, "udp_port") ?: 7220

        return DiscoveredServer(
            name = name,
            address = sourceAddress,
            tcpPort = tcpPort,
            udpPort = udpPort
        )
    }

    private fun readString(json: String, key: String): String? {
        return Regex(""""$key"\s*:\s*"((?:\\.|[^"])*)"""")
            .find(json)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")
    }

    private fun readInt(json: String, key: String): Int? {
        return Regex(""""$key"\s*:\s*(\d+)"""")
            .find(json)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }
}
