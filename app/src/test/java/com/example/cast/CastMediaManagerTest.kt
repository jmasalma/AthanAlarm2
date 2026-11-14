package com.example.cast

import com.google.android.gms.cast.MediaStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CastMediaManagerTest {

    private val routes = listOf(
        CastRouteCandidate("DEFAULT_ROUTE", "Phone", null, isDefault = true, matchesSelector = false, hasCastDevice = false),
        CastRouteCandidate("route-tv", "Living Room TV", "Chromecast", isDefault = false, matchesSelector = true, hasCastDevice = true),
        CastRouteCandidate("route-group", "Home group", "Google Cast Group", isDefault = false, matchesSelector = true, hasCastDevice = true),
        CastRouteCandidate("route-kitchen", "Kitchen speaker", "Nest Audio", isDefault = false, matchesSelector = true, hasCastDevice = true),
        CastRouteCandidate("route-kitchen-member", "Kitchen speaker", null, isDefault = false, matchesSelector = true, hasCastDevice = true),
        CastRouteCandidate("route-no-dev", "Ghost Speaker", null, isDefault = false, matchesSelector = true, hasCastDevice = false)
    )

    @Test
    fun `isValidCastRouteId rejects default and member routes`() {
        assertFalse(CastMediaManager.isValidCastRouteId("DEFAULT_ROUTE", isDefault = true))
        assertFalse(CastMediaManager.isValidCastRouteId("abc-member", isDefault = false))
        assertFalse(CastMediaManager.isValidCastRouteId("abc:member", isDefault = false))
        assertTrue(CastMediaManager.isValidCastRouteId("route-kitchen", isDefault = false))
    }

    @Test
    fun `findBestTargetRoute matches configured target without hijacking TV when target missing`() {
        // When Kitchen speaker is temporarily absent from initial cache, do NOT fall back to Living Room TV
        val withoutKitchen = routes.filter { it.id != "route-kitchen" }
        val result = CastMediaManager.findBestTargetRoute(
            routes = withoutKitchen,
            targetIds = listOf("route-kitchen"),
            targetNames = listOf("Kitchen speaker")
        )
        assertNull(result)
    }

    @Test
    fun `findBestTargetRoute matches target by name even with preset id`() {
        val result = CastMediaManager.findBestTargetRoute(
            routes = routes,
            targetIds = listOf("preset_home_group"),
            targetNames = listOf("Home group")
        )
        assertEquals("route-group", result?.id)
    }

    @Test
    fun `findBestTargetRoute matches target by id`() {
        val result = CastMediaManager.findBestTargetRoute(
            routes = routes,
            targetIds = listOf("route-kitchen"),
            targetNames = listOf("Kitchen speaker")
        )
        assertEquals("route-kitchen", result?.id)
    }

    @Test
    fun `findFallbackSpeakerRoute skips failed group and TV to select active speaker`() {
        val fallback = CastMediaManager.findFallbackSpeakerRoute(
            routes = routes,
            excludeIds = setOf("route-group")
        )
        assertEquals("route-kitchen", fallback?.id)
    }

    @Test
    fun `shouldTreatStatusAsCompleted only triggers after load on idle with non-none reason`() {
        assertFalse(CastMediaManager.shouldTreatStatusAsCompleted(false, MediaStatus.PLAYER_STATE_IDLE, MediaStatus.IDLE_REASON_FINISHED))
        assertFalse(CastMediaManager.shouldTreatStatusAsCompleted(true, MediaStatus.PLAYER_STATE_PLAYING, MediaStatus.IDLE_REASON_NONE))
        assertFalse(CastMediaManager.shouldTreatStatusAsCompleted(true, MediaStatus.PLAYER_STATE_IDLE, MediaStatus.IDLE_REASON_NONE))
        assertTrue(CastMediaManager.shouldTreatStatusAsCompleted(true, MediaStatus.PLAYER_STATE_IDLE, MediaStatus.IDLE_REASON_FINISHED))
    }
}
