package de.sscvolumecontrol.ssc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpConnection(private val ipv6Address: String) : ConnectionBase() {
    private val SSC_PORT = 45
    private var udpSocket: DatagramSocket? = null

    init {
        udpSocket = DatagramSocket()
    }

    override fun disconnect() {
        udpSocket?.close()
        udpSocket = null
    }

    override suspend fun sendImpl(json: JsonObject): JsonObject {
        val udpSocket = udpSocket ?: throw IllegalStateException("Device is not connected")
        val message = json.toString().toByteArray()
        val udpPacketTx = DatagramPacket(message, message.size, InetAddress.getByName(ipv6Address), SSC_PORT)
        udpSocket.send(udpPacketTx)

        val buffer = ByteArray(1024)
        val udpPacketRx = DatagramPacket(buffer, buffer.size)
        udpSocket.receive(udpPacketRx)

        val response = String(udpPacketRx?.data ?: ByteArray(0)).trim()
        return Json.decodeFromString(JsonObject.serializer(), response)
    }
}