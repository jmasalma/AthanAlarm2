package com.example.cast

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.example.model.PrayerType
import com.example.util.AppLogger
import com.example.util.LogCategory
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadOptions
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CastMediaManager {

    private const val TAG = "CastMediaManager"
    private val localHttpServer = LocalHttpAudioServer()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isCastingFlow = MutableStateFlow(false)
    val isCastingFlow: StateFlow<Boolean> = _isCastingFlow.asStateFlow()

    @Volatile
    private var isSessionConnected = false

    private var pendingMediaInfo: MediaInfo? = null
    private var activeSessionListener: SessionManagerListener<CastSession>? = null
    private var activeDiscoveryCallback: MediaRouter.Callback? = null
    private var connectionTimeoutRunnable: Runnable? = null
    private var remoteClientCallback: RemoteMediaClient.Callback? = null

    fun getCastSelector(): MediaRouteSelector {
        return MediaRouteSelector.Builder()
            .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
            .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
            .addControlCategory(
                CastMediaControlIntent.categoryForCast(
                    CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
                )
            )
            .build()
    }

    /**
     * Ensures background route discovery is active so routes don't disappear when dialogs close.
     */
    fun startDiscovery(context: Context) {
        mainHandler.post {
            try {
                val mediaRouter = MediaRouter.getInstance(context)
                val selector = getCastSelector()
                if (activeDiscoveryCallback == null) {
                    val cb = object : MediaRouter.Callback() {
                        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
                            AppLogger.i(LogCategory.CAST, TAG, "Route discovered: ${route.name} (ID: ${route.id})")
                        }
                    }
                    activeDiscoveryCallback = cb
                    mediaRouter.addCallback(selector, cb, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
                    AppLogger.i(LogCategory.CAST, TAG, "Background route discovery started")
                }
            } catch (e: Exception) {
                AppLogger.w(LogCategory.CAST, TAG, "startDiscovery failed: ${e.message}")
            }
        }
    }

    /**
     * Connects to a specific route by ID immediately.
     */
    fun selectRouteById(context: Context, routeId: String) {
        mainHandler.post {
            try {
                val mediaRouter = MediaRouter.getInstance(context)
                val target = mediaRouter.routes.firstOrNull { it.id == routeId }
                if (target != null) {
                    AppLogger.i(LogCategory.CAST, TAG, "Selecting Cast route: ${target.name} (${target.id})")
                    if (mediaRouter.selectedRoute.id == target.id) {
                        mediaRouter.unselect(MediaRouter.UNSELECT_REASON_STOPPED)
                    }
                    mediaRouter.selectRoute(target)
                } else {
                    AppLogger.w(LogCategory.CAST, TAG, "Target route $routeId not currently in MediaRouter routes")
                }
            } catch (e: Exception) {
                AppLogger.e(LogCategory.CAST, TAG, "Error selecting route $routeId: ${e.message}", e)
            }
        }
    }

    /**
     * Initiates audio streaming to the selected Cast device(s).
     */
    fun playOnCast(
        context: Context,
        soundChoice: String,
        prayerType: PrayerType = PrayerType.DHUHR,
        targetDeviceId: String? = null,
        targetDeviceName: String? = null,
        onStarted: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        _isCastingFlow.value = true
        isSessionConnected = false
        AppLogger.i(LogCategory.CAST, TAG, "playOnCast requested: sound=$soundChoice, prayer=$prayerType, targetId=$targetDeviceId, targetName=$targetDeviceName")
        val resolved = AudioStreamResolver.resolveAudioData(context, soundChoice, prayerType)
        val streamUrl = localHttpServer.startWithBytes(resolved.bytes, resolved.mimeType, context)
        AppLogger.i(LogCategory.CAST, TAG, "Local HTTP audio stream URL ready: $streamUrl (${resolved.bytes.size} bytes)")

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, resolved.title)
            putString(MediaMetadata.KEY_ARTIST, "Athan Alarm")
            putString(MediaMetadata.KEY_SUBTITLE, "Notification")
        }

        val mediaInfo = MediaInfo.Builder(streamUrl)
            .setContentType(resolved.mimeType)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setMetadata(metadata)
            .build()

        mainHandler.post {
            try {
                val castContext = try {
                    CastContext.getSharedInstance(context)
                } catch (e: Exception) {
                    AppLogger.e(LogCategory.CAST, TAG, "CastContext unavailable: ${e.message}", e)
                    null
                }

                // Parse target IDs / names (supports comma-delimited multi-selection)
                val targetIds = targetDeviceId?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                val targetNames = targetDeviceName?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

                val currentSession = castContext?.sessionManager?.currentCastSession

                // 1. If session is already connected, load media directly
                if (currentSession != null && currentSession.isConnected) {
                    AppLogger.i(LogCategory.CAST, TAG, "Existing CastSession is connected. Loading media directly...")
                    try {
                        if (currentSession.volume < 0.15) {
                            currentSession.volume = 0.5
                            AppLogger.i(LogCategory.CAST, TAG, "Adjusted Cast volume from <15% to 50%")
                        }
                    } catch (_: Exception) {}
                    loadMediaOnSession(currentSession, mediaInfo, onStarted, onError)
                    return@post
                }

                val mediaRouter = MediaRouter.getInstance(context)
                val selector = getCastSelector()

                // Keep active scan running so routes are resolved
                startDiscovery(context)

                val isRouteTargetMatch: (MediaRouter.RouteInfo) -> Boolean = { route ->
                    if (targetIds.isNotEmpty() || targetNames.isNotEmpty()) {
                        targetIds.contains(route.id) || targetNames.any { name ->
                            route.name.equals(name, ignoreCase = true) || route.name.contains(name, ignoreCase = true)
                        }
                    } else {
                        !route.isDefault && route.matchesSelector(selector)
                    }
                }

                // 2. Find target route
                val findTargetRoute: () -> MediaRouter.RouteInfo? = {
                    mediaRouter.routes.firstOrNull { isRouteTargetMatch(it) }
                        ?: mediaRouter.routes.firstOrNull { !it.isDefault && it.matchesSelector(selector) }
                }

                val targetRoute = findTargetRoute()

                if (targetRoute != null && castContext != null) {
                    AppLogger.i(LogCategory.CAST, TAG, "Found target route: ${targetRoute.name} (${targetRoute.id}). Initiating session...")
                    pendingMediaInfo = mediaInfo
                    attachSessionListener(castContext, onStarted, onError)

                    if (mediaRouter.selectedRoute.id == targetRoute.id) {
                        AppLogger.i(LogCategory.CAST, TAG, "Route already selected in MediaRouter; unselecting to force reconnection")
                        mediaRouter.unselect(MediaRouter.UNSELECT_REASON_STOPPED)
                        mainHandler.postDelayed({
                            mediaRouter.selectRoute(targetRoute)
                        }, 100L)
                    } else {
                        mediaRouter.selectRoute(targetRoute)
                    }
                    startTimeoutWatchdog(castContext, targetRoute.name, onError)
                } else if (castContext != null) {
                    AppLogger.i(LogCategory.CAST, TAG, "Target route not immediately in cache. Starting active scan...")
                    pendingMediaInfo = mediaInfo
                    attachSessionListener(castContext, onStarted, onError)

                    var scanListener: MediaRouter.Callback? = null
                    scanListener = object : MediaRouter.Callback() {
                        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
                            checkAndSelect(route)
                        }
                        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
                            checkAndSelect(route)
                        }

                        private fun checkAndSelect(route: MediaRouter.RouteInfo) {
                            if (!route.isDefault && isRouteTargetMatch(route)) {
                                AppLogger.i(LogCategory.CAST, TAG, "Discovered matching route during active scan: ${route.name}")
                                scanListener?.let { mediaRouter.removeCallback(it) }
                                if (mediaRouter.selectedRoute.id == route.id) {
                                    mediaRouter.unselect(MediaRouter.UNSELECT_REASON_STOPPED)
                                    mainHandler.postDelayed({
                                        mediaRouter.selectRoute(route)
                                    }, 100L)
                                } else {
                                    mediaRouter.selectRoute(route)
                                }
                                startTimeoutWatchdog(castContext, route.name, onError)
                            }
                        }
                    }

                    mediaRouter.addCallback(selector, scanListener, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
                    mainHandler.postDelayed({
                        scanListener.let { mediaRouter.removeCallback(it) }
                    }, 10000L)

                    startTimeoutWatchdog(castContext, targetDeviceName ?: "Google Cast speaker", onError)
                } else {
                    AppLogger.e(LogCategory.CAST, TAG, "CastContext is null; Google Play Services Cast unavailable")
                    _isCastingFlow.value = false
                    onError?.invoke("Google Cast service is not available on this device.")
                }
            } catch (e: Exception) {
                AppLogger.e(LogCategory.CAST, TAG, "Exception in playOnCast: ${e.message}", e)
                _isCastingFlow.value = false
                onError?.invoke(e.message ?: "Failed to initiate casting")
            }
        }
    }

    private fun startTimeoutWatchdog(castContext: CastContext, deviceName: String, onError: ((String) -> Unit)?) {
        cancelTimeoutWatchdog()
        val watchdog = Runnable {
            if (!isSessionConnected) {
                AppLogger.w(LogCategory.CAST, TAG, "Cast session connection timed out for: $deviceName")
                detachSessionListener(castContext)
                pendingMediaInfo = null
                _isCastingFlow.value = false
                onError?.invoke("Could not connect to $deviceName. Please check that it is powered on and on the same Wi-Fi.")
            }
        }
        connectionTimeoutRunnable = watchdog
        mainHandler.postDelayed(watchdog, 12000L)
    }

    private fun cancelTimeoutWatchdog() {
        connectionTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        connectionTimeoutRunnable = null
    }

    private fun loadMediaOnSession(
        session: CastSession,
        mediaInfo: MediaInfo,
        onStarted: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        cancelTimeoutWatchdog()
        val remoteClient = session.remoteMediaClient
        if (remoteClient == null) {
            AppLogger.e(LogCategory.CAST, TAG, "Remote media client is null on connected CastSession")
            onError?.invoke("Remote media client is unavailable on the connected speaker")
            return
        }

        // Attach completion callback to monitor when cast audio ends
        remoteClientCallback?.let { remoteClient.unregisterCallback(it) }
        val callback = object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() {
                val status = remoteClient.mediaStatus
                if (status != null) {
                    val playerState = status.playerState
                    if (playerState == MediaStatus.PLAYER_STATE_IDLE && status.idleReason != MediaStatus.IDLE_REASON_NONE) {
                        AppLogger.i(LogCategory.CAST, TAG, "Remote media client completed/idle (reason=${status.idleReason}). Setting isCasting=false")
                        _isCastingFlow.value = false
                    }
                }
            }
        }
        remoteClientCallback = callback
        remoteClient.registerCallback(callback)

        val options = MediaLoadOptions.Builder()
            .setAutoplay(true)
            .setPlayPosition(0)
            .build()

        AppLogger.i(LogCategory.CAST, TAG, "Loading MediaInfo into RemoteMediaClient...")
        val pendingResult = remoteClient.load(mediaInfo, options)
        pendingResult.setResultCallback { result ->
            if (result.status.isSuccess) {
                AppLogger.i(LogCategory.CAST, TAG, "Media successfully loaded and playing on Cast device!")
                _isCastingFlow.value = true
                isSessionConnected = true
                onStarted?.invoke()
            } else {
                AppLogger.e(LogCategory.CAST, TAG, "Cast media load failed with status code: ${result.status.statusCode}")
                _isCastingFlow.value = false
                isSessionConnected = false
                onError?.invoke("Cast speaker playback error (code: ${result.status.statusCode})")
            }
        }
    }

    private fun attachSessionListener(
        castContext: CastContext,
        onStarted: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        detachSessionListener(castContext)

        val listener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarting(session: CastSession) {
                AppLogger.i(LogCategory.CAST, TAG, "Cast session starting...")
            }

            override fun onSessionStarted(session: CastSession, sessionId: String) {
                AppLogger.i(LogCategory.CAST, TAG, "Cast session started successfully (SessionId: $sessionId)")
                isSessionConnected = true
                cancelTimeoutWatchdog()
                try {
                    if (session.volume < 0.15) {
                        session.volume = 0.5
                    }
                } catch (_: Exception) {}

                val media = pendingMediaInfo
                if (media != null) {
                    loadMediaOnSession(session, media, onStarted, onError)
                    pendingMediaInfo = null
                }
            }

            override fun onSessionEnded(session: CastSession, error: Int) {
                AppLogger.i(LogCategory.CAST, TAG, "Cast session ended (code: $error)")
                cancelTimeoutWatchdog()
                isSessionConnected = false
                _isCastingFlow.value = false
                detachSessionListener(castContext)
            }

            override fun onSessionStartFailed(session: CastSession, error: Int) {
                AppLogger.e(LogCategory.CAST, TAG, "Cast session failed to start (error: $error)")
                cancelTimeoutWatchdog()
                isSessionConnected = false
                _isCastingFlow.value = false
                detachSessionListener(castContext)
                onError?.invoke("Could not connect to Google Home speaker (error $error). Check Wi-Fi connection.")
            }

            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                AppLogger.i(LogCategory.CAST, TAG, "Cast session resumed")
                _isCastingFlow.value = true
            }

            override fun onSessionResumeFailed(session: CastSession, error: Int) {
                AppLogger.w(LogCategory.CAST, TAG, "Cast session resume failed: error $error")
                _isCastingFlow.value = false
                detachSessionListener(castContext)
            }

            override fun onSessionEnding(session: CastSession) {
                AppLogger.i(LogCategory.CAST, TAG, "Cast session ending...")
            }
            override fun onSessionResuming(session: CastSession, sessionId: String) {
                AppLogger.i(LogCategory.CAST, TAG, "Cast session resuming (SessionId: $sessionId)...")
            }
            override fun onSessionSuspended(session: CastSession, reason: Int) {
                AppLogger.w(LogCategory.CAST, TAG, "Cast session suspended: reason $reason")
            }
        }

        activeSessionListener = listener
        castContext.sessionManager.addSessionManagerListener(listener, CastSession::class.java)
    }

    private fun detachSessionListener(castContext: CastContext) {
        activeSessionListener?.let {
            try {
                castContext.sessionManager.removeSessionManagerListener(it, CastSession::class.java)
            } catch (_: Exception) {}
        }
        activeSessionListener = null
    }

    /**
     * Stops casting on the connected Google Cast device and shuts down the local HTTP streamer.
     */
    fun stopCast(context: Context) {
        cancelTimeoutWatchdog()
        mainHandler.post {
            try {
                AppLogger.i(LogCategory.CAST, TAG, "stopCast called")
                val castContext = try {
                    CastContext.getSharedInstance(context)
                } catch (_: Exception) {
                    null
                }
                castContext?.sessionManager?.currentCastSession?.remoteMediaClient?.let { client ->
                    remoteClientCallback?.let { client.unregisterCallback(it) }
                    client.stop()
                }
            } catch (e: Exception) {
                AppLogger.w(LogCategory.CAST, TAG, "Error stopping remote media client: ${e.message}")
            } finally {
                remoteClientCallback = null
                localHttpServer.stop()
                isSessionConnected = false
                _isCastingFlow.value = false
                pendingMediaInfo = null
            }
        }
    }
}
