package de.sscvolumecontrol

import android.net.wifi.WifiManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Observer
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import de.sscvolumecontrol.ssc.Device
import de.sscvolumecontrol.ssc.DeviceDiscovery
import de.sscvolumecontrol.ui.composables.DeviceList
import de.sscvolumecontrol.ui.theme.SscVolumeControlTheme


class DeviceScanActivity : ComponentActivity() {
    private var deviceDiscovery: DeviceDiscovery? = null
    private var wifiManager: WifiManager? = null
    private val devices = mutableStateOf(listOf<Device>())
    private val selectedDevices = mutableStateOf(setOf<Device>())
    private val isDiscovering = mutableStateOf(false)

    override fun onResume() {
        super.onResume()
        try {
            val wifiManager = getApplicationContext().getSystemService(WIFI_SERVICE) as WifiManager
            this.wifiManager = wifiManager
            Log.i("INFO", "Got wifi manager ${wifiManager.toString()}")

            val deviceDiscovery = DeviceDiscovery()
            this.deviceDiscovery = deviceDiscovery
            deviceDiscovery.startDiscovery(wifiManager)

            devices.value = deviceDiscovery.devices.value ?: emptyList()
            isDiscovering.value = deviceDiscovery.isRunning.value ?: false

            deviceDiscovery.devices.observe(this, Observer { newDevices ->
                // Update the UI with the new list of devices
                devices.value = newDevices
            })
            deviceDiscovery.isRunning.observe(this, Observer { isRunning ->
                isDiscovering.value = isRunning
            })

        } catch (e: Exception) {
            Toast.makeText(this, "Failed to acquire multicast lock", Toast.LENGTH_SHORT).show()
            setContent {
                Text(text = "Failed to acquire multicast lock")
            }
            e.printStackTrace()
            return
        }

        setContent {
            SscVolumeControlTheme {
                DeviceScanActivityContent(
                    devices,
                    selectedDevices,
                    isDiscovering,
                    ::onSelectionSubmitted
                )
            }
        }
    }

    override fun onPause() {
        deviceDiscovery?.stopDiscovery()
        super.onPause()
    }
    private fun onSelectionSubmitted() {
        setControlledDevices(this, deviceDiscovery?.devices?.value ?: emptyList())
        deviceDiscovery?.stopDiscovery()
        finish()
    }

}

@Composable
@OptIn(ExperimentalTvMaterial3Api::class)
fun DeviceScanActivityContent(
    devices: MutableState<List<Device>>,
    selectedDevices: MutableState<Set<Device>>,
    isDiscovering: MutableState<Boolean>,
    onSelectionSubmitted: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RectangleShape
    ) {

        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text(
                "Select the devices you want to control:",
                modifier = Modifier.padding(bottom = 16.dp)
            )
            DeviceList(devices, selectedDevices)
            if (isDiscovering.value) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            Button(
                onClick = onSelectionSubmitted,
                enabled = selectedDevices.value.isNotEmpty(),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Submit Selection")
            }
        }
    }
}

@Preview
@Composable
fun PreviewDeviceScanActivityContent() {
    val device1 = Device("::1", true, true, "Left", "Loudspeaker", "Santa Clause")
    val device2 = Device("::2", true, true, "Right", "Loudspeaker", "Santa Clause")
    val devices = remember {
        mutableStateOf(listOf(device1, device2))
    }
    val selectedDevices = remember {
        mutableStateOf(setOf<Device>())
    }
    val isDiscovering = remember {
        mutableStateOf(true)
    }

    DeviceScanActivityContent(
        devices,
        selectedDevices,
        isDiscovering,
        onSelectionSubmitted = {})
}