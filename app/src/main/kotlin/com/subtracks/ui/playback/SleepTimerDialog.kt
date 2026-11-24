package com.subtracks.ui.playback

import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subtracks.R
import com.subtracks.playback.SleepLimit
import com.subtracks.playback.SleepTimer
import kotlinx.coroutines.delay

@Composable
internal fun SleepTimerDialog(
    timer: SleepTimer,
    queueLabel: String,
    onDismiss: () -> Unit,
) {
    val active by timer.limit.collectAsStateWithLifecycle()
    var custom by remember { mutableStateOf("") }
    val amount = custom.toIntOrNull() ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sleep_timer)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when (val limit = active) {
                    is SleepLimit.Deadline -> {
                        var now by remember(limit) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
                        LaunchedEffect(limit) {
                            while (now < limit.elapsedMs) {
                                delay(1000L)
                                now = SystemClock.elapsedRealtime()
                            }
                        }
                        val seconds = ((limit.elapsedMs - now + 999L) / 1000L).coerceAtLeast(0L)
                        Text(stringResource(R.string.sleep_countdown, seconds / 60, seconds % 60))
                        TextButton(onClick = timer::extend) { Text(stringResource(R.string.sleep_extend)) }
                    }

                    is SleepLimit.Tracks -> {
                        Text(stringResource(R.string.sleep_tracks_left, limit.remaining))
                    }

                    SleepLimit.QueueEnd -> {
                        Text(queueLabel)
                    }

                    null -> {
                        Unit
                    }
                }
                listOf(5, 10, 15, 30, 60, 120, 240, 360).forEach { minutes ->
                    val label =
                        if (minutes < 60) {
                            stringResource(R.string.sleep_minutes, minutes)
                        } else {
                            pluralStringResource(R.plurals.sleep_hours, minutes / 60, minutes / 60)
                        }
                    SleepOption(label) {
                        timer.minutes(minutes)
                        onDismiss()
                    }
                }
                SleepOption(stringResource(R.string.sleep_end_song)) {
                    timer.tracks(1)
                    onDismiss()
                }
                SleepOption(queueLabel) {
                    timer.atQueueEnd()
                    onDismiss()
                }
                OutlinedTextField(
                    value = custom,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) custom = it },
                    label = { Text(stringResource(R.string.sleep_custom)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Row {
                    TextButton(
                        enabled = amount in 1..1440,
                        onClick = {
                            timer.minutes(amount)
                            onDismiss()
                        },
                    ) { Text(stringResource(R.string.sleep_custom_minutes)) }
                    TextButton(
                        enabled = amount in 1..999,
                        onClick = {
                            timer.tracks(amount)
                            onDismiss()
                        },
                    ) { Text(stringResource(R.string.sleep_custom_tracks)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.info_close)) } },
        dismissButton = {
            if (active != null) {
                TextButton(
                    onClick = {
                        timer.cancel()
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.sleep_cancel), color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}

@Composable
private fun SleepOption(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.fillMaxWidth())
    }
}
