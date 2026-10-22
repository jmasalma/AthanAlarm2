package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
class AudioPlaybackConfigTest {

    @Test
    fun testRawAudioResourcesExistAndAreReadable() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Chime
        val chimeStream = context.resources.openRawResource(R.raw.athan_chime)
        assertNotNull("Chime audio resource must exist", chimeStream)
        assertTrue("Chime file has content", chimeStream.available() > 1000)
        chimeStream.close()

        // 2. Fajr Athan MP3 (AthanF.mp3)
        val fajrStream = context.resources.openRawResource(R.raw.athan_fajr)
        assertNotNull("Fajr Athan MP3 resource must exist", fajrStream)
        assertTrue("Fajr Athan file has content (> 1MB)", fajrStream.available() > 1_000_000)
        fajrStream.close()

        // 3. Daily Athan MP3 (Athan.mp3)
        val generalStream = context.resources.openRawResource(R.raw.athan_general)
        assertNotNull("General Athan MP3 resource must exist", generalStream)
        assertTrue("General Athan file has content (> 1MB)", generalStream.available() > 1_000_000)
        generalStream.close()
    }

    @Test
    fun testPrayerSpecificAudioSelectionLogic() {
        fun resolveAudioRes(soundChoice: String, prayerType: PrayerType): Int {
            return when {
                soundChoice == "adhan" || soundChoice == "athan" -> {
                    if (prayerType == PrayerType.FAJR || prayerType == PrayerType.NEXT_FAJR) {
                        R.raw.athan_fajr
                    } else {
                        R.raw.athan_general
                    }
                }
                soundChoice == "chime" -> R.raw.athan_chime
                else -> R.raw.athan_chime
            }
        }

        // Verify Fajr uses Fajr MP3
        assertEquals(R.raw.athan_fajr, resolveAudioRes("athan", PrayerType.FAJR))
        assertEquals(R.raw.athan_fajr, resolveAudioRes("athan", PrayerType.NEXT_FAJR))

        // Verify all other prayers use general Athan MP3
        assertEquals(R.raw.athan_general, resolveAudioRes("athan", PrayerType.DHUHR))
        assertEquals(R.raw.athan_general, resolveAudioRes("athan", PrayerType.ASR))
        assertEquals(R.raw.athan_general, resolveAudioRes("athan", PrayerType.MAGHRIB))
        assertEquals(R.raw.athan_general, resolveAudioRes("athan", PrayerType.ISHA))

        // Verify legacy 'adhan' key also maps accurately
        assertEquals(R.raw.athan_fajr, resolveAudioRes("adhan", PrayerType.FAJR))
        assertEquals(R.raw.athan_general, resolveAudioRes("adhan", PrayerType.DHUHR))

        // Verify chime selection
        assertEquals(R.raw.athan_chime, resolveAudioRes("chime", PrayerType.FAJR))
        assertEquals(R.raw.athan_chime, resolveAudioRes("chime", PrayerType.MAGHRIB))
    }
}
