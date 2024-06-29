package io.github.zadagu.sscvolumecontrol.ssc

import android.util.Log
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

interface Connection {
    fun disconnect()
    suspend fun send(json: JsonObject): JsonObject
}

abstract class ConnectionBase : Connection {
    var connected = true
    override fun disconnect() {
        connected = false
    }

    protected abstract suspend fun sendImpl(json: JsonObject): JsonObject

    override suspend fun send(json: JsonObject): JsonObject {
        if (!connected) {
            throw IllegalStateException("Device is not connected")
        }
        val response = sendImpl(json)

        // Handle error responses
        val oscSpace = response["osc"]
        if (oscSpace is JsonObject) {
            val oscError = oscSpace["error"]
            if (oscError is JsonPrimitive) {
                throw Exception("Error response from device: ${oscError.content}")
            }
        }
        Log.i("SSC", "Tx: $json\nRx: $response")
        return response
    }

    suspend fun getValue(path: String): JsonPrimitive {
        if (!connected) {
            throw IllegalStateException("Device is not connected")
        }
        val response = send(wrapElement(path, JsonNull))
        return unwrapElement(path, response) as JsonPrimitive
    }
}