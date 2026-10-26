package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.cast.AudioStreamResolver
import com.example.cast.LocalHttpAudioServer
import com.example.model.PrayerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AudioStreamResolverTest {

    @Test
    fun testResolveBundledChimeAudio() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolved = AudioStreamResolver.resolveAudio(context, "chime")

        assertEquals("Gentle Chime", resolved.title)
        assertEquals("audio/mpeg", resolved.mimeType)
        val stream = resolved.streamSupplier.invoke()
        assertNotNull(stream)
        stream?.close()
    }

    @Test
    fun testResolveBundledAthanAudio() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolvedGeneral = AudioStreamResolver.resolveAudio(context, "athan", PrayerType.DHUHR)
        assertEquals("Athan", resolvedGeneral.title)
        assertEquals("audio/mpeg", resolvedGeneral.mimeType)
        val streamGeneral = resolvedGeneral.streamSupplier.invoke()
        assertNotNull(streamGeneral)
        streamGeneral?.close()

        val resolvedFajr = AudioStreamResolver.resolveAudio(context, "athan", PrayerType.FAJR)
        assertEquals("Athan", resolvedFajr.title)
        assertEquals("audio/mpeg", resolvedFajr.mimeType)
        val streamFajr = resolvedFajr.streamSupplier.invoke()
        assertNotNull(streamFajr)
        streamFajr?.close()
    }

    @Test
    fun testResolveSystemNotificationAndAlarm() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolvedNotif = AudioStreamResolver.resolveAudio(context, "system_notification")
        assertEquals("System Notification", resolvedNotif.title)
        assertEquals("audio/mpeg", resolvedNotif.mimeType)

        val resolvedAlarm = AudioStreamResolver.resolveAudio(context, "system_alarm")
        assertEquals("System Alarm", resolvedAlarm.title)
        assertEquals("audio/mpeg", resolvedAlarm.mimeType)
    }

    @Test
    fun testLocalHttpAudioServerStartAndStop() {
        val server = LocalHttpAudioServer()
        val dummyData = "dummy audio data".toByteArray()

        val url = server.startWithBytes(
            bytes = dummyData,
            mimeType = "audio/mpeg"
        )

        assertNotNull(url)
        assertTrue("URL must start with http://", url.startsWith("http://"))
        assertTrue("URL must end with /athan.mp3", url.endsWith("/athan.mp3"))

        server.stop()
    }
}
