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
     * Attempts to initiate audio streaming to the selected Cast device while maintaining
     * phone safety playback.
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
        val resolved = AudioStreamResolver.resolveAudio(context, soundChoice, prayerType)
        val streamUrl = localHttpServer.start(resolved.streamSupplier, resolved.contentLength)

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, resolved.title)
            putString(MediaMetadata.KEY_SUBTITLE, "Athan Notification")
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

                val currentSession = castContext?.sessionManager?.currentCastSession
                if (currentSession != null && currentSession.isConnected) {
                    loadMediaOnSession(currentSession, mediaInfo, onStarted, onError)
                    return@post
                }

                // If no active session, attempt route selection via MediaRouter
                val mediaRouter = MediaRouter.getInstance(context)
                val targetRoute = if (!targetDeviceId.isNullOrBlank()) {
                    mediaRouter.routes.firstOrNull { it.id == targetDeviceId }
                } else if (!targetDeviceName.isNullOrBlank()) {
                    mediaRouter.routes.firstOrNull { it.name.equals(targetDeviceName, ignoreCase = true) }
                } else {
                    null
                }

                if (targetRoute != null && castContext != null) {
                    pendingMediaInfo = mediaInfo
                    attachSessionListener(castContext, onStarted, onError)
                    mediaRouter.selectRoute(targetRoute)
                } else if (currentSession != null) {
                    loadMediaOnSession(currentSession, mediaInfo, onStarted, onError)
                } else {
                    Log.w(TAG, "Target cast device not immediately reachable; fallback to phone playback active.")
                    onError?.invoke("Device not connected to session. Please re-select in Cast settings.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cast audio: ${e.message}", e)
                onError?.invoke(e.message ?: "Failed to initiate casting")
            }
        }
    }

    private fun loadMediaOnSession(
        session: CastSession,
        mediaInfo: MediaInfo,
        onStarted: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        val remoteClient = session.remoteMediaClient
        if (remoteClient == null) {
            onError?.invoke("RemoteMediaClient is null")
            return
        }

        val options = MediaLoadOptions.Builder()
            .setAutoplay(true)
            .build()

        val pendingResult = remoteClient.load(mediaInfo, options)
        pendingResult.setResultCallback { result ->
            if (result.status.isSuccess) {
                Log.d(TAG, "Media successfully loaded on Cast device.")
                _isCastingFlow.value = true
                onStarted?.invoke()
            } else {
                Log.w(TAG, "Cast media load failed with status: ${result.status.statusCode}")
                onError?.invoke("Cast device error code: ${result.status.statusCode}")
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
                val media = pendingMediaInfo
                if (media != null) {
                    loadMediaOnSession(session, media, onStarted, onError)
                    pendingMediaInfo = null
                }
            }

            override fun onSessionEnded(session: CastSession, error: Int) {
                _isCastingFlow.value = false
                detachSessionListener(castContext)
            }

            override fun onSessionStartFailed(session: CastSession, error: Int) {
                Log.w(TAG, "Cast session failed to start: error $error")
                _isCastingFlow.value = false
                detachSessionListener(castContext)
                onError?.invoke("Failed to start session on Chromecast: error $error")
            }

            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
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
