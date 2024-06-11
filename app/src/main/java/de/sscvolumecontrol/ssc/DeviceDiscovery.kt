package de.sscvolumecontrol.ssc;
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo

public class DeviceDiscovery {
    private var runningJmdns = mutableMapOf<InetAddress, JmDNS>()
    private val runningJmdnsMutex = Mutex()
    private val ipv6ToDevice = mutableMapOf<String, Device>()
    private val ipv6ToDeviceMutex = Mutex()
    private var multicastLock: WifiManager.MulticastLock? = null
    private val _availableDevices = MutableLiveData(listOf<Device>())
    private val _isRunning = MutableLiveData(true)
    private val scope = CoroutineScope(Dispatchers.IO)
    val devices: LiveData<List<Device>> = _availableDevices
    val isRunning: LiveData<Boolean> = _isRunning

    fun startDiscovery(wifiManager: WifiManager, timeout: Long = 10000) {
        val multicastLock = wifiManager.createMulticastLock("multicastLock")
        this.multicastLock = multicastLock
        Log.i("INFO", "Got multicast lock")
        multicastLock.setReferenceCounted(true)
        multicastLock.acquire()

        val networkInterfaces = NetworkInterface.getNetworkInterfaces()
        for (networkInterface in networkInterfaces) {
            val address = networkInterface.inetAddresses.toList().filter { it is Inet6Address && !it.isLinkLocalAddress }.firstOrNull()
            if (address != null) {
                startDiscoveryOnInterface(address)
            }
        }

        scope.launch {
            Thread.sleep(timeout)
            stopDiscovery()
        }
    }
    private fun startDiscoveryOnInterface(address: InetAddress=Inet6Address.getByName("::0")) {
        scope.launch {
            runningJmdnsMutex.withLock {
                if (runningJmdns.containsKey(address)) {
                    return@withLock
                }
                Log.i("INFO", "Starting device discovery on address ${address.hostAddress}")
                try {
                    // Create a JmDNS instance
                    val jmdns = JmDNS.create(address)

                    // Add a service listener
                    val listener = ServiceListener(jmdns)
                    jmdns.addServiceListener("_ssc._udp.local.", listener)
                    jmdns.addServiceListener("_ssc._tcp.local.", listener)

                    runningJmdns[address] = jmdns
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun stopDiscovery() {
        if (!_isRunning.value!!) {
            return
        }
        scope.launch {
            runningJmdnsMutex.withLock {
                for ((_, jmdns) in runningJmdns) {
                    jmdns.unregisterAllServices()
                    jmdns.close()
                }
                runningJmdns.clear()
            }
            val multicastLock = multicastLock
            if (multicastLock != null && multicastLock.isHeld) {
                multicastLock.release()
            }
            _isRunning.postValue(false)
        }
    }

    inner class ServiceListener(private val jmdns: JmDNS) : javax.jmdns.ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {
            // A new service was found, let's ask JmDNS to resolve it
            jmdns.requestServiceInfo(event.type, event.name, 1)
            Log.i("INFO", "Service added: ${event.name} of type ${event.type}")
        }

        override fun serviceRemoved(event: ServiceEvent) {
            // A service was lost
        }

        override fun serviceResolved(event: ServiceEvent) {
            val info: ServiceInfo = event.info
            Log.w("ipv6", "Service resolved: ${info.inet6Addresses.toList().joinToString("; \n")}")
            findGlobalIpv6Address(info.inet6Addresses.toList())?.let { inetAddress ->
                val ipv6Address = inetAddress.hostAddress
                val hasUdpSupport = info.type.startsWith("_ssc._udp")
                val hasTcpSupport = info.type.startsWith("_ssc._tcp")
                if (ipv6Address != null) {
                    scope.launch {
                        createOrUpdateDevice(ipv6Address, hasUdpSupport, hasTcpSupport)
                    }
                } else {
                    Log.i("INFO", "No IPv6 address found for device ${info.name} and type ${info.type}")
                }
            } ?: Log.i("INFO", "No IPv6 address found for device ${info.name} and type ${info.type}")
        }

        private suspend fun createOrUpdateDevice(ipv6Address: String, hasUdpSupport: Boolean, hasTcpSupport: Boolean) {
            var device: Device
            ipv6ToDeviceMutex.withLock {
                device = ipv6ToDevice.getOrPut(ipv6Address) { Device(ipv6Address, hasUdpSupport, hasTcpSupport) }
            }

            device.also {
                it.hasUdpSupport = it.hasUdpSupport || hasUdpSupport
                it.hasTcpSupport = it.hasTcpSupport || hasTcpSupport
            }
            Log.i("INFO", "Device $ipv6Address has UDP support: $hasUdpSupport, TCP support: $hasTcpSupport")
            if (device.name == null) {
                scope.launch {
                    try {
                        val deviceWithIdentity = device.retrieveIdentity()
                        ipv6ToDeviceMutex.withLock {
                            ipv6ToDevice[ipv6Address] = deviceWithIdentity
                        }
                        _availableDevices.postValue(ipv6ToDevice.values.toList())
                    } catch (e: Exception) {
                        Log.e("ERROR", "Failed to retrieve identity for device $ipv6Address", e)
                    }
                }
            }
            val devicesList = ipv6ToDevice.values.toList()
            _availableDevices.postValue(devicesList)
        }
    }
}

fun findGlobalIpv6Address(ipv6Addresses: List<Inet6Address>): Inet6Address? {
    val removedLocal = ipv6Addresses.filter { !it.isLinkLocalAddress }
    return removedLocal.firstOrNull();
}
