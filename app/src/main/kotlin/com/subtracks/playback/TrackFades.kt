package com.subtracks.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Sequential fades around automatic transitions, using the active session player. */
internal class TrackFades(
    private val player: Player,
    private val scope: CoroutineScope,
    private val seconds: Int,
) : Player.Listener {
    private var ramp: Job? = null
    private var fadingOut = false
    private var fadingIn = false
    private val baseVolume = player.volume

    fun start() {
        player.addListener(this)
        schedule()
    }

    fun stop() {
        player.removeListener(this)
        ramp?.cancel()
        player.volume = baseVolume
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        fadingIn = fadingOut && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        fadingOut = false
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
            fadingIn = false
            fadingOut = false
        }
    }

    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        val changed =
            events.containsAny(
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_REPEAT_MODE_CHANGED,
                Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
            )
        if (changed) schedule()
    }

    private fun schedule() {
        ramp?.cancel()
        if (!player.isPlaying || player.duration <= 0) {
            player.volume = baseVolume
            return
        }
        val duration = player.duration
        val window = minOf(seconds * 1000L, duration / 2)
        val next = player.nextMediaItemIndex
        val currentAlbum = player.currentMediaItem?.mediaMetadata?.albumTitle?.toString()
        val fadeTail =
            next >= 0 && next != player.currentMediaItemIndex &&
                (currentAlbum.isNullOrBlank() || currentAlbum != player.getMediaItemAt(next).mediaMetadata.albumTitle?.toString())
        ramp =
            scope.launch {
                while (window > 0 && player.isPlaying) {
                    val position = player.currentPosition
                    val left = (duration - position).coerceAtLeast(0L)
                    fadingIn = fadingIn && position < window
                    fadingOut = fadeTail && left <= window
                    val gain =
                        when {
                            fadingIn -> position.toFloat() / window
                            fadingOut -> left.toFloat() / window
                            else -> 1f
                        }
                    player.volume = baseVolume * gain.coerceIn(0f, 1f)
                    if (left == 0L || (!fadingIn && !fadeTail)) break
                    val untilUpdate = if (fadingIn || fadingOut) 40L else left - window
                    delay((untilUpdate / player.playbackParameters.speed).toLong().coerceAtLeast(1L))
                }
            }
    }
}
