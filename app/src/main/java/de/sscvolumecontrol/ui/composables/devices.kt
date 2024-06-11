@file:OptIn(ExperimentalTvMaterial3Api::class)
package de.sscvolumecontrol.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import de.sscvolumecontrol.R
import de.sscvolumecontrol.ssc.Device

@Composable
fun CheckedIcon(
    checked: Boolean,
    icon: Painter,
    checkedIcon: ImageVector,
    contentDescription: String?
) {
    Box(modifier = Modifier.size(24.dp)) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(21.dp).align(Alignment.TopStart)
        )

        if (checked) {
            Icon(
                imageVector = checkedIcon,
                contentDescription = "Selected",
                modifier = Modifier.align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.small).size(11.dp),
            )
        }
    }
}

@Composable
fun DeviceItem(device: Device, selectedDevices: MutableState<Set<Device>>?) {
    val isSelected = selectedDevices?.value?.contains(device) ?: false

    Row(
        modifier = Modifier.clickable {
            if (selectedDevices != null) {
                if (isSelected) {
                    selectedDevices.value -= device
                } else {
                    selectedDevices.value += device
                }
            }
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckedIcon(
            checked = isSelected,
            icon = painterResource(id = R.drawable.cast_audio),
            checkedIcon = Icons.Default.Check,
            contentDescription = "Device Icon"
        )
        Spacer(modifier = Modifier.width(8.dp))

        Column {
            if (device.name != null) {
                Text(text = "${device.product} ${device.name}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "${device.vendor}", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(text = "Device: <waiting for name>")
            }
        }
    }
}

@Composable
fun DeviceList(devices: MutableState<List<Device>>, selectedDevices: MutableState<Set<Device>>?) {
    Column {
        for (device in devices.value) {
            DeviceItem(device = device, selectedDevices = selectedDevices)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Preview
@Composable
fun PreviewDeviceItem() {
    val device = Device("::1", true, true, "Left", "Loudspeaker", "Santa Clause")
    val selectedDevices = remember { mutableStateOf(setOf(device)) }
    DeviceItem(device, selectedDevices)
}

@Preview
@Composable
fun PreviewDeviceList() {
    val device1 = Device("::1", true, true, "Left", "Loudspeaker", "Santa Clause")
    val device2 = Device("::2", true, true, "Right", "Loudspeaker", "Santa Clause")
    val device3 = Device("::3", true, true)
    val devices = setOf(
        device1,
        device2,
        device3
    )
    val selectedDevices = remember { mutableStateOf(devices) }
    val devicesState = remember { mutableStateOf(devices.toList()) }
    DeviceList(devicesState, selectedDevices)
}