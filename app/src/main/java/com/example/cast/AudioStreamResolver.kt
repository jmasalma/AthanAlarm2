package com.example.cast

import android.content.Context
import android.net.Uri
import com.example.R
import com.example.model.PrayerType
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

object AudioStreamResolver {

    data class ResolvedAudio(
        val streamSupplier: () -> InputStream?,
        val contentLength: Long,
        val mimeType: String = "audio/mpeg",
        val title: String = "Athan Alarm"
    )

    fun resolveAudio(context: Context, soundChoice: String, prayerType: PrayerType = PrayerType.DHUHR): ResolvedAudio {
        val customFilePath = when {
            soundChoice.startsWith("custom_file:") -> soundChoice.removePrefix("custom_file:")
            soundChoice.startsWith("file://") -> Uri.parse(soundChoice).path
            else -> null
        }
        if (customFilePath != null) {
            val file = File(customFilePath)
            if (file.exists()) {
                return ResolvedAudio(
                    streamSupplier = { FileInputStream(file) },
                    contentLength = file.length(),
                    title = "Custom Audio"
                )
            }
        }

        if (soundChoice.startsWith("content://")) {
            val uri = Uri.parse(soundChoice)
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                val size = pfd?.statSize ?: -1L
                pfd?.close()
                return ResolvedAudio(
                    streamSupplier = { context.contentResolver.openInputStream(uri) },
                    contentLength = size,
                    title = "Custom Audio"
                )
            } catch (_: Exception) {}
        }

        if (soundChoice == "chime") {
            return try {
                val afd = context.resources.openRawResourceFd(R.raw.athan_chime)
                val length = afd?.length ?: -1L
                afd?.close()
                ResolvedAudio(
                    streamSupplier = { context.resources.openRawResource(R.raw.athan_chime) },
                    contentLength = length,
                    mimeType = "audio/wav",
                    title = "Gentle Chime"
                )
            } catch (_: Exception) {
                ResolvedAudio(
                    streamSupplier = { context.resources.openRawResource(R.raw.athan_chime) },
                    contentLength = -1L,
                    mimeType = "audio/wav",
                    title = "Gentle Chime"
                )
            }
        }

        val resId = if (prayerType == PrayerType.FAJR || prayerType == PrayerType.NEXT_FAJR) {
            R.raw.athan_fajr
        } else {
            R.raw.athan_general
        }

        return try {
            val afd = context.resources.openRawResourceFd(resId)
            val length = afd?.length ?: -1L
            afd?.close()
            ResolvedAudio(
                streamSupplier = { context.resources.openRawResource(resId) },
                contentLength = length,
                mimeType = "audio/mpeg",
                title = if (resId == R.raw.athan_fajr) "Fajr Athan" else "Full Athan"
            )
        } catch (_: Exception) {
            ResolvedAudio(
                streamSupplier = { context.resources.openRawResource(resId) },
                contentLength = -1L,
                mimeType = "audio/mpeg",
                title = "Full Athan"
            )
        }
    }
}
