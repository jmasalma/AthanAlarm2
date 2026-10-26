package com.example.cast

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.example.model.PrayerType
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadOptions
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CastMediaManager {

    private const val TAG = "CastMediaManager"
    private val localHttpServer = LocalHttpAudioServer()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isCastingFlow = MutableStateFlow(false)
    val isCastingFlow: StateFlow<Boolean> = _isCastingFlow.asStateFlow()

    private var pendingMediaInfo: MediaInfo? = null
    private var activeSessionListener: SessionManagerListener<CastSession>? = null
    private var activeDiscoveryCallback: MediaRouter.Callback? = null
    private var connectionTimeoutRunnable: Runnable? = null

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
                            Log.d(TAG, "Route discovered: ${route.name} (${route.id})")
                        }
                    }
                    activeDiscoveryCallback = cb
                    mediaRouter.addCallback(selector, cb, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
                }
            } catch (e: Exception) {
                Log.w(TAG, "startDiscovery failed: ${e.message}")
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
                    Log.d(TAG, "Selecting Cast route: ${target.name} (${target.id})")
                    if (mediaRouter.selectedRoute.id == target.id) {
                        mediaRouter.unselect(MediaRouter.UNSELECT_REASON_STOPPED)
                    }
                    mediaRouter.selectRoute(target)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error selecting route: ${e.message}")
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
        val resolved = AudioStreamResolver.resolveAudioData(context, soundChoice, prayerType)
        val streamUrl = localHttpServer.startWithBytes(resolved.bytes, resolved.mimeType, context)

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
                    Log.w(TAG, "CastContext unavailable: ${e.message}")
                    null
                }

                // Parse target IDs / names (supports comma-delimited multi-selection)
                val targetIds = targetDeviceId?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                val targetNames = targetDeviceName?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

                val currentSession = castContext?.sessionManager?.currentCastSession

                // 1. If session is already connected, load media directly
                if (currentSession != null && currentSession.isConnected) {
                    try {
                        if (currentSession.volume < 0.15) {
                            currentSession.volume = 0.5
                        }
                    } catch (_: Exception) {}
                    loadMediaOnSession(currentSession, mediaInfo, onStarted, onError)
                    return@post
                }

                val mediaRouter = MediaRouter.getInstance(context)
                val selector = getCastSelector()

                // Keep active scan running so routes are resolved
                startDiscovery(context)

                // 2. Find target route
                val findTargetRoute: () -> MediaRouter.RouteInfo? = {
                    mediaRouter.routes.firstOrNull { route ->
                        targetIds.contains(route.id) || targetNames.any { name -> route.name.equals(name, ignoreCase = true) }
                    } ?: mediaRouter.routes.firstOrNull { route ->
                        !route.isDefault && route.matchesSelector(selector)
                    }
                }

                val targetRoute = findTargetRoute()

                if (targetRoute != null && castContext != null) {
                    pendingMediaInfo = mediaInfo
                    attachSessionListener(castContext, onStarted, onError)

                    // Crucial: if already set to this route in MediaRouter, unselect first to trigger fresh session
                    if (mediaRouter.selectedRoute.id == targetRoute.id) {
                        Log.d(TAG, "Route already selected in MediaRouter; unselecting to trigger reconnection")
                        mediaRouter.unselect(MediaRouter.UNSELECT_REASON_STOPPED)
                    }
                    mediaRouter.selectRoute(targetRoute)
                    startTimeoutWatchdog(castContext, targetRoute.name, onError)
                } else if (castContext != null) {
                    // Device not yet visible in routes; start fast scan and connect as soon as discovered
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
                            if (!route.isDefault && (targetIds.contains(route.id) || targetNames.any { route.name.equals(it, ignoreCase = true) } || route.matchesSelector(selector))) {
                                scanListener?.let { mediaRouter.removeCallback(it) }
                                if (mediaRouter.selectedRoute.id == route.id) {
                                    mediaRouter.unselect(MediaRouter.UNSELECT_REASON_STOPPED)
                                }
                                mediaRouter.selectRoute(route)
                                startTimeoutWatchdog(castContext, route.name, onError)
                            }
                        }
                    }

                    mediaRouter.addCallback(selector, scanListener, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
                    mainHandler.postDelayed({
                        scanListener.let { mediaRouter.removeCallback(it) }
                    }, 5000L)

                    startTimeoutWatchdog(castContext, targetDeviceName ?: "Google Cast speaker", onError)
                } else {
                    Log.w(TAG, "CastContext is null")
                    onError?.invoke("Google Cast service is not available on this device.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cast audio: ${e.message}", e)
                onError?.invoke(e.message ?: "Failed to initiate casting")
            }
        }
    }

    private fun startTimeoutWatchdog(castContext: CastContext, deviceName: String, onError: ((String) -> Unit)?) {
        cancelTimeoutWatchdog()
        val watchdog = Runnable {
            if (pendingMediaInfo != null && !_isCastingFlow.value) {
                Log.w(TAG, "Cast session connection timed out for: $deviceName")
                detachSessionListener(castContext)
                pendingMediaInfo = null
                _isCastingFlow.value = false
                onError?.invoke("Could not connect to $deviceName. Please check that it is powered on and on the same Wi-Fi.")
            }
        }
        connectionTimeoutRunnable = watchdog
        mainHandler.postDelayed(watchdog, 10000L)
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
            onError?.invoke("Remote media client is unavailable on the connected speaker")
            return
        }

        val options = MediaLoadOptions.Builder()
            .setAutoplay(true)
            .setPlayPosition(0)
            .build()

        val pendingResult = remoteClient.load(mediaInfo, options)
        pendingResult.setResultCallback { result ->
            if (result.status.isSuccess) {
                Log.d(TAG, "Media successfully loaded on Cast device.")
                _isCastingFlow.value = true
                onStarted?.invoke()
            } else {
                Log.w(TAG, "Cast media load failed with status: ${result.status.statusCode}")
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
                Log.d(TAG, "Cast session starting...")
            }

            override fun onSessionStarted(session: CastSession, sessionId: String) {
                Log.d(TAG, "Cast session started: $sessionId")
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
                Log.d(TAG, "Cast session ended: $error")
                cancelTimeoutWatchdog()
                _isCastingFlow.value = false
                detachSessionListener(castContext)
            }

            override fun onSessionStartFailed(session: CastSession, error: Int) {
                Log.w(TAG, "Cast session failed to start: error $error")
                cancelTimeoutWatchdog()
                _isCastingFlow.value = false
                detachSessionListener(castContext)
                onError?.invoke("Could not connect to Google Home speaker (error $error). Check Wi-Fi connection.")
            }

            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                Log.d(TAG, "Cast session resumed")
                _isCastingFlow.value = true
            }

            override fun onSessionResumeFailed(session: CastSession, error: Int) {
                _isCastingFlow.value = false
                detachSessionListener(castContext)
            }

            override fun onSessionEnding(session: CastSession) {}
            override fun onSessionResuming(session: CastSession, sessionId: String) {}
            override fun onSessionSuspended(session: CastSession, reason: Int) {}
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
                val castContext = try {
                    CastContext.getSharedInstance(context)
                } catch (_: Exception) {
                    null
                }
                castContext?.sessionManager?.currentCastSession?.remoteMediaClient?.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping remote media client: ${e.message}")
            } finally {
                localHttpServer.stop()
                _isCastingFlow.value = false
                pendingMediaInfo = null
            }
        }
    }
}
