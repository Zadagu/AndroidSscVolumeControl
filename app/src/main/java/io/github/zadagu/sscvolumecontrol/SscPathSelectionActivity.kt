package io.github.zadagu.sscvolumecontrol

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import io.github.zadagu.sscvolumecontrol.ssc.Device
import io.github.zadagu.sscvolumecontrol.ssc.SscLimits
import io.github.zadagu.sscvolumecontrol.ssc.SscLimitsType
import io.github.zadagu.sscvolumecontrol.ssc.getOscSchemaRecursive
import io.github.zadagu.sscvolumecontrol.ssc.getLimitsForSchema
import io.github.zadagu.sscvolumecontrol.ui.composables.LimitsList
import io.github.zadagu.sscvolumecontrol.ui.theme.SscVolumeControlTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val IntentExtraSelectedSscPath = "selectedSscPath"
const val IntentExtraSscDeviceIpv6 = "sscDeviceIpv6"

class SscPathSelectionActivity : ComponentActivity() {
    private val path: MutableState<String?> = mutableStateOf(null)
    private val errorString = mutableStateOf<String?>(null)
    private val deviceSscSchemaWithLimits = mutableStateOf(emptyMap<String, SscLimits>())
    private val isLoadingSchema = mutableStateOf(true)
    private var ipv6Address: String? = null
    private var device: Device? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        path.value = intent.getStringExtra(IntentExtraSelectedSscPath)
        val ipv6Address = intent.getStringExtra(IntentExtraSscDeviceIpv6)
        this.ipv6Address = ipv6Address

        if (ipv6Address == null) {
            errorString.value = "No device IPv6 address provided"
            return
        }

        setContent {
            SscVolumeControlTheme {
                SscPathSelectionActivityContent(
                    deviceSscSchemaWithLimits,
                    isLoadingSchema,
                    errorString,
                    path,
                    this::onPathClick
                )
            }
        }

        val device = Device(ipv6Address, true, true) // todo determine actual support
        this.device = device
        loadSchema(device)
    }

    private fun loadSchema(device: Device) {
        scope.launch {
            device.connect {
                isLoadingSchema.value = true
                val schema = getOscSchemaRecursive(it)
                val schemaWithLimits = getLimitsForSchema(it, schema)
                deviceSscSchemaWithLimits.value = schemaWithLimits.filter { (_, limits) -> limits.type == SscLimitsType.Number }
                isLoadingSchema.value = false
            }
        }
    }

    private fun onPathClick(path: String) {
        this.path.value = path
        val resultIntent = Intent()
        resultIntent.putExtra(IntentExtraSelectedSscPath, path)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
        this.finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SscPathSelectionActivityContent(
    deviceSscSchemaWithLimits: MutableState<Map<String, SscLimits>>,
    isLoadingSchema: MutableState<Boolean>,
    errorString: MutableState<String?>,
    path: MutableState<String?>,
    onPathClick: (path: String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = {
                    Text("Please select the loudness property of your speakers!")
                }
            )
        },
    ) { paddingValues ->
        if (isLoadingSchema.value) {
            Column (
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(paddingValues).fillMaxSize()
            ) {
                Text("Loading schema...", modifier = Modifier.padding(bottom = 8.dp))
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (errorString.value != null) {
            Column (
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(paddingValues).fillMaxSize()
            ) {
                Text(errorString.value!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            return@Scaffold
        }

        LimitsList(deviceSscSchemaWithLimits, Modifier.padding(paddingValues), onPathClick)
    }
}
