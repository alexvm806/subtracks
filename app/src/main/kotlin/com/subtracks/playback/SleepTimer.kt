package com.subtracks.playback

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface SleepLimit {
    data class Deadline(
        val elapsedMs: Long,
    ) : SleepLimit

    data class Tracks(
        val remaining: Int,
    ) : SleepLimit
}

class SleepTimer(
    private val elapsedMs: () -> Long = SystemClock::elapsedRealtime,
) {
    private val active = MutableStateFlow<SleepLimit?>(null)
    val limit: StateFlow<SleepLimit?> = active

    fun minutes(minutes: Int) {
        require(minutes in 1..1440)
        active.value = SleepLimit.Deadline(elapsedMs() + minutes * 60_000L)
    }

    fun tracks(count: Int) {
        require(count in 1..999)
        active.value = SleepLimit.Tracks(count)
    }

    fun extend() {
        val previous = active.value as? SleepLimit.Deadline ?: return
        active.value = previous.copy(elapsedMs = previous.elapsedMs + 5 * 60_000L)
    }

    fun finishedTrack() {
        val previous = active.value as? SleepLimit.Tracks ?: return
        active.value = if (previous.remaining > 1) SleepLimit.Tracks(previous.remaining - 1) else null
    }

    fun cancel() {
        active.value = null
    }
}
