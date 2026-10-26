package com.example

import com.example.util.AppLogger
import com.example.util.LogCategory
import com.example.util.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppLoggerTest {

    @Before
    fun setup() {
        AppLogger.clear()
    }

    @Test
    fun testAppLoggerBasicLogging() {
        AppLogger.i(LogCategory.CAST, "CastTag", "Cast device connected")
        AppLogger.w(LogCategory.ALARM, "AlarmTag", "Alarm scheduled in past, adjusting")
        AppLogger.e(LogCategory.SERVER, "ServerTag", "HTTP server port binding failed", RuntimeException("Port in use"))

        val allLogs = AppLogger.getLogs(LogCategory.ALL)
        assertEquals(3, allLogs.size)

        assertEquals(LogLevel.INFO, allLogs[0].level)
        assertEquals(LogCategory.CAST, allLogs[0].category)
        assertEquals("Cast device connected", allLogs[0].message)

        assertEquals(LogLevel.WARN, allLogs[1].level)
        assertEquals(LogCategory.ALARM, allLogs[1].category)

        assertEquals(LogLevel.ERROR, allLogs[2].level)
        assertEquals(LogCategory.SERVER, allLogs[2].category)
        assertTrue(allLogs[2].message.contains("Port in use"))
    }

    @Test
    fun testAppLoggerCategoryFiltering() {
        AppLogger.i(LogCategory.CAST, "Cast", "Cast log 1")
        AppLogger.i(LogCategory.ALARM, "Alarm", "Alarm log 1")
        AppLogger.i(LogCategory.SERVER, "Server", "Server log 1")
        AppLogger.i(LogCategory.CAST, "Cast", "Cast log 2")

        val castLogs = AppLogger.getLogs(LogCategory.CAST)
        assertEquals(2, castLogs.size)
        assertTrue(castLogs.all { it.category == LogCategory.CAST })

        val alarmLogs = AppLogger.getLogs(LogCategory.ALARM)
        assertEquals(1, alarmLogs.size)
        assertEquals("Alarm log 1", alarmLogs[0].message)

        val serverLogs = AppLogger.getLogs(LogCategory.SERVER)
        assertEquals(1, serverLogs.size)
    }

    @Test
    fun testAppLoggerErrorOnlyFiltering() {
        AppLogger.i(LogCategory.CAST, "Cast", "Normal info")
        AppLogger.w(LogCategory.CAST, "Cast", "Warning message")
        AppLogger.e(LogCategory.CAST, "Cast", "Critical error")

        val errorsOnly = AppLogger.getLogs(categoryFilter = LogCategory.ALL, onlyErrors = true)
        assertEquals(1, errorsOnly.size)
        assertEquals(LogLevel.ERROR, errorsOnly[0].level)
        assertEquals("Critical error", errorsOnly[0].message)
    }

    @Test
    fun testAppLoggerCapacityCappedAt500() {
        for (i in 1..550) {
            AppLogger.i(LogCategory.SYSTEM, "TestTag", "Log entry #$i")
        }

        val allLogs = AppLogger.getLogs(LogCategory.ALL)
        assertEquals(500, allLogs.size)

        // Oldest (1..50) should have been evicted; first element should be #51
        assertEquals("Log entry #51", allLogs.first().message)
        assertEquals("Log entry #550", allLogs.last().message)
    }

    @Test
    fun testAppLoggerFormattedTextOutput() {
        AppLogger.i(LogCategory.CAST, "CastMgr", "Playing prayer on Google Home")

        val formattedText = AppLogger.getFormattedText(LogCategory.CAST)
        assertTrue("Formatted text should contain tag", formattedText.contains("CastMgr"))
        assertTrue("Formatted text should contain category", formattedText.contains("[CAST]"))
        assertTrue("Formatted text should contain level char", formattedText.contains("[I]"))
        assertTrue("Formatted text should contain message", formattedText.contains("Playing prayer on Google Home"))
    }

    @Test
    fun testAppLoggerClear() {
        AppLogger.i(LogCategory.CAST, "Tag", "Message")
        assertEquals(1, AppLogger.getLogs().size)

        AppLogger.clear()
        assertTrue(AppLogger.getLogs().isEmpty())
    }
}
