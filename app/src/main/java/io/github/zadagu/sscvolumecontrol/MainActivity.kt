package io.github.zadagu.sscvolumecontrol

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.zadagu.sscvolumecontrol.ssc.Device
import io.github.zadagu.sscvolumecontrol.ssc.limits
import io.github.zadagu.sscvolumecontrol.ui.composables.DeviceList
import io.github.zadagu.sscvolumecontrol.ui.theme.SscVolumeControlTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

const val SscPathSelectionRequestCode = 2424

class MainActivity : ComponentActivity() {
    private val devices = mutableStateOf(listOf<Device>())
    private val userMinimumVolume = mutableStateOf(0.0f)
    private val userMaximumVolume = mutableStateOf(100.0f)
    private val speakerMinimumVolume = mutableStateOf(0.0f)
    private val speakerMaximumVolume = mutableStateOf(100.0f)
    private val sscVolumePath = mutableStateOf("audio.out.level")
    private val isSscVolumePathValid = mutableStateOf(false)
    private val scope = CoroutineScope(Dispatchers.IO)
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = this.getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        retrieveVolumeServiceConfig()

        setContent {
            SscVolumeControlTheme {
                MainScreen(
                    devices,
                    userMinimumVolume,
                    userMaximumVolume,
                    speakerMinimumVolume,
                    speakerMaximumVolume,
                    sscVolumePath,
                    isSscVolumePathValid,
                    onDeviceSearchClick = this@MainActivity::onDeviceSearchClick,
                    onSetSscVolumePathClick = this@MainActivity::onSetSscVolumePathClick,
                    onApplySettingsClick = this@MainActivity::onApplySettingsClick,
                    )
            }
        }

        preferences.registerOnSharedPreferenceChangeListener { _, _ ->
            updateDevices()
            retrieveVolumeServiceConfig()
            scope.launch {
                retrieveSpeakerLimits()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, VolumeService::class.java))
            Toast.makeText(this, "Started volume service in foreground", Toast.LENGTH_SHORT).show()
        } else {
            startService(Intent(this, VolumeService::class.java))
            Toast.makeText(this, "Started volume service", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateDevices() {
        Log.i("MainActivity", "Updating devices")
        val updatedDevices = getControlledDevices(this)
        val newIpv6Addresses = updatedDevices.map { it.ipv6Address }
        val newDevicesState = devices.value.toMutableList()
        newDevicesState.removeAll { !newIpv6Addresses.contains(it.ipv6Address) }
        val newDevices = updatedDevices.filter { !newDevicesState.contains(it) }
        newDevicesState.addAll(newDevices)
        devices.value = newDevicesState
        scope.launch {
            retrieveSpeakerLimits()
            val newDevicesWithName = newDevicesState.map { it.retrieveIdentity() }
            devices.value = newDevicesWithName
        }
    }

    private fun retrieveVolumeServiceConfig() {
        val settings = getVolumeServiceSettings(this)
        if (settings != null) {
            userMinimumVolume.value = settings.userMinimumVolume
            userMaximumVolume.value = settings.userMaximumVolume
            speakerMinimumVolume.value = settings.speakerMinimumVolume
            speakerMaximumVolume.value = settings.speakerMaximumVolume
            sscVolumePath.value = settings.sscVolumePath
        }
    }

    private fun updateVolumeServiceConfig() {
        val settings = VolumeServiceSettings(
            userMinimumVolume.value,
            userMaximumVolume.value,
            speakerMinimumVolume.value,
            speakerMaximumVolume.value,
            sscVolumePath.value
        )
        setVolumeServiceSettings(this, settings)
    }

    private suspend fun retrieveSpeakerLimits() {
        val devices = devices.value
        if (devices.isEmpty()) {
            return
        }
        val device = devices[0]
        device.withConnection() {
            try {
                val limits = limits(it, sscVolumePath.value)
                speakerMinimumVolume.value = limits.min ?: 0.0f
                speakerMaximumVolume.value = limits.max ?: 100.0f
                userMinimumVolume.value = max(min(userMinimumVolume.value, speakerMaximumVolume.value), speakerMinimumVolume.value)
                userMaximumVolume.value = max(min(userMaximumVolume.value, speakerMaximumVolume.value), speakerMinimumVolume.value)
                isSscVolumePathValid.value = true
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to retrieve speaker limits", e)
                isSscVolumePathValid.value = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateDevices()
        scope.launch {
            retrieveSpeakerLimits()
        }
    }

    private fun onDeviceSearchClick() {
        startActivity(Intent(this, DeviceScanActivity::class.java))
    }

    private fun onSetSscVolumePathClick() {
        val intent = Intent(this, SscPathSelectionActivity::class.java)
        val device = devices.value.firstOrNull() ?: return
        intent.putExtra(IntentExtraSelectedSscPath, sscVolumePath.value)
        intent.putExtra(IntentExtraSscDeviceIpv6, device.ipv6Address)
        startActivityForResult(intent, SscPathSelectionRequestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SscPathSelectionRequestCode && resultCode == Activity.RESULT_OK) {
            val selectedPath = data?.getStringExtra(IntentExtraSelectedSscPath)
            if (selectedPath != null) {
                sscVolumePath.value = selectedPath
                updateVolumeServiceConfig()
                scope.launch {
                    retrieveSpeakerLimits()
                    userMinimumVolume.value = speakerMinimumVolume.value
                    userMaximumVolume.value = speakerMaximumVolume.value
                }
            }
        }
    }

    private fun onApplySettingsClick() {
        updateVolumeServiceConfig()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    devices: MutableState<List<Device>>,
    userMinimumVolume: MutableState<Float>,
    userMaximumVolume: MutableState<Float>,
    speakerMinimumVolume: MutableState<Float>,
    speakerMaximumVolume: MutableState<Float>,
    sscVolumePath: MutableState<String>,
    isSscVolumePathValid: MutableState<Boolean>,
    onDeviceSearchClick: () -> Unit,
    onSetSscVolumePathClick: () -> Unit,
    onApplySettingsClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RectangleShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text(
                text = "Controlled Devices:",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            DeviceList(devices, null)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                deviceSearchButton(
                    devices,
                    modifier = Modifier
                        .padding(bottom = 16.dp),
                    onDeviceSearchClick = onDeviceSearchClick
                )
            }

            Row (
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                Text("Volume path:")
                Spacer(modifier = Modifier.width(8.dp))
                InputChip(
                    onClick = onSetSscVolumePathClick,
                    label = {
                        Text(
                            sscVolumePath.value,
                            color = if (isSscVolumePathValid.value) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                        )
                    },
                    selected = true
                )
            }
            Text("If the volume control is too coarse you can reduce the range here.")
            Row (verticalAlignment = Alignment.CenterVertically) {
                Text("Minimum volume: ${userMinimumVolume.value.toInt()}")
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = userMinimumVolume.value,
                    onValueChange = {userMinimumVolume.value = it},
                    valueRange = speakerMinimumVolume.value.. speakerMaximumVolume.value,
                    modifier = Modifier.onKeyEvent {
                        if (it.key == Key.DirectionLeft && it.type == KeyEventType.KeyDown) {
                            userMinimumVolume.value = max(userMinimumVolume.value - 1, speakerMinimumVolume.value)
                            true
                        } else if (it.key == Key.DirectionRight && it.type == KeyEventType.KeyDown) {
                            userMinimumVolume.value = min(userMinimumVolume.value + 1, speakerMaximumVolume.value)
                            true
                        } else {
                            false
                        }
                    }
                )
            }
            Row (verticalAlignment = Alignment.CenterVertically) {
                Text("Maximum volume: ${userMaximumVolume.value.toInt()}")
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = userMaximumVolume.value,
                    onValueChange = {userMaximumVolume.value = it},
                    valueRange = speakerMinimumVolume.value.. speakerMaximumVolume.value,
                    modifier = Modifier.onKeyEvent {
                        if (it.key == Key.DirectionLeft && it.type == KeyEventType.KeyDown) {
                            userMaximumVolume.value = max(userMaximumVolume.value - 1, speakerMinimumVolume.value)
                            true
                        } else if (it.key == Key.DirectionRight && it.type == KeyEventType.KeyDown) {
                            userMaximumVolume.value = min(userMaximumVolume.value + 1, speakerMaximumVolume.value)
                            true
                        } else {
                            false
                        }
                    }
                )
            }
            OutlinedButton(
                onClick = onApplySettingsClick,
                modifier = Modifier.align(Alignment.End)) {
                Text("Apply changes")
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun deviceSearchButton(devices: MutableState<List<Device>>, modifier: Modifier, onDeviceSearchClick: () -> Unit) {
    @Composable
    fun buttonContent() {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search for devices",
            modifier = Modifier.padding(end = 4.dp),
        )
        Text("Search for devices")
    }

    if (devices.value.isEmpty()) {
        Button(
            onClick = onDeviceSearchClick,
            enabled = true,
            modifier = modifier
        ) {
            buttonContent()
        }
    } else {
        // If the user already configured devices, this button should draw less attention.
        OutlinedButton(
            onClick = onDeviceSearchClick,
            enabled = true,
            modifier = modifier
        ) {
            buttonContent()
        }
    }
}

//@Preview(showBackground = true)
//@Composable
//fun MainScreenPreview() {
//    val device1 = Device("::1", true, true, "Left", "Loudspeaker", "Santa Clause")
//    val device2 = Device("::2", true, true, "Right", "Loudspeaker", "Santa Clause")
//    val devices = remember { mutableStateOf(listOf(device1, device2)) }
//    MainScreen(devices) { }
//}
