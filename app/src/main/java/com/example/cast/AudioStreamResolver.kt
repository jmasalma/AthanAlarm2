package com.example.cast

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import com.example.R
import com.example.model.PrayerType
import java.io.File
import java.io.InputStream

object AudioStreamResolver {

    private const val TAG = "AudioStreamResolver"

    data class ResolvedAudio(
        val streamSupplier: () -> InputStream?,
        val contentLength: Long,
        val mimeType: String = "audio/mpeg",
        val title: String = "Athan"
    )

    data class ResolvedAudioData(
        val bytes: ByteArray,
        val mimeType: String = "audio/mpeg",
        val title: String = "Athan"
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ResolvedAudioData
            return bytes.contentEquals(other.bytes) && mimeType == other.mimeType && title == other.title
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + mimeType.hashCode()
            result = 31 * result + title.hashCode()
            return result
        }
    }

    fun resolveAudioData(context: Context, soundChoice: String, prayerType: PrayerType = PrayerType.DHUHR): ResolvedAudioData {
        // 1. Custom MP3 File
        val customFilePath = when {
            soundChoice.startsWith("custom_file:") -> soundChoice.removePrefix("custom_file:")
            soundChoice.startsWith("file://") -> Uri.parse(soundChoice).path
            else -> null
        }
        if (customFilePath != null) {
            val file = File(customFilePath)
            if (file.exists()) {
                try {
                    val bytes = file.readBytes()
                    if (bytes.isNotEmpty()) {
                        return ResolvedAudioData(
                            bytes = bytes,
                            mimeType = "audio/mpeg",
                            title = "Custom Audio"
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed reading custom file: ${e.message}")
                }
            }
        }

        // 2. Custom Content URI (from picker)
        if (soundChoice.startsWith("content://")) {
            val uri = Uri.parse(soundChoice)
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    val detectedMime = context.contentResolver.getType(uri) ?: "audio/mpeg"
                    return ResolvedAudioData(
                        bytes = bytes,
                        mimeType = if (detectedMime.startsWith("audio/")) detectedMime else "audio/mpeg",
                        title = "Custom Audio"
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading content URI: ${e.message}")
            }
        }

        // 3. Gentle Ding / Chime (converted to MP3 for universal Google Cast compatibility)
        if (soundChoice == "chime") {
            try {
                val bytes = context.resources.openRawResource(R.raw.athan_chime).use { it.readBytes() }
                if (bytes.isNotEmpty()) {
                    return ResolvedAudioData(
                        bytes = bytes,
                        mimeType = "audio/mpeg",
                        title = "Gentle Chime"
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading chime resource: ${e.message}")
            }
        }

        // 4. System Notification Sound
        if (soundChoice == "system_notification") {
            try {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                val bytes = if (uri != null) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } else null

                if (bytes != null && bytes.isNotEmpty()) {
                    return ResolvedAudioData(
                        bytes = bytes,
                        mimeType = "audio/mpeg",
                        title = "System Notification"
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading system notification sound: ${e.message}")
            }

            // Fallback to chime if system notification stream is restricted
            val fallbackBytes = try {
                context.resources.openRawResource(R.raw.athan_chime).use { it.readBytes() }
            } catch (_: Exception) { ByteArray(0) }
            return ResolvedAudioData(
                bytes = fallbackBytes,
                mimeType = "audio/mpeg",
                title = "System Notification"
            )
        }

        // 5. System Alarm Ringtone
        if (soundChoice == "system_alarm") {
            try {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val bytes = if (uri != null) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } else null

                if (bytes != null && bytes.isNotEmpty()) {
                    return ResolvedAudioData(
                        bytes = bytes,
                        mimeType = "audio/mpeg",
                        title = "System Alarm"
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading system alarm ringtone: ${e.message}")
            }

            // Fallback to athan
            val fallbackBytes = try {
                context.resources.openRawResource(R.raw.athan_general).use { it.readBytes() }
            } catch (_: Exception) { ByteArray(0) }
            return ResolvedAudioData(
                bytes = fallbackBytes,
                mimeType = "audio/mpeg",
                title = "System Alarm"
            )
        }

        // 6. Full Athan (Fajr or General)
        val resId = if (prayerType == PrayerType.FAJR || prayerType == PrayerType.NEXT_FAJR) {
            R.raw.athan_fajr
        } else {
            R.raw.athan_general
        }

        val athanBytes = try {
            context.resources.openRawResource(resId).use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading bundled athan: ${e.message}", e)
            ByteArray(0)
        }

        return ResolvedAudioData(
            bytes = athanBytes,
            mimeType = "audio/mpeg",
            title = "Athan"
        )
    }

    fun resolveAudio(context: Context, soundChoice: String, prayerType: PrayerType = PrayerType.DHUHR): ResolvedAudio {
        val data = resolveAudioData(context, soundChoice, prayerType)
        return ResolvedAudio(
            streamSupplier = { data.bytes.inputStream() },
            contentLength = data.bytes.size.toLong(),
            mimeType = data.mimeType,
            title = data.title
        )
    }
}
