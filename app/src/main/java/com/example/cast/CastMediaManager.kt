package com.example.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.example.model.PrayerType
import com.example.util.AppLogger
import com.example.util.LogCategory
import com.google.android.gms.cast.Cast
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.LaunchOptions
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.RemoteMediaPlayer
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class CastRouteCandidate(
    val id: String,
    val name: String,
    val description: String?,
    val isDefault: Boolean,
    val matchesSelector: Boolean,
    val hasCastDevice: Boolean
)

@Suppress("DEPRECATION")
object CastMediaManager {

    private const val TAG = "CastMediaManager"
    private const val OVERALL_TIMEOUT_MS = 28000L
    private const val ATTEMPT_TIMEOUT_MS = 8500L
    internal const val WIFI_WAIT_FAIL_FAST_MS = 7500L
    private const val SUSPENSION_TIMEOUT_MS = 12000L
    private const val MAX_STREAMING_DURATION_MS = 7 * 60 * 1000L
    private const val MAX_RETRY_ATTEMPTS = 3

    private val localHttpServer = LocalHttpAudioServer()
    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }
    private val castApi: Cast.CastApi by lazy {
        Cast::class.java.getField("CastApi").get(null) as Cast.CastApi
    }

    private val _isCastingFlow = MutableStateFlow(false)
    val isCastingFlow: StateFlow<Boolean> = _isCastingFlow.asStateFlow()

    @Volatile
    private var isCastInProgress = false

    @Volatile
    private var isSessionConnected = false

    @Volatile
    private var castGeneration: Long = 0L

    @Volatile
    private var activeAttemptId: Long = 0L

    private var activeApiClient: GoogleApiClient? = null
    private var activeRemotePlayer: RemoteMediaPlayer? = null

    private var activeDiscoveryCallback: MediaRouter.Callback? = null
    private var activeScanCallback: MediaRouter.Callback? = null
    private var activeNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var activeConnectivityManager: ConnectivityManager? = null
    private var overallTimeoutRunnable: Runnable? = null
    private var attemptTimeoutRunnable: Runnable? = null
    private var activePollRunnable: Runnable? = null
    private var pendingSelectionRunnable: Runnable? = null
    private var pendingRetryRunnable: Runnable? = null
    private var wifiWaitFailFastRunnable: Runnable? = null
    private var suspensionTimeoutRunnable: Runnable? = null
    private var maxStreamingDurationRunnable: Runnable? = null

    @Volatile
    private var hasInitiatedConnection = false

    @Volatile
    private var retryAttempts = 0

    private var multicastLock: WifiManager.MulticastLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    internal fun isValidCastRouteId(id: String, isDefault: Boolean): Boolean {
        if (isDefault) return false
        return !id.endsWith("-member") && !id.contains(":member") && !id.contains("-member")
    }

    internal fun isGroupRouteInfo(name: String, description: String?): Boolean {
        return name.contains("group", ignoreCase = true) ||
            (description?.contains("group", ignoreCase = true) == true)
    }

    internal fun findBestTargetRoute(
        routes: List<CastRouteCandidate>,
        targetIds: List<String>,
        targetNames: List<String>
    ): CastRouteCandidate? {
        val valid = routes.filter { isValidCastRouteId(it.id, it.isDefault) && it.hasCastDevice }
        if (valid.isEmpty()) return null

        if (targetIds.size > 1) {
            val groupMatch = valid.lastOrNull { it.matchesSelector && isGroupRouteInfo(it.name, it.description) }
            if (groupMatch != null) return groupMatch
        }

        if (targetIds.isNotEmpty() || targetNames.isNotEmpty()) {
            // Strict target matching: never hijack an unrelated TV/speaker from initial cache while
            // active scan is still discovering the user's configured target.
            val idMatch = if (targetIds.isNotEmpty()) {
                valid.lastOrNull { targetIds.contains(it.id) }
            } else null
            if (idMatch != null) return idMatch

            return if (targetNames.isNotEmpty()) {
                valid.lastOrNull { route -> targetNames.any { it.equals(route.name, ignoreCase = true) } }
            } else null
        }

        // Only if no specific destination was configured, pick a sensible speaker over a TV.
        return valid.firstOrNull {
            !isGroupRouteInfo(it.name, it.description) &&
                !it.name.contains("TV", ignoreCase = true) &&
                it.matchesSelector
        } ?: valid.firstOrNull { it.matchesSelector }
    }

    internal fun findFallbackSpeakerRoute(
        routes: List<CastRouteCandidate>,
        excludeIds: Set<String>
    ): CastRouteCandidate? {
        val valid = routes.filter { isValidCastRouteId(it.id, it.isDefault) && it.hasCastDevice && it.matchesSelector }
        if (valid.isEmpty()) return null

        val nonExcluded = valid.filter { !excludeIds.contains(it.id) }
        val pool = if (nonExcluded.isNotEmpty()) nonExcluded else valid

        return pool.firstOrNull {
            !isGroupRouteInfo(it.name, it.description) && !it.name.contains("TV", ignoreCase = true)
        } ?: pool.firstOrNull {
            !isGroupRouteInfo(it.name, it.description)
        } ?: pool.firstOrNull()
    }

    internal fun shouldTreatStatusAsCompleted(
        hasMediaLoaded: Boolean,
        playerState: Int,
        idleReason: Int
    ): Boolean {
        return hasMediaLoaded &&
            playerState == MediaStatus.PLAYER_STATE_IDLE &&
            idleReason != MediaStatus.IDLE_REASON_NONE
    }

    internal fun shouldFailFastNoWifi(
        wifiEnabled: Boolean,
        hasWifiTransport: Boolean,
        validRouteCount: Int,
        elapsedMs: Long
    ): Boolean {
        if (validRouteCount > 0) return false
        if (!wifiEnabled) return true
        return !hasWifiTransport && elapsedMs >= WIFI_WAIT_FAIL_FAST_MS
    }

    internal fun shouldAdjustVolume(
        hasVerifiedVolume: Boolean,
        volume: Double,
        isMuted: Boolean
    ): Boolean {
        return !hasVerifiedVolume && (isMuted || volume < 0.15)
    }

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
                val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wifiManager?.createWifiLock(lockMode, "CastMediaManager::WifiLock")?.apply {
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
     * Ensures background route discovery is active so routes are freshly resolved in MediaRouter.
     */
    fun startDiscovery(context: Context) {
        acquireLocks(context)
        mainHandler.post {
            try {
                val mediaRouter = MediaRouter.getInstance(context)
                val selector = getCastSelector()

                if (activeDiscoveryCallback == null) {
                    val cb = object : MediaRouter.Callback() {
                        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
                            AppLogger.i(LogCategory.CAST, TAG, "Route discovered: ${route.name} (ID: ${route.id})")
                        }

                        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
                            // Suppress repetitive logging on frequent route property updates
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
            removeDiscoveryCallbacks(context)
        }
    }

    private fun removeDiscoveryCallbacks(context: Context) {
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

    /**
     * Initiates audio streaming directly to the resolved CastDevice via Google Play Services Cast API.
     * Bypassing CastContext/SessionManager avoids foreground AppVisibility restrictions when alarms fire
     * from AthanAudioService while the app is backgrounded or the screen is off.
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
        val generation = ++castGeneration
        cancelScheduledRunnables()
        disconnectActiveClient(stopRemoteMedia = false)

        isCastInProgress = true
        _isCastingFlow.value = false
        isSessionConnected = false
        hasInitiatedConnection = false
        retryAttempts = 0
        acquireLocks(context)

        AppLogger.i(
            LogCategory.CAST,
            TAG,
            "playOnCast requested: sound=$soundChoice, prayer=$prayerType, targetId=$targetDeviceId, targetName=$targetDeviceName"
        )

        val resolved = AudioStreamResolver.resolveAudioData(context, soundChoice, prayerType)
        var currentStreamUrl = localHttpServer.startWithBytes(resolved.bytes, resolved.mimeType, context)
        AppLogger.i(LogCategory.CAST, TAG, "Local HTTP audio stream URL ready: $currentStreamUrl (${resolved.bytes.size} bytes)")

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, resolved.title)
            putString(MediaMetadata.KEY_ARTIST, "Athan Alarm")
            putString(MediaMetadata.KEY_SUBTITLE, "Notification")
        }

        fun buildMediaInfo(url: String): MediaInfo {
            return MediaInfo.Builder(url)
                .setContentType(resolved.mimeType)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setMetadata(metadata)
                .build()
        }

        var currentMediaInfo = buildMediaInfo(currentStreamUrl)
        var errorDelivered = false
        fun deliverError(message: String) {
            if (!errorDelivered) {
                errorDelivered = true
                onError?.invoke(message)
            }
        }

        mainHandler.post {
            if (generation != castGeneration || !isCastInProgress) return@post
            try {
                val appContext = context.applicationContext
                val mediaRouter = MediaRouter.getInstance(appContext)
                val selector = getCastSelector()

                val targetIds = targetDeviceId?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                val targetNames = targetDeviceName?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

                fun getCastDevice(route: MediaRouter.RouteInfo): CastDevice? {
                    return try {
                        CastDevice.getFromBundle(route.extras)
                    } catch (_: Exception) {
                        null
                    }
                }

                fun toCandidates(routes: List<MediaRouter.RouteInfo>): List<CastRouteCandidate> {
                    return routes.map { route ->
                        CastRouteCandidate(
                            id = route.id,
                            name = route.name,
                            description = route.description,
                            isDefault = route.isDefault,
                            matchesSelector = route.matchesSelector(selector),
                            hasCastDevice = getCastDevice(route) != null
                        )
                    }
                }

                fun findMatchingRoute(): MediaRouter.RouteInfo? {
                    val routes = mediaRouter.routes
                    val best = findBestTargetRoute(toCandidates(routes), targetIds, targetNames) ?: return null
                    return routes.lastOrNull { it.id == best.id }
                }

                val initialRoutes = mediaRouter.routes
                val nonDefaultInitial = initialRoutes.filter { !it.isDefault }
                val validInitialCount = toCandidates(initialRoutes).count {
                    isValidCastRouteId(it.id, it.isDefault) && it.hasCastDevice
                }
                val wifiEnabled = LocalHttpAudioServer.isWifiEnabled(appContext)
                val hasWifiTransport = LocalHttpAudioServer.hasWifiTransport(appContext)

                AppLogger.i(
                    LogCategory.CAST,
                    TAG,
                    "MediaRouter initial cache: total=${initialRoutes.size}, nonDefault=${nonDefaultInitial.size}, wifiEnabled=$wifiEnabled, hasWifiTransport=$hasWifiTransport: ${
                        nonDefaultInitial.map { "${it.name}(${it.id}, hasCastDev=${getCastDevice(it) != null})" }
                    }"
                )

                if (shouldFailFastNoWifi(wifiEnabled, hasWifiTransport, validInitialCount, elapsedMs = 0L)) {
                    val label = targetDeviceName ?: "Google Cast speaker"
                    AppLogger.w(LogCategory.CAST, TAG, "Wi-Fi is turned off and no Cast routes are available; failing fast for phone fallback.")
                    stopCast(appContext)
                    deliverError("Wi-Fi is turned off; cannot cast to $label.")
                    return@post
                }

                // Remove old scan callbacks before starting a fresh active scan
                removeDiscoveryCallbacks(appContext)
                startDiscovery(appContext)

                val attemptedRouteIds = linkedSetOf<String>()
                var isRetrying = false

                fun cancelPendingSelection() {
                    pendingSelectionRunnable?.let { mainHandler.removeCallbacks(it) }
                    pendingSelectionRunnable = null
                }

                fun startOverallWatchdog() {
                    overallTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                    val watchdog = Runnable {
                        if (generation != castGeneration || isSessionConnected || !isCastInProgress) return@Runnable
                        val label = targetDeviceName ?: "Google Cast speaker"
                        AppLogger.w(LogCategory.CAST, TAG, "Cast playback timed out (${OVERALL_TIMEOUT_MS}ms) for: $label")
                        stopCast(appContext)
                        deliverError("Could not connect to $label. Please check that it is powered on and on the same Wi-Fi.")
                    }
                    overallTimeoutRunnable = watchdog
                    mainHandler.postDelayed(watchdog, OVERALL_TIMEOUT_MS)
                }

                lateinit var connectToRoute: (MediaRouter.RouteInfo, String) -> Unit

                fun handleAttemptFailure(reason: String, failedRouteId: String) {
                    if (generation != castGeneration || !isCastInProgress || isSessionConnected || isRetrying) return
                    attemptTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                    attemptTimeoutRunnable = null
                    suspensionTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                    suspensionTimeoutRunnable = null
                    disconnectActiveClient(stopRemoteMedia = false)

                    if (retryAttempts < MAX_RETRY_ATTEMPTS) {
                        retryAttempts++
                        isRetrying = true
                        attemptedRouteIds.add(failedRouteId)
                        AppLogger.w(
                            LogCategory.CAST,
                            TAG,
                            "Cast attempt failed ($reason). Scheduling retry #$retryAttempts of $MAX_RETRY_ATTEMPTS..."
                        )

                        val retryRunnable = Runnable {
                            isRetrying = false
                            if (generation != castGeneration || !isCastInProgress || isSessionConnected) return@Runnable

                            val routes = mediaRouter.routes
                            val candidates = toCandidates(routes)

                            // Attempt 1: retry target with freshest RouteInfo from active scan.
                            // Attempt >= 2: if group/target already failed twice, fall back to an active individual speaker.
                            val nextRoute: MediaRouter.RouteInfo? = if (retryAttempts == 1) {
                                findMatchingRoute()
                            } else {
                                val fallbackCandidate = findFallbackSpeakerRoute(candidates, attemptedRouteIds)
                                if (fallbackCandidate != null) {
                                    AppLogger.w(
                                        LogCategory.CAST,
                                        TAG,
                                        "Target re-attempts exhausted (attempt #$retryAttempts). Falling back to active speaker: ${fallbackCandidate.name} (${fallbackCandidate.id})"
                                    )
                                    routes.lastOrNull { it.id == fallbackCandidate.id }
                                } else {
                                    findMatchingRoute()
                                }
                            }

                            if (nextRoute != null) {
                                connectToRoute(nextRoute, "retry(#$retryAttempts)")
                            } else {
                                AppLogger.e(LogCategory.CAST, TAG, "No Cast route available for retry #$retryAttempts")
                                stopCast(appContext)
                                deliverError("Could not connect to ${targetDeviceName ?: "Google Home speaker"} ($reason).")
                            }
                        }
                        pendingRetryRunnable?.let { mainHandler.removeCallbacks(it) }
                        pendingRetryRunnable = retryRunnable
                        mainHandler.postDelayed(retryRunnable, 500L)
                    } else {
                        AppLogger.e(LogCategory.CAST, TAG, "All $retryAttempts Cast connection attempts failed ($reason).")
                        stopCast(appContext)
                        deliverError("Could not connect to ${targetDeviceName ?: "Google Home speaker"} after $retryAttempts attempts ($reason).")
                    }
                }

                connectToRoute = { route, source ->
                    if (generation == castGeneration && isCastInProgress && !isSessionConnected) {
                        cancelPendingSelection()
                        wifiWaitFailFastRunnable?.let { mainHandler.removeCallbacks(it) }
                        wifiWaitFailFastRunnable = null
                        hasInitiatedConnection = true
                        val castDevice = getCastDevice(route)
                        if (castDevice == null) {
                            AppLogger.w(LogCategory.CAST, TAG, "Route ${route.name} (${route.id}) had null CastDevice via $source")
                            handleAttemptFailure("Missing CastDevice metadata", route.id)
                        } else {
                            val attemptId = ++activeAttemptId
                            disconnectActiveClient(stopRemoteMedia = false)

                            AppLogger.i(
                                LogCategory.CAST,
                                TAG,
                                "Connecting directly to CastDevice via $source [attempt=${retryAttempts + 1}]: " +
                                    "name=${castDevice.friendlyName}, id=${castDevice.deviceId}, " +
                                    "ip=${castDevice.inetAddress?.hostAddress ?: "unknown"}:${castDevice.servicePort}, " +
                                    "onLocalNet=${castDevice.isOnLocalNetwork}"
                            )

                            // Per-attempt timeout so a stalled socket/handshake triggers retry & speaker fallback cleanly
                            attemptTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                            val attemptTimeout = Runnable {
                                if (generation == castGeneration && attemptId == activeAttemptId && isCastInProgress && !isSessionConnected) {
                                    handleAttemptFailure("Attempt timed out after ${ATTEMPT_TIMEOUT_MS}ms", route.id)
                                }
                            }
                            attemptTimeoutRunnable = attemptTimeout
                            mainHandler.postDelayed(attemptTimeout, ATTEMPT_TIMEOUT_MS)

                            var hasConnectedForAttempt = false
                            var hasMediaLoadedForAttempt = false
                            var hasVerifiedVolume = false
                            var clientRef: GoogleApiClient? = null

                            val castListener = object : Cast.Listener() {
                                override fun onApplicationDisconnected(statusCode: Int) {
                                    if (generation != castGeneration || attemptId != activeAttemptId) return
                                    AppLogger.i(LogCategory.CAST, TAG, "Cast application disconnected (statusCode=$statusCode, connected=$isSessionConnected)")
                                    if (isSessionConnected) {
                                        isSessionConnected = false
                                        _isCastingFlow.value = false
                                        isCastInProgress = false
                                        stopCast(appContext)
                                    } else if (isCastInProgress) {
                                        handleAttemptFailure("Application disconnected (status=$statusCode)", route.id)
                                    }
                                }

                                override fun onVolumeChanged() {
                                    val client = clientRef ?: return
                                    if (generation != castGeneration || attemptId != activeAttemptId || !isCastInProgress) return
                                    if (hasVerifiedVolume || !client.isConnected) return
                                    hasVerifiedVolume = true
                                    try {
                                        val currentVol = castApi.getVolume(client)
                                        val muted = castApi.isMute(client)
                                        if (shouldAdjustVolume(hasVerifiedVolume = false, volume = currentVol, isMuted = muted)) {
                                            if (muted) {
                                                castApi.setMute(client, false)
                                                AppLogger.i(LogCategory.CAST, TAG, "Unmuted Cast device after status sync")
                                            }
                                            if (currentVol < 0.15) {
                                                castApi.setVolume(client, 0.5)
                                                AppLogger.i(LogCategory.CAST, TAG, "Adjusted Cast volume from ${(currentVol * 100).toInt()}% to 50% (real status)")
                                            }
                                        } else {
                                            AppLogger.i(LogCategory.CAST, TAG, "Preserving Cast speaker volume: ${(currentVol * 100).toInt()}% (muted=$muted)")
                                        }
                                    } catch (e: Exception) {
                                        AppLogger.w(LogCategory.CAST, TAG, "Non-fatal volume adjustment check failed: ${e.message}")
                                    }
                                }
                            }

                            val connectionCallbacks = object : GoogleApiClient.ConnectionCallbacks {
                                override fun onConnected(connectionHint: Bundle?) {
                                    val client = clientRef ?: return
                                    if (generation != castGeneration || attemptId != activeAttemptId || !isCastInProgress) return

                                    // Cancel any pending suspension teardown if Play Services auto-reconnected
                                    suspensionTimeoutRunnable?.let {
                                        mainHandler.removeCallbacks(it)
                                        suspensionTimeoutRunnable = null
                                    }

                                    if (hasConnectedForAttempt) {
                                        AppLogger.i(LogCategory.CAST, TAG, "GoogleApiClient reconnected after suspension to ${castDevice.friendlyName}")
                                        try {
                                            castApi.requestStatus(client)
                                        } catch (_: Exception) {}
                                        return
                                    }
                                    hasConnectedForAttempt = true

                                    AppLogger.i(
                                        LogCategory.CAST,
                                        TAG,
                                        "GoogleApiClient connected to ${castDevice.friendlyName} (hintAppStopped=${connectionHint?.getBoolean(Cast.EXTRA_APP_NO_LONGER_RUNNING)})"
                                    )

                                    if (!client.isConnected) {
                                        handleAttemptFailure("GoogleApiClient disconnected immediately after onConnected", route.id)
                                        return
                                    }

                                    // Request actual device status so Cast.Listener.onVolumeChanged inspects real speaker volume
                                    // instead of reading Play Services' pre-status 0.0 default.
                                    try {
                                        castApi.requestStatus(client)
                                    } catch (_: Exception) {}

                                    val launchOptions = LaunchOptions.Builder()
                                        .setRelaunchIfRunning(true)
                                        .build()

                                    AppLogger.i(LogCategory.CAST, TAG, "Launching Default Media Receiver on ${castDevice.friendlyName}...")
                                    try {
                                        castApi.launchApplication(
                                            client,
                                            CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID,
                                            launchOptions
                                        ).setResultCallback { appResult ->
                                            if (generation != castGeneration || attemptId != activeAttemptId || !isCastInProgress) return@setResultCallback
                                            if (!appResult.status.isSuccess) {
                                                handleAttemptFailure(
                                                    "launchApplication failed (code=${appResult.status.statusCode}, msg=${appResult.status.statusMessage})",
                                                    route.id
                                                )
                                                return@setResultCallback
                                            }

                                            AppLogger.i(
                                                LogCategory.CAST,
                                                TAG,
                                                "Receiver launched (sessionId=${appResult.sessionId}, wasLaunched=${appResult.wasLaunched})"
                                            )

                                            if (!client.isConnected) {
                                                handleAttemptFailure("Client disconnected before RemoteMediaPlayer.load", route.id)
                                                return@setResultCallback
                                            }

                                            val player = RemoteMediaPlayer()
                                            activeRemotePlayer = player
                                            var lastLoggedState = -1

                                            player.setOnStatusUpdatedListener {
                                                if (generation != castGeneration || attemptId != activeAttemptId) return@setOnStatusUpdatedListener
                                                val status = player.mediaStatus ?: return@setOnStatusUpdatedListener
                                                val playerState = status.playerState
                                                if (playerState != lastLoggedState) {
                                                    lastLoggedState = playerState
                                                    AppLogger.i(
                                                        LogCategory.CAST,
                                                        TAG,
                                                        "RemoteMediaPlayer state=$playerState, idleReason=${status.idleReason}"
                                                    )
                                                }
                                                if (shouldTreatStatusAsCompleted(hasMediaLoadedForAttempt, playerState, status.idleReason)) {
                                                    AppLogger.i(
                                                        LogCategory.CAST,
                                                        TAG,
                                                        "RemoteMediaPlayer completed playback (idleReason=${status.idleReason}). Stopping cast cleanly."
                                                    )
                                                    _isCastingFlow.value = false
                                                    isCastInProgress = false
                                                    isSessionConnected = false
                                                    stopCast(appContext)
                                                }
                                            }

                                            try {
                                                castApi.setMessageReceivedCallbacks(client, player.namespace, player)
                                            } catch (e: Exception) {
                                                handleAttemptFailure("Failed registering RemoteMediaPlayer channel: ${e.message}", route.id)
                                                return@setResultCallback
                                            }

                                            val freshUrl = localHttpServer.getStreamUrl(appContext)
                                            val targetMediaInfo = if (!freshUrl.contains("127.0.0.1") && freshUrl != currentMediaInfo.contentId) {
                                                AppLogger.i(LogCategory.CAST, TAG, "Refreshing stream URL to Wi-Fi IP: $freshUrl")
                                                currentStreamUrl = freshUrl
                                                buildMediaInfo(freshUrl).also { currentMediaInfo = it }
                                            } else {
                                                currentMediaInfo
                                            }

                                            AppLogger.i(LogCategory.CAST, TAG, "Loading MediaInfo into RemoteMediaPlayer (${targetMediaInfo.contentId})...")
                                            try {
                                                player.load(client, targetMediaInfo, true).setResultCallback { mediaResult ->
                                                    if (generation != castGeneration || attemptId != activeAttemptId || !isCastInProgress) return@setResultCallback
                                                    if (mediaResult.status.isSuccess) {
                                                        AppLogger.i(
                                                            LogCategory.CAST,
                                                            TAG,
                                                            "Media successfully loaded and playing on ${castDevice.friendlyName}!"
                                                        )
                                                        hasMediaLoadedForAttempt = true
                                                        isSessionConnected = true
                                                        _isCastingFlow.value = true
                                                        cancelWatchdogs()
                                                        activePollRunnable?.let { mainHandler.removeCallbacks(it) }
                                                        activePollRunnable = null
                                                        removeDiscoveryCallbacks(appContext)
                                                        unregisterNetworkCallback()

                                                        // Safety ceiling in case connection silently drops without an IDLE status update
                                                        maxStreamingDurationRunnable?.let { mainHandler.removeCallbacks(it) }
                                                        val maxDurationGuard = Runnable {
                                                            if (generation == castGeneration && attemptId == activeAttemptId && isCastInProgress) {
                                                                AppLogger.w(
                                                                    LogCategory.CAST,
                                                                    TAG,
                                                                    "Max Cast streaming duration (${MAX_STREAMING_DURATION_MS}ms) reached; stopping cast cleanly."
                                                                )
                                                                stopCast(appContext)
                                                            }
                                                        }
                                                        maxStreamingDurationRunnable = maxDurationGuard
                                                        mainHandler.postDelayed(maxDurationGuard, MAX_STREAMING_DURATION_MS)

                                                        onStarted?.invoke()
                                                    } else {
                                                        handleAttemptFailure(
                                                            "RemoteMediaPlayer.load failed (code=${mediaResult.status.statusCode}, msg=${mediaResult.status.statusMessage})",
                                                            route.id
                                                        )
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                handleAttemptFailure("Exception during RemoteMediaPlayer.load: ${e.message}", route.id)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        handleAttemptFailure("Exception during launchApplication: ${e.message}", route.id)
                                    }
                                }

                                override fun onConnectionSuspended(cause: Int) {
                                    if (generation != castGeneration || attemptId != activeAttemptId || !isCastInProgress) return
                                    AppLogger.w(
                                        LogCategory.CAST,
                                        TAG,
                                        "GoogleApiClient connection suspended (cause=$cause, connected=$isSessionConnected)"
                                    )
                                    suspensionTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                                    val timeout = Runnable {
                                        if (generation != castGeneration || attemptId != activeAttemptId || !isCastInProgress) return@Runnable
                                        if (isSessionConnected) {
                                            AppLogger.w(
                                                LogCategory.CAST,
                                                TAG,
                                                "Cast connection remained suspended for ${SUSPENSION_TIMEOUT_MS}ms during playback; stopping cast cleanly to prevent server/service leak."
                                            )
                                            stopCast(appContext)
                                        } else {
                                            handleAttemptFailure("GoogleApiClient connection suspended (cause=$cause)", route.id)
                                        }
                                    }
                                    suspensionTimeoutRunnable = timeout
                                    mainHandler.postDelayed(timeout, SUSPENSION_TIMEOUT_MS)
                                }
                            }

                            val connectionFailedListener = GoogleApiClient.OnConnectionFailedListener { result: ConnectionResult ->
                                if (generation != castGeneration || attemptId != activeAttemptId || !isCastInProgress) return@OnConnectionFailedListener
                                handleAttemptFailure(
                                    "GoogleApiClient connection failed (code=${result.errorCode}, msg=${result.errorMessage})",
                                    route.id
                                )
                            }

                            val castOptions = Cast.CastOptions.Builder(castDevice, castListener).build()
                            val apiClient = GoogleApiClient.Builder(appContext)
                                .addApi(Cast.API, castOptions)
                                .addConnectionCallbacks(connectionCallbacks)
                                .addOnConnectionFailedListener(connectionFailedListener)
                                .build()

                            clientRef = apiClient
                            activeApiClient = apiClient
                            apiClient.connect()
                        }
                    }
                }

                val scheduleConnection: (MediaRouter.RouteInfo, String, Long) -> Unit = { route, source, delayMs ->
                    if (!hasInitiatedConnection && !isRetrying) {
                        cancelPendingSelection()
                        val r = Runnable {
                            if (generation != castGeneration || !isCastInProgress || isSessionConnected || hasInitiatedConnection || isRetrying) {
                                return@Runnable
                            }
                            val freshTarget = findMatchingRoute() ?: route
                            connectToRoute(freshTarget, "$source(settled)")
                        }
                        pendingSelectionRunnable = r
                        mainHandler.postDelayed(r, delayMs)
                    }
                }

                val scanListener = object : MediaRouter.Callback() {
                    override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
                        if (generation != castGeneration || !isCastInProgress) return
                        val castDev = getCastDevice(route)
                        val valid = isValidCastRouteId(route.id, route.isDefault)
                        AppLogger.i(
                            LogCategory.CAST,
                            TAG,
                            "onRouteAdded: ${route.name} (${route.id}) [valid=$valid, hasCastDevice=${castDev != null}]"
                        )
                        if (!valid || castDev == null) return

                        if (!hasInitiatedConnection && !isRetrying) {
                            val candidate = CastRouteCandidate(
                                id = route.id,
                                name = route.name,
                                description = route.description,
                                isDefault = route.isDefault,
                                matchesSelector = route.matchesSelector(selector),
                                hasCastDevice = true
                            )
                            if (findBestTargetRoute(listOf(candidate), targetIds, targetNames) != null) {
                                // Live discovery just refreshed this route; connect promptly after brief settlement
                                scheduleConnection(route, "onRouteAdded(live)", 150L)
                            }
                        }
                    }

                    override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
                        if (generation != castGeneration || !isCastInProgress) return
                        if (!isValidCastRouteId(route.id, route.isDefault) || getCastDevice(route) == null) return
                        if (!hasInitiatedConnection && !isRetrying) {
                            val candidate = CastRouteCandidate(
                                id = route.id,
                                name = route.name,
                                description = route.description,
                                isDefault = route.isDefault,
                                matchesSelector = route.matchesSelector(selector),
                                hasCastDevice = true
                            )
                            if (findBestTargetRoute(listOf(candidate), targetIds, targetNames) != null) {
                                scheduleConnection(route, "onRouteChanged(live)", 150L)
                            }
                        }
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

                startOverallWatchdog()

                if (!hasWifiTransport && validInitialCount == 0) {
                    val wifiFailFast = Runnable {
                        if (generation != castGeneration || isSessionConnected || !isCastInProgress || hasInitiatedConnection) {
                            return@Runnable
                        }
                        val currentValidCount = toCandidates(mediaRouter.routes).count {
                            isValidCastRouteId(it.id, it.isDefault) && it.hasCastDevice
                        }
                        val currentWifiEnabled = LocalHttpAudioServer.isWifiEnabled(appContext)
                        val currentHasWifi = LocalHttpAudioServer.hasWifiTransport(appContext)
                        if (shouldFailFastNoWifi(currentWifiEnabled, currentHasWifi, currentValidCount, WIFI_WAIT_FAIL_FAST_MS)) {
                            val label = targetDeviceName ?: "Google Cast speaker"
                            AppLogger.w(
                                LogCategory.CAST,
                                TAG,
                                "No Wi-Fi transport (wifiEnabled=$currentWifiEnabled) and 0 Cast routes after ${WIFI_WAIT_FAIL_FAST_MS}ms; failing fast for phone fallback."
                            )
                            stopCast(appContext)
                            deliverError("Phone is not connected to Wi-Fi; cannot reach $label.")
                        }
                    }
                    wifiWaitFailFastRunnable = wifiFailFast
                    mainHandler.postDelayed(wifiFailFast, WIFI_WAIT_FAIL_FAST_MS)
                }

                if (!hasWifiTransport || currentStreamUrl.contains("127.0.0.1")) {
                    registerWifiRecoveryCallback(appContext) {
                        if (generation != castGeneration || !isCastInProgress || isSessionConnected) return@registerWifiRecoveryCallback
                        val freshUrl = localHttpServer.getStreamUrl(appContext)
                        if (!freshUrl.contains("127.0.0.1") && freshUrl != currentStreamUrl) {
                            currentStreamUrl = freshUrl
                            currentMediaInfo = buildMediaInfo(freshUrl)
                            AppLogger.i(LogCategory.CAST, TAG, "Wi-Fi interface active; updated stream URL to $freshUrl")
                        }
                        if (!hasInitiatedConnection && !isRetrying) {
                            AppLogger.i(LogCategory.CAST, TAG, "Refreshing MediaRouter active scan after Wi-Fi recovery")
                            mediaRouter.removeCallback(scanListener)
                            mediaRouter.addCallback(
                                selector,
                                scanListener,
                                MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN or
                                    MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY or
                                    MediaRouter.CALLBACK_FLAG_UNFILTERED_EVENTS
                            )
                            val recoveredMatch = findMatchingRoute()
                            if (recoveredMatch != null) {
                                scheduleConnection(recoveredMatch, "wifiRecovery", 250L)
                            }
                        }
                    }
                }

                val initialTarget = findMatchingRoute()
                if (initialTarget != null) {
                    val isGroup = isGroupRouteInfo(initialTarget.name, initialTarget.description)
                    val warmupMs = if (isGroup) 450L else 250L
                    AppLogger.i(
                        LogCategory.CAST,
                        TAG,
                        "Found target in initial cache: ${initialTarget.name} (group=$isGroup). Waiting ${warmupMs}ms for live active-scan update before connecting..."
                    )
                    scheduleConnection(initialTarget, "initialCache", warmupMs)
                } else {
                    AppLogger.i(LogCategory.CAST, TAG, "Target route (${targetDeviceName ?: targetDeviceId ?: "any"}) not in initial cache. Scanning...")
                }

                var pollCount = 0
                val pollRunnable = object : Runnable {
                    override fun run() {
                        if (generation != castGeneration || isSessionConnected || !isCastInProgress) {
                            activePollRunnable = null
                            return
                        }

                        pollCount++
                        val routes = mediaRouter.routes
                        val candidates = toCandidates(routes).filter { isValidCastRouteId(it.id, it.isDefault) }

                        if (!hasInitiatedConnection && !isRetrying) {
                            val match = findMatchingRoute()
                            if (match != null) {
                                AppLogger.i(
                                    LogCategory.CAST,
                                    TAG,
                                    "Periodic scan found matching route at tick #$pollCount (${pollCount * 500}ms): ${match.name}"
                                )
                                connectToRoute(match, "periodicScan(match)")
                            } else if (pollCount == 12) {
                                // After 6 seconds without finding the exact saved route, fall back to an active speaker if available
                                val fallbackCandidate = findFallbackSpeakerRoute(candidates, emptySet())
                                val fallbackRoute = fallbackCandidate?.let { fc -> routes.lastOrNull { it.id == fc.id } }
                                if (fallbackRoute != null) {
                                    AppLogger.w(
                                        LogCategory.CAST,
                                        TAG,
                                        "Target (${targetDeviceName ?: "speaker"}) still absent after 6s; falling back to active speaker: ${fallbackRoute.name}"
                                    )
                                    connectToRoute(fallbackRoute, "periodicScan(fallback)")
                                }
                            }
                        }

                        if (pollCount % 4 == 0) {
                            AppLogger.i(
                                LogCategory.CAST,
                                TAG,
                                "Active scan tick #$pollCount (${pollCount * 500}ms): ${candidates.size} routes: ${
                                    candidates.map { "${it.name}(hasCastDev=${it.hasCastDevice})" }
                                }"
                            )
                        }

                        if (pollCount < 55 && isCastInProgress && !isSessionConnected) {
                            mainHandler.postDelayed(this, 500L)
                        } else {
                            activePollRunnable = null
                        }
                    }
                }
                activePollRunnable = pollRunnable
                mainHandler.postDelayed(pollRunnable, 500L)
            } catch (e: Exception) {
                AppLogger.e(LogCategory.CAST, TAG, "Exception in playOnCast: ${e.message}", e)
                stopCast(context)
                deliverError(e.message ?: "Failed to initiate casting")
            }
        }
    }

    private fun registerWifiRecoveryCallback(context: Context, onWifiReady: () -> Unit) {
        unregisterNetworkCallback()
        try {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    mainHandler.post { onWifiReady() }
                }

                override fun onLinkPropertiesChanged(network: Network, linkProperties: android.net.LinkProperties) {
                    mainHandler.post { onWifiReady() }
                }
            }
            activeConnectivityManager = cm
            activeNetworkCallback = callback
            cm.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            AppLogger.w(LogCategory.CAST, TAG, "Failed to register Wi-Fi NetworkCallback: ${e.message}")
        }
    }

    private fun unregisterNetworkCallback() {
        val cb = activeNetworkCallback
        val cm = activeConnectivityManager
        activeNetworkCallback = null
        activeConnectivityManager = null
        if (cb != null && cm != null) {
            try {
                cm.unregisterNetworkCallback(cb)
            } catch (_: Exception) {}
        }
    }

    private fun cancelWatchdogs() {
        overallTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        overallTimeoutRunnable = null
        attemptTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        attemptTimeoutRunnable = null
        wifiWaitFailFastRunnable?.let { mainHandler.removeCallbacks(it) }
        wifiWaitFailFastRunnable = null
    }

    private fun cancelScheduledRunnables() {
        cancelWatchdogs()
        pendingSelectionRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingSelectionRunnable = null
        pendingRetryRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingRetryRunnable = null
        activePollRunnable?.let { mainHandler.removeCallbacks(it) }
        activePollRunnable = null
        suspensionTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        suspensionTimeoutRunnable = null
        maxStreamingDurationRunnable?.let { mainHandler.removeCallbacks(it) }
        maxStreamingDurationRunnable = null
    }

    private fun disconnectActiveClient(stopRemoteMedia: Boolean) {
        val client = activeApiClient
        val player = activeRemotePlayer
        activeApiClient = null
        activeRemotePlayer = null

        if (client != null) {
            var delayedDisconnect = false
            try {
                if (client.isConnected) {
                    if (stopRemoteMedia) {
                        if (player != null) {
                            try {
                                player.stop(client)
                            } catch (_: Exception) {}
                        }
                        try {
                            delayedDisconnect = true
                            var hasDisconnected = false
                            val finishDisconnect = Runnable {
                                if (!hasDisconnected) {
                                    hasDisconnected = true
                                    if (player != null) {
                                        try {
                                            castApi.removeMessageReceivedCallbacks(client, player.namespace)
                                        } catch (_: Exception) {}
                                    }
                                    try {
                                        client.disconnect()
                                    } catch (_: Exception) {}
                                }
                            }
                            mainHandler.postDelayed(finishDisconnect, 400L)
                            castApi.stopApplication(client).setResultCallback {
                                mainHandler.removeCallbacks(finishDisconnect)
                                finishDisconnect.run()
                            }
                        } catch (_: Exception) {
                            delayedDisconnect = false
                        }
                    }
                    if (!delayedDisconnect && player != null) {
                        try {
                            castApi.removeMessageReceivedCallbacks(client, player.namespace)
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {
            } finally {
                if (!delayedDisconnect) {
                    try {
                        client.disconnect()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    /**
     * Stops casting on the connected Google Cast device and shuts down the local HTTP streamer.
     * Safe and idempotent from any thread.
     */
    fun stopCast(context: Context) {
        castGeneration++
        activeAttemptId++
        val hadActivity = isCastInProgress || _isCastingFlow.value || isSessionConnected || activeApiClient != null
        isCastInProgress = false
        isSessionConnected = false
        hasInitiatedConnection = false
        retryAttempts = 0
        _isCastingFlow.value = false

        mainHandler.post {
            try {
                cancelScheduledRunnables()
                unregisterNetworkCallback()
                removeDiscoveryCallbacks(context)
                if (hadActivity) {
                    AppLogger.i(LogCategory.CAST, TAG, "stopCast called: disconnecting Cast client and stopping HTTP server")
                }
                disconnectActiveClient(stopRemoteMedia = true)
            } catch (e: Exception) {
                AppLogger.w(LogCategory.CAST, TAG, "Error in stopCast: ${e.message}")
            } finally {
                localHttpServer.stop()
                releaseLocks()
            }
        }
    }
}
