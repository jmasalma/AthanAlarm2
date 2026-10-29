package com.example.cast

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.example.model.PrayerType
import com.example.util.AppLogger
import com.example.util.LogCategory
import com.google.android.gms.cast.CastDevice
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
    private var activeScanCallback: MediaRouter.Callback? = null
    private var connectionTimeoutRunnable: Runnable? = null
    private var remoteClientCallback: RemoteMediaClient.Callback? = null
    private var activePollRunnable: Runnable? = null
    @Volatile
    private var hasInitiatedSelection = false

    private var multicastLock: WifiManager.MulticastLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private fun acquireLocks(context: Context) {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (multicastLock == null) {
                multicastLock = wifiManager?.createMulticastLock("CastMediaManager::MulticastLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (multicastLock?.isHeld != true) {
                multicastLock?.acquire()
                AppLogger.i(LogCategory.CAST, TAG, "Cast MulticastLock acquired: isHeld=${multicastLock?.isHeld}")
            }

            if (wifiLock == null) {
                @Suppress("DEPRECATION")
                wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "CastMediaManager::WifiLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wifiLock?.isHeld != true) {
                wifiLock?.acquire()
                AppLogger.i(LogCategory.CAST, TAG, "Cast WifiLock acquired: isHeld=${wifiLock?.isHeld}")
            }
        } catch (e: Exception) {
            AppLogger.w(LogCategory.CAST, TAG, "Error acquiring Cast locks: ${e.message}")
        }
    }

    private fun releaseLocks() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
                AppLogger.i(LogCategory.CAST, TAG, "Cast MulticastLock released")
            }
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
                AppLogger.i(LogCategory.CAST, TAG, "Cast WifiLock released")
            }
        } catch (e: Exception) {
            AppLogger.w(LogCategory.CAST, TAG, "Error releasing Cast locks: ${e.message}")
        }
    }

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
        acquireLocks(context)
        mainHandler.post {
            try {
                val mediaRouter = MediaRouter.getInstance(context)
                val castContext = try { CastContext.getSharedInstance(context) } catch (_: Exception) { null }
                val selector = castContext?.mergedSelector ?: getCastSelector()

                if (activeDiscoveryCallback == null) {
                    val cb = object : MediaRouter.Callback() {
                        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
                            AppLogger.i(LogCategory.CAST, TAG, "Route discovered: ${route.name} (ID: ${route.id})")
                        }
                        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
                            AppLogger.i(LogCategory.CAST, TAG, "Route changed: ${route.name} (ID: ${route.id})")
                        }
                        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
                            AppLogger.i(LogCategory.CAST, TAG, "Discovery cb onRouteSelected: ${route.name} (reason=$reason)")
                        }
                        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
                            AppLogger.i(LogCategory.CAST, TAG, "Discovery cb onRouteUnselected: ${route.name} (reason=$reason)")
                        }
                    }
                    activeDiscoveryCallback = cb
                    mediaRouter.addCallback(
                        selector,
                        cb,
                        MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY or
                                MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN or
                                MediaRouter.CALLBACK_FLAG_UNFILTERED_EVENTS
                    )
                    AppLogger.i(LogCategory.CAST, TAG, "Background route discovery started with active scan flags")
                }
            } catch (e: Exception) {
                AppLogger.w(LogCategory.CAST, TAG, "startDiscovery failed: ${e.message}")
            }
        }
    }

    /**
     * Stops active route discovery to preserve device battery when not casting.
     */
    fun stopDiscovery(context: Context) {
        mainHandler.post {
            try {
                val mediaRouter = MediaRouter.getInstance(context)
                activeDiscoveryCallback?.let { cb ->
                    mediaRouter.removeCallback(cb)
                    AppLogger.i(LogCategory.CAST, TAG, "Background route discovery callback removed")
                }
                activeScanCallback?.let { cb ->
                    mediaRouter.removeCallback(cb)
                    AppLogger.i(LogCategory.CAST, TAG, "Active scan route callback removed")
                }
            } catch (e: Exception) {
                AppLogger.w(LogCategory.CAST, TAG, "Error removing discovery callback: ${e.message}")
            } finally {
                activeDiscoveryCallback = null
                activeScanCallback = null
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
                    if (mediaRouter.selectedRoute.id != target.id) {
                        mediaRouter.selectRoute(target)
                    }
                } else {
                    AppLogger.w(LogCategory.CAST, TAG, "Target route $routeId not currently in MediaRouter routes")
                }
            } catch (e: Exception) {
                AppLogger.e(LogCategory.CAST, TAG, "Error selecting route $routeId: ${e.message}", e)
            }
        }
    }

    private fun triggerRouteSelection(
        mediaRouter: MediaRouter,
        selector: MediaRouteSelector,
        targetRoute: MediaRouter.RouteInfo
    ) {
        if (mediaRouter.selectedRoute.id == targetRoute.id) {
            AppLogger.i(
                LogCategory.CAST,
                TAG,
                "Target route is already selected in MediaRouter: ${targetRoute.name} (${targetRoute.id})"
            )
        } else {
            AppLogger.i(
                LogCategory.CAST,
                TAG,
                "Selecting target route in MediaRouter: ${targetRoute.name} (${targetRoute.id})"
            )
            mediaRouter.selectRoute(targetRoute)
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
        hasInitiatedSelection = false
        acquireLocks(context)

        AppLogger.i(
            LogCategory.CAST,
            TAG,
            "playOnCast requested: sound=$soundChoice, prayer=$prayerType, targetId=$targetDeviceId, targetName=$targetDeviceName"
        )
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

                // 2. If session is currently connecting or resuming, wait for it
                if (currentSession != null && (currentSession.isConnecting || currentSession.isResuming)) {
                    AppLogger.i(LogCategory.CAST, TAG, "Existing CastSession is connecting/resuming. Waiting for connection...")
                    pendingMediaInfo = mediaInfo
                    attachSessionListener(castContext, onStarted, onError)
                    startTimeoutWatchdog(context, castContext, targetDeviceName ?: "Google Cast speaker", onError)
                    return@post
                }

                val mediaRouter = MediaRouter.getInstance(context)
                val selector = castContext?.mergedSelector ?: getCastSelector()

                // Keep active scan running so routes are resolved
                startDiscovery(context)

                val initialRoutes = mediaRouter.routes
                val nonDefaultInitial = initialRoutes.filter { !it.isDefault }
                AppLogger.i(
                    LogCategory.CAST,
                    TAG,
                    "MediaRouter initial cache: total=${initialRoutes.size}, nonDefault=${nonDefaultInitial.size}: ${nonDefaultInitial.map { "${it.name}(${it.id})" }}"
                )

                val isValidCastTarget: (MediaRouter.RouteInfo) -> Boolean = { route ->
                    if (route.isDefault) {
                        false
                    } else {
                        val id = route.id
                        !id.endsWith("-member") && !id.contains(":member") && !id.contains("-member")
                    }
                }

                val getCastDevice: (MediaRouter.RouteInfo) -> CastDevice? = { route ->
                    try {
                        CastDevice.getFromBundle(route.extras)
                    } catch (_: Exception) {
                        null
                    }
                }

                val isGroupRoute: (MediaRouter.RouteInfo) -> Boolean = { route ->
                    isValidCastTarget(route) && (
                        route.name.contains("group", ignoreCase = true) ||
                        (route.description?.contains("group", ignoreCase = true) == true)
                    )
                }

                val isRouteTargetMatch: (MediaRouter.RouteInfo) -> Boolean = { route ->
                    if (!isValidCastTarget(route)) {
                        false
                    } else if (targetIds.isNotEmpty()) {
                        if (targetIds.contains(route.id)) {
                            true
                        } else if (targetNames.isNotEmpty()) {
                            targetNames.any { name -> route.name.equals(name, ignoreCase = true) }
                        } else {
                            false
                        }
                    } else if (targetNames.isNotEmpty()) {
                        targetNames.any { name -> route.name.equals(name, ignoreCase = true) }
                    } else {
                        route.matchesSelector(selector)
                    }
                }

                // 3. Find target route
                val findTargetRoute: () -> MediaRouter.RouteInfo? = {
                    val validRoutes = mediaRouter.routes.filter { isValidCastTarget(it) }
                    val groupRoute = if (targetIds.size > 1) {
                        validRoutes.firstOrNull { it.matchesSelector(selector) && isGroupRoute(it) }
                    } else null

                    if (groupRoute != null) {
                        AppLogger.i(
                            LogCategory.CAST,
                            TAG,
                            "Multi-device target ($targetDeviceName) specified; auto-routing to Speaker Group: ${groupRoute.name}"
                        )
                        groupRoute
                    } else {
                        validRoutes.firstOrNull { targetIds.contains(it.id) }
                            ?: validRoutes.firstOrNull { isRouteTargetMatch(it) }
                            ?: validRoutes.firstOrNull { it.matchesSelector(selector) }
                    }
                }

                var lastSelectionAttemptTime = 0L
                var selectedTargetRouteId: String? = null

                val targetRoute = findTargetRoute()

                if (targetRoute != null && castContext != null) {
                    val castDev = getCastDevice(targetRoute)
                    AppLogger.i(
                        LogCategory.CAST,
                        TAG,
                        "Found target route in initial cache: ${targetRoute.name} (${targetRoute.id}) [hasCastDevice=${castDev != null}]. Initiating session..."
                    )
                    hasInitiatedSelection = true
                    selectedTargetRouteId = targetRoute.id
                    lastSelectionAttemptTime = System.currentTimeMillis()
                    pendingMediaInfo = mediaInfo
                    attachSessionListener(castContext, onStarted, onError)
                    triggerRouteSelection(mediaRouter, selector, targetRoute)
                    startTimeoutWatchdog(context, castContext, targetRoute.name, onError)
                } else if (castContext != null) {
                    AppLogger.i(LogCategory.CAST, TAG, "Target route not in initial cache. Starting active scan with callbacks & periodic polling...")
                    pendingMediaInfo = mediaInfo
                    attachSessionListener(castContext, onStarted, onError)

                    var scanListener: MediaRouter.Callback? = null
                    var pollRunnable: Runnable? = null
                    var pollCount = 0

                    val selectMatchingRoute: (MediaRouter.RouteInfo, String) -> Unit = { route, source ->
                        hasInitiatedSelection = true
                        selectedTargetRouteId = route.id
                        lastSelectionAttemptTime = System.currentTimeMillis()
                        val castDev = getCastDevice(route)
                        AppLogger.i(
                            LogCategory.CAST,
                            TAG,
                            "Selecting discovered target route via $source: ${route.name} (${route.id}) [hasCastDevice=${castDev != null}]"
                        )
                        triggerRouteSelection(mediaRouter, selector, route)
                        startTimeoutWatchdog(context, castContext, route.name, onError)
                    }

                    scanListener = object : MediaRouter.Callback() {
                        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
                            val castDev = getCastDevice(route)
                            val valid = isValidCastTarget(route)
                            AppLogger.i(
                                LogCategory.CAST,
                                TAG,
                                "onRouteAdded: ${route.name} (${route.id}) [valid=$valid, hasCastDevice=${castDev != null}]"
                            )
                            if (!valid) return

                            if (!hasInitiatedSelection) {
                                if (targetIds.size > 1 && isGroupRoute(route) && route.matchesSelector(selector)) {
                                    AppLogger.i(
                                        LogCategory.CAST,
                                        TAG,
                                        "Multi-device target configured; auto-selecting discovered Speaker Group: ${route.name}"
                                    )
                                    selectMatchingRoute(route, "onRouteAdded(group)")
                                } else if (isRouteTargetMatch(route)) {
                                    selectMatchingRoute(route, "onRouteAdded(match)")
                                }
                            }
                        }

                        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
                            val castDev = getCastDevice(route)
                            val valid = isValidCastTarget(route)
                            AppLogger.i(
                                LogCategory.CAST,
                                TAG,
                                "onRouteChanged: ${route.name} (${route.id}) [valid=$valid, hasCastDevice=${castDev != null}]"
                            )
                            if (!valid) return

                            // If selection has not been initiated yet, check if this updated route matches our target
                            if (!hasInitiatedSelection) {
                                if (targetIds.size > 1 && isGroupRoute(route) && route.matchesSelector(selector)) {
                                    AppLogger.i(
                                        LogCategory.CAST,
                                        TAG,
                                        "Multi-device target configured; auto-selecting Speaker Group: ${route.name}"
                                    )
                                    selectMatchingRoute(route, "onRouteChanged(group)")
                                } else if (isRouteTargetMatch(route)) {
                                    selectMatchingRoute(route, "onRouteChanged(match)")
                                }
                            }
                        }

                        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
                            AppLogger.i(LogCategory.CAST, TAG, "MediaRouter onRouteSelected: ${route.name} (${route.id}) [reason=$reason]")
                        }

                        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
                            AppLogger.i(LogCategory.CAST, TAG, "MediaRouter onRouteUnselected: ${route.name} (${route.id}) [reason=$reason]")
                        }
                    }
                    activeScanCallback = scanListener

                    mediaRouter.addCallback(
                        selector,
                        scanListener,
                        MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN or
                                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY or
                                MediaRouter.CALLBACK_FLAG_UNFILTERED_EVENTS
                    )

                    // Periodic 500ms polling safeguard
                    pollRunnable = object : Runnable {
                        override fun run() {
                            val curSession = castContext.sessionManager.currentCastSession
                            val isSessionAlive = curSession != null && (curSession.isConnected || curSession.isConnecting || curSession.isResuming)

                            if (isSessionConnected || !_isCastingFlow.value || isSessionAlive) {
                                if (isSessionConnected || isSessionAlive) {
                                    AppLogger.i(LogCategory.CAST, TAG, "CastSession active or connecting. Stopping periodic scan polling.")
                                    pollRunnable = null
                                    activePollRunnable = null
                                    return
                                }
                            }
                            pollCount++
                            val validRoutes = mediaRouter.routes.filter { isValidCastTarget(it) }

                            if (!hasInitiatedSelection) {
                                if (targetIds.size > 1) {
                                    val group = validRoutes.firstOrNull { isGroupRoute(it) && it.matchesSelector(selector) }
                                    if (group != null) {
                                        AppLogger.i(LogCategory.CAST, TAG, "Periodic scan found Speaker Group for multi-device target: ${group.name}")
                                        selectMatchingRoute(group, "periodicScan(group)")
                                        return
                                    }
                                }
                                val match = validRoutes.firstOrNull { targetIds.contains(it.id) }
                                    ?: validRoutes.firstOrNull { isRouteTargetMatch(it) }
                                if (match != null) {
                                    AppLogger.i(LogCategory.CAST, TAG, "Periodic scan found matching route at tick #$pollCount (${pollCount * 500}ms): ${match.name}")
                                    selectMatchingRoute(match, "periodicScan(match)")
                                    return
                                }
                            }

                            if (pollCount % 4 == 0) { // Log every 2 seconds
                                AppLogger.i(
                                    LogCategory.CAST,
                                    TAG,
                                    "Active scan tick #$pollCount (${pollCount * 500}ms): discovered ${validRoutes.size} valid routes: ${validRoutes.map { "${it.name}(hasCastDev=${getCastDevice(it) != null})" }}"
                                )
                            }

                            if (pollCount < 40) { // 20s max polling
                                mainHandler.postDelayed(this, 500L)
                            } else {
                                pollRunnable = null
                                activePollRunnable = null
                            }
                        }
                    }
                    activePollRunnable = pollRunnable
                    mainHandler.postDelayed(pollRunnable!!, 500L)

                    startTimeoutWatchdog(context, castContext, targetDeviceName ?: "Google Cast speaker", onError)
                } else {
                    AppLogger.e(LogCategory.CAST, TAG, "CastContext is null; Google Play Services Cast unavailable")
                    _isCastingFlow.value = false
                    releaseLocks()
                    onError?.invoke("Google Cast service is not available on this device.")
                }
            } catch (e: Exception) {
                AppLogger.e(LogCategory.CAST, TAG, "Exception in playOnCast: ${e.message}", e)
                _isCastingFlow.value = false
                releaseLocks()
                onError?.invoke(e.message ?: "Failed to initiate casting")
            }
        }
    }

    private fun startTimeoutWatchdog(context: Context, castContext: CastContext, deviceName: String, onError: ((String) -> Unit)?) {
        cancelTimeoutWatchdog()
        val watchdog = Runnable {
            if (!isSessionConnected) {
                AppLogger.w(LogCategory.CAST, TAG, "Cast session connection timed out (20s) for: $deviceName")
                detachSessionListener(castContext)
                stopCast(context)
                onError?.invoke("Could not connect to $deviceName. Please check that it is powered on and on the same Wi-Fi.")
            }
        }
        connectionTimeoutRunnable = watchdog
        mainHandler.postDelayed(watchdog, 20000L)
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
                AppLogger.i(
                    LogCategory.CAST,
                    TAG,
                    "Cast session started successfully (SessionId: $sessionId, Device: ${session.castDevice?.friendlyName})"
                )
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
        hasInitiatedSelection = false
        activePollRunnable?.let { mainHandler.removeCallbacks(it) }
        activePollRunnable = null
        stopDiscovery(context)

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
                    try { client.stop() } catch (_: Exception) {}
                }
                try {
                    castContext?.sessionManager?.endCurrentSession(true)
                    AppLogger.i(LogCategory.CAST, TAG, "Cast session ended cleanly via endCurrentSession(true)")
                } catch (e: Exception) {
                    AppLogger.w(LogCategory.CAST, TAG, "Error ending Cast session: ${e.message}")
                }
                try {
                    val mediaRouter = MediaRouter.getInstance(context)
                    if (mediaRouter.selectedRoute.id != mediaRouter.defaultRoute.id) {
                        mediaRouter.selectRoute(mediaRouter.defaultRoute)
                        AppLogger.i(LogCategory.CAST, TAG, "Reset MediaRouter to default route: ${mediaRouter.defaultRoute.name}")
                    }
                } catch (e: Exception) {
                    AppLogger.w(LogCategory.CAST, TAG, "Error resetting MediaRouter route: ${e.message}")
                }
            } catch (e: Exception) {
                AppLogger.w(LogCategory.CAST, TAG, "Error in stopCast: ${e.message}")
            } finally {
                remoteClientCallback = null
                localHttpServer.stop()
                isSessionConnected = false
                _isCastingFlow.value = false
                pendingMediaInfo = null
                releaseLocks()
            }
        }
    }
}
