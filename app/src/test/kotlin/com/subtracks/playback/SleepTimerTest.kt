package com.subtracks.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepTimerTest {
    @Test
    fun replacingTheDeadlineAndExtendingUseElapsedTime() {
        var now = 1000L
        val timer = SleepTimer { now }
        timer.minutes(5)
        assertEquals(SleepLimit.Deadline(301000L), timer.limit.value)
        timer.extend()
        assertEquals(SleepLimit.Deadline(601000L), timer.limit.value)
        now += 500L
        timer.minutes(1)
        assertEquals(SleepLimit.Deadline(61500L), timer.limit.value)
        timer.cancel()
        assertNull(timer.limit.value)
    }

    @Test
    fun countingTracksArmsTheFinalBoundaryAndThenClears() {
        val timer = SleepTimer { 0L }
        timer.minutes(10)
        timer.tracks(2)
        timer.finishedTrack()
        assertEquals(SleepLimit.Tracks(1), timer.limit.value)
        timer.finishedTrack()
        assertNull(timer.limit.value)
    }
}
