package de.sscvolumecontrol.ssc

import android.util.Log
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

class Device(var ipv6Address: String, var hasUdpSupport: Boolean, var hasTcpSupport: Boolean, val name: String? = null, val product: String? =null, val vendor: String? = null) {
    suspend fun <R>connect(fn: suspend (connection: ConnectionBase) -> R): R {
        val connection = if (hasTcpSupport) {
            TcpConnection(ipv6Address)
        } else if (hasUdpSupport) {
            UdpConnection(ipv6Address)
        } else {
            throw UnsupportedOperationException("Device does not support any connection type")
        }
        try {
            return fn(connection)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun retrieveIdentity(): Device {
        Log.i("INFO", "Retrieving identity for device $ipv6Address")
        return this.connect() {
            val vendorResponse = it.send(wrapElement("device.identity.vendor", JsonNull))
            val vendor = (unwrapElement("device.identity.vendor", vendorResponse) as JsonPrimitive).content
            val productResponse = it.send(wrapElement("device.identity.product", JsonNull))
            val product = (unwrapElement("device.identity.product", productResponse) as JsonPrimitive).content
            val nameResponse = it.send(wrapElement("device.name", JsonNull))
            val name = (unwrapElement("device.name", nameResponse) as JsonPrimitive).content
            return@connect Device(ipv6Address, hasUdpSupport, hasTcpSupport, name, product, vendor)
        }
    }
}