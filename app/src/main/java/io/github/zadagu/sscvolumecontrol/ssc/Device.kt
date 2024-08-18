package io.github.zadagu.sscvolumecontrol.ssc

import android.util.Log
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

class Device(
    var ipv6Address: String,
    var hasUdpSupport: Boolean,
    var hasTcpSupport: Boolean,
    val name: String? = null,
    val product: String? = null,
    val vendor: String? = null
) {
    private val connectFn = {
        if (hasTcpSupport) {
            TcpConnection(ipv6Address)
        } else if (hasUdpSupport) {
            UdpConnection(ipv6Address)
        } else {
            throw UnsupportedOperationException("Device does not support any connection type")
        }
    }
    val connection = AutoManagingConnection(connectFn)

    override fun equals(other: Any?): Boolean {
        return this.ipv6Address == (other as Device).ipv6Address
    }

    suspend fun <R> withConnection(fn: suspend (connection: SscConnection) -> R): R {
        return connection.withConnection(fn)
    }

    suspend fun retrieveIdentity(): Device {
        Log.i("INFO", "Retrieving identity for device $ipv6Address")
        return this.withConnection() {
            val vendorResponse = it.send(wrapElement("device.identity.vendor", JsonNull))
            val vendor = (unwrapElement("device.identity.vendor", vendorResponse) as JsonPrimitive).content
            val productResponse = it.send(wrapElement("device.identity.product", JsonNull))
            val product = (unwrapElement("device.identity.product", productResponse) as JsonPrimitive).content
            val nameResponse = it.send(wrapElement("device.name", JsonNull))
            val name = (unwrapElement("device.name", nameResponse) as JsonPrimitive).content
            return@withConnection Device(
                ipv6Address,
                hasUdpSupport,
                hasTcpSupport,
                name,
                product,
                vendor
            )
        }
    }
}