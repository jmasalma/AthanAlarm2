package com.example.cast

import android.content.Context
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
        val mimeType: String,
        val title: String
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
                    return ResolvedAudioData(
                        bytes = bytes,
                        mimeType = "audio/mpeg",
                        title = "Custom Audio"
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed reading custom file: ${e.message}")
                }
            }
        }

        if (soundChoice.startsWith("content://")) {
            val uri = Uri.parse(soundChoice)
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    return ResolvedAudioData(
                        bytes = bytes,
                        mimeType = "audio/mpeg",
                        title = "Custom Audio"
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading content URI: ${e.message}")
            }
        }

        if (soundChoice == "chime") {
            try {
                val bytes = context.resources.openRawResource(R.raw.athan_chime).use { it.readBytes() }
                return ResolvedAudioData(
                    bytes = bytes,
                    mimeType = "audio/wav",
                    title = "Gentle Chime"
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading chime: ${e.message}")
            }
        }

        val resId = if (prayerType == PrayerType.FAJR || prayerType == PrayerType.NEXT_FAJR) {
            R.raw.athan_fajr
        } else {
            R.raw.athan_general
        }

        val bytes = try {
            context.resources.openRawResource(resId).use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading bundled athan: ${e.message}", e)
            ByteArray(0)
        }

        return ResolvedAudioData(
            bytes = bytes,
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
