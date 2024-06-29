package io.github.zadagu.sscvolumecontrol

import android.content.Context
import io.github.zadagu.sscvolumecontrol.ssc.Device
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

const val PREFERENCES_NAME = "de.sscvolumecontrol"
const val DEVICES_IPV6 = "devices_ipv6"
const val DEVICES_TCP_SUPPORT = "devices_tcp_support"

@Serializable
data class VolumeServiceSettings(
    /** The minimum volume that can be set user wants to set. */
    public var userMinimumVolume: Float = 35.0f,
    /** The maximum volume that can be set user wants to set. */
    public var userMaximumVolume: Float = 95.0f,
    /** The minimum volume that can be set on the speaker. */
    public var speakerMinimumVolume: Float = 0.0f,
    /** The maximum volume that can be set on the speaker. */
    public var speakerMaximumVolume: Float = 100.0f,
    /** The path to the volume setting in the speaker. (depends on speaker. default value matches Neumann KH120-II) */
    public var sscVolumePath: String = "audio.out.level",
) {

}

fun getControlledDevices(context: Context): List<Device> {
    val sharedPref = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    val devicesIpv6 = sharedPref.getStringSet(DEVICES_IPV6, setOf()) ?: setOf()
    val devicesTcpSupport = sharedPref.getStringSet(DEVICES_TCP_SUPPORT, setOf()) ?: setOf()
    return devicesIpv6.map { Device(it, true, devicesTcpSupport.contains(it)) }
}

fun setControlledDevices(context: Context, devices: List<Device>) {
    val sharedPref = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    with(sharedPref.edit()) {
        putStringSet(DEVICES_IPV6, devices.map { it.ipv6Address }.toSet())
        putStringSet(DEVICES_TCP_SUPPORT, devices.filter { it.hasTcpSupport }.map { it.ipv6Address }.toSet())
        apply()
    }
}

fun getVolumeServiceSettings(context: Context): VolumeServiceSettings? {
    val sharedPref = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    val settingsJson = sharedPref.getString("volume_service_settings", null) ?: return null
    return Json.decodeFromString<VolumeServiceSettings>(settingsJson)
}

fun setVolumeServiceSettings(context: Context, settings: VolumeServiceSettings) {
    val sharedPref = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    with(sharedPref.edit()) {
        putString("volume_service_settings", Json.encodeToString(settings))
        apply()
    }
}