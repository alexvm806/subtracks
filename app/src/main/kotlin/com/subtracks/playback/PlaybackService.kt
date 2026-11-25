package com.subtracks.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import coil3.SingletonImageLoader
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.subtracks.MainActivity
import com.subtracks.R
import com.subtracks.UserAgent
import com.subtracks.data.net.NetworkMode
import com.subtracks.data.net.networkMode
import com.subtracks.data.prefs.UserPreferences
import com.subtracks.data.repo.LibraryRepository
import com.subtracks.data.repo.SourceRepository
import com.subtracks.data.source.StarType
import com.subtracks.log.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koin.core.context.GlobalContext
import java.io.File

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var artworkLoader: CoverArtBitmapLoader? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var sourceRepository: SourceRepository? = null
    private var library: LibraryRepository? = null
    private var currentSongId: String? = null
    private val sleepTimer by lazy { GlobalContext.get().get<SleepTimer>() }
    private var starred = false
    private var starJob: Job? = null

    private val gainProcessor: ReplayGainProcessor = ReplayGainProcessor { replayGain.applyItem(it) }
    private val replayGain: ReplayGainController = ReplayGainController { gainProcessor.setLinearGain(it) }

    private val starCallback =
        object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
            ): MediaSession.ConnectionResult {
                val base = MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller).build()
                return MediaSession.ConnectionResult
                    .AcceptedResultBuilder(session, controller)
                    .setAvailableSessionCommands(
                        base.availableSessionCommands
                            .buildUpon()
                            .add(STAR_COMMAND)
                            .build(),
                    ).build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle,
            ): ListenableFuture<SessionResult> {
                if (customCommand.customAction != STAR_COMMAND.customAction) {
                    return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                }
                toggleStar()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

    @OptIn(UnstableApi::class, ExperimentalApi::class)
    override fun onCreate() {
        super.onCreate()
        val sources = GlobalContext.get().get<SourceRepository>()
        sourceRepository = sources
        library = GlobalContext.get().get<LibraryRepository>()
        val preferences = GlobalContext.get().get<UserPreferences>()
        val cache = StreamCache.open(this)
        scope.launch {
            preferences
                .replayGainMode()
                .combine(preferences.replayGainPreampDb()) { mode, preamp -> ReplayGainSettings(mode, preamp) }
                .collect(replayGain::setSettings)
        }
        val exoPlayer =
            ExoPlayer
                .Builder(this)
                .setRenderersFactory(ReplayGainRenderersFactory(this, gainProcessor))
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true,
                ).setHandleAudioBecomingNoisy(true)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(this)
                        .setEnableClippingInMediaPeriod(true)
                        .setDataSourceFactory(
                            mediaDataSourceFactory(
                                this,
                                cache,
                                OkHttpDataSource.Factory(streamingClient()).setUserAgent(UserAgent.value),
                            ),
                        ),
                ).build()
        scope.launch {
            sleepTimer.limit.collectLatest { limit ->
                exoPlayer.pauseAtEndOfMediaItems = limit is SleepLimit.Tracks && limit.remaining == 1
                if (limit is SleepLimit.Deadline) {
                    delay((limit.elapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
                    if (sleepTimer.limit.value == limit) {
                        exoPlayer.pause()
                        sleepTimer.cancel()
                    }
                }
            }
        }
        scope.launch {
            combine(preferences.preloadNextTrack(), networkMode(applicationContext), sources.offline) { enabled, mode, offline ->
                enabled && mode == NetworkMode.Wifi && !offline
            }.distinctUntilChanged().collect { preload ->
                exoPlayer.preloadConfiguration =
                    if (preload) {
                        ExoPlayer.PreloadConfiguration(3_000_000L)
                    } else {
                        ExoPlayer.PreloadConfiguration.DEFAULT
                    }
            }
        }
        scope.launch {
            preferences.fadeSeconds().distinctUntilChanged().collectLatest { seconds ->
                if (seconds > 0) {
                    val fades = TrackFades(exoPlayer, scope, seconds)
                    fades.start()
                    try {
                        awaitCancellation()
                    } finally {
                        fades.stop()
                    }
                }
            }
        }
        exoPlayer.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                        sleepTimer.finishedTrack()
                    }
                    observeStar(mediaItem?.mediaId)
                }

                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int,
                ) {
                    if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                        sleepTimer.cancel()
                    }
                }
            },
        )
        val player = KnownDurationPlayer(exoPlayer)
        val loader =
            CoverArtBitmapLoader(
                applicationContext,
                sources,
                SingletonImageLoader.get(this),
            )
        artworkLoader = loader
        session =
            MediaSession
                .Builder(this, player)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java)
                            .setAction(MainActivity.ACTION_OPEN_NOW_PLAYING),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                ).setBitmapLoader(loader)
                .setCallback(starCallback)
                .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    @OptIn(UnstableApi::class)
    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseAllPlayersAndStopSelf()
    }

    private fun observeStar(songId: String?) {
        starJob?.cancel()
        currentSongId = songId
        starred = false
        val library = library ?: return
        if (songId == null) {
            session?.setMediaButtonPreferences(emptyList())
            return
        }
        session?.setMediaButtonPreferences(listOf(starButton(false)))
        starJob =
            scope.launch {
                val sourceId = sourceRepository?.activeSourceIdOnce() ?: return@launch
                library.song(sourceId, songId).collect { song ->
                    if (currentSongId != songId) return@collect
                    starred = song?.starred != null
                    session?.setMediaButtonPreferences(listOf(starButton(starred)))
                }
            }
    }

    private fun toggleStar() {
        val songId = currentSongId ?: return
        val library = library ?: return
        library.toggleStar(StarType.Song, songId)
    }

    private fun starButton(isStarred: Boolean): CommandButton =
        CommandButton
            .Builder(if (isStarred) CommandButton.ICON_STAR_FILLED else CommandButton.ICON_STAR_UNFILLED)
            .setSessionCommand(STAR_COMMAND)
            .setDisplayName(getString(if (isStarred) R.string.actions_unstar else R.string.actions_star))
            .build()

    private fun streamingClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(Log.interceptor())
            .addInterceptor { chain ->
                val started = SystemClock.elapsedRealtime()
                val response = chain.proceed(chain.request())
                Log.d(
                    "stream",
                    "range=${chain.request().header("Range")} code=${response.code} " +
                        "contentRange=${response.header("Content-Range")} " +
                        "length=${response.body.contentLength()} in ${SystemClock.elapsedRealtime() - started}ms",
                )
                response
            }.build()

    override fun onDestroy() {
        scope.cancel()
        sleepTimer.cancel()
        artworkLoader?.shutdown()
        artworkLoader = null
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private companion object {
        val STAR_COMMAND = SessionCommand("com.subtracks.STAR", Bundle.EMPTY)
    }
}

// Process-scoped so it is never released while a loader thread is still committing to it, and so
// a recreated service does not try to lock the directory a second time.
@OptIn(UnstableApi::class)
private object StreamCache {
    @Volatile
    private var instance: SimpleCache? = null

    fun open(context: Context): SimpleCache? =
        instance ?: synchronized(this) {
            instance ?: runCatching {
                SimpleCache(
                    File(context.cacheDir, STREAM_CACHE_DIR),
                    LeastRecentlyUsedCacheEvictor(STREAM_CACHE_BYTES),
                    StandaloneDatabaseProvider(context),
                )
            }.onFailure { Log.w("playback", "stream cache unavailable: ${it.message}") }
                .getOrNull()
                ?.also { instance = it }
        }

    private const val STREAM_CACHE_DIR = "stream-cache"
    private const val STREAM_CACHE_BYTES = 64L * 1024 * 1024
}

@OptIn(UnstableApi::class)
internal fun mediaDataSourceFactory(
    context: Context,
    cache: Cache?,
    upstream: DataSource.Factory,
): DataSource.Factory {
    val known = KnownLengthDataSourceFactory(upstream)
    val source: DataSource.Factory =
        if (cache == null) {
            known
        } else {
            CacheDataSource
                .Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(known)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }
    return DefaultDataSource.Factory(context, source)
}
