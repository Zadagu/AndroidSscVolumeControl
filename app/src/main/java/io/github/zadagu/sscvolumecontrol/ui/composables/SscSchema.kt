package io.github.zadagu.sscvolumecontrol.ui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.zadagu.sscvolumecontrol.ssc.SscLimits

@Composable
fun LimitItem(path: String, limits: SscLimits, onClick: (path: String) -> Unit) {
    Row (
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = Modifier.selectable(false, onClick = { onClick(path) }).fillMaxWidth()
    ) {
        Column (modifier = Modifier.padding(8.dp)) {
            Text(path, style = MaterialTheme.typography.bodyMedium)
            Text(limits.desc ?: "No description available", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun LimitsList(limits: MutableState<Map<String, SscLimits>>, modifier: Modifier, onClick: (path: String) -> Unit) {
    LazyColumn (modifier = modifier.fillMaxSize()) {
        items(limits.value.entries.toList()) { (path, limits) ->
            LimitItem(path, limits, onClick)
        }
    }
}