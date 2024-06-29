package io.github.zadagu.sscvolumecontrol.ssc


import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.net.InetAddress

class TcpConnection(private val ipv6Address: String) : ConnectionBase() {
    private val SSC_PORT = 45
    private var tcpSocket: Socket? = null
    private var tcpOutputStream: OutputStreamWriter? = null
    private var tcpInputStream: BufferedReader? = null
    private val sendMutex = Mutex()

    init {
        tcpSocket = Socket(InetAddress.getByName(ipv6Address), SSC_PORT)
        tcpOutputStream = OutputStreamWriter(tcpSocket!!.getOutputStream())
        tcpInputStream = BufferedReader(InputStreamReader(tcpSocket!!.getInputStream()))
    }

    override fun disconnect() {
        if (sendMutex.isLocked) {
            throw IllegalStateException("Cannot disconnect while sending")
        }
        tcpOutputStream?.close()
        tcpSocket?.close()
        tcpOutputStream = null
        tcpSocket = null
    }

    override suspend fun sendImpl(json: JsonObject): JsonObject {
        sendMutex.withLock {
            val tcpOutputStream = tcpOutputStream ?: throw IllegalStateException("Device is not connected")
            val tcpInputStream = tcpInputStream ?: throw IllegalStateException("Device is not connected")
            tcpOutputStream.write(json.toString() + "\r\n")
            tcpOutputStream.flush()

            val message = tcpInputStream.readLine() ?: throw IOException("No response received")
            return Json.decodeFromString(JsonObject.serializer(), message)
        }
    }
}