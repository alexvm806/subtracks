package com.subtracks.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subtracks.R
import com.subtracks.data.repo.DeviceMusic
import com.subtracks.data.repo.SourceRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
internal fun DeviceMusicMenu(firstRun: Boolean = false) {
    val importer = koinInject<DeviceMusic>()
    val sources = koinInject<SourceRepository>()
    val busy by importer.busy.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), importer::importFiles)
    val importLabel = stringResource(if (busy) R.string.device_importing else R.string.device_import)
    if (firstRun) {
        TextButton(onClick = { picker.launch(arrayOf("audio/*")) }, enabled = !busy) { Text(importLabel) }
        return
    }
    val available by remember(sources) { sources.sources() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val selected = available.firstOrNull { it.isActive }
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(selected?.name ?: stringResource(R.string.device_sources))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            available.forEach { source ->
                DropdownMenuItem(
                    text = { Text(source.name) },
                    onClick = {
                        expanded = false
                        if (!source.isActive) scope.launch { sources.selectSource(source.id) }
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(importLabel) },
                enabled = !busy,
                onClick = {
                    expanded = false
                    picker.launch(arrayOf("audio/*"))
                },
            )
        }
    }
}
