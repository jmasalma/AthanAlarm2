package com.example.util

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

enum class LogLevel {
    INFO,
    WARN,
    ERROR
}

enum class LogCategory(val displayName: String) {
    ALL("All"),
    CAST("Cast"),
    ALARM("Alarm"),
    SERVER("Server"),
    SYSTEM("System")
}

data class LogEntry(
    val timestampMillis: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val category: LogCategory,
    val tag: String,
    val message: String
) {
    fun formattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        return sdf.format(Date(timestampMillis))
    }

    fun toFormattedLine(): String {
        val levelChar = when (level) {
            LogLevel.INFO -> "I"
            LogLevel.WARN -> "W"
            LogLevel.ERROR -> "E"
        }
        return "[${formattedTime()}] [$levelChar] [${category.name}] $tag: $message"
    }
}

object AppLogger {

    const val MAX_LOGS = 250
    const val RETENTION_MILLIS = 24 * 60 * 60 * 1000L // 24-hour expiration limit
    const val MAX_MESSAGE_LENGTH = 500

    private val logDeque = ConcurrentLinkedDeque<LogEntry>()

    private val _logUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val logUpdates: SharedFlow<Unit> = _logUpdates.asSharedFlow()

    fun i(category: LogCategory, tag: String, message: String) {
        log(LogLevel.INFO, category, tag, message)
        Log.i(tag, "[$category] $message")
    }

    fun w(category: LogCategory, tag: String, message: String) {
        log(LogLevel.WARN, category, tag, message)
        Log.w(tag, "[$category] $message")
    }

    fun e(category: LogCategory, tag: String, message: String, throwable: Throwable? = null) {
        val fullMessage = if (throwable != null) "$message (${throwable.message})" else message
        log(LogLevel.ERROR, category, tag, fullMessage)
        if (throwable != null) {
            Log.e(tag, "[$category] $fullMessage", throwable)
        } else {
            Log.e(tag, "[$category] $fullMessage")
        }
    }

    /**
     * Purges entries that are older than the 24-hour retention limit or exceed MAX_LOGS.
     */
    fun pruneExpiredLogs(now: Long = System.currentTimeMillis()) {
        val cutoff = now - RETENTION_MILLIS
        while (logDeque.isNotEmpty() && (logDeque.peekFirst()?.timestampMillis ?: Long.MAX_VALUE) < cutoff) {
            logDeque.pollFirst()
        }
        while (logDeque.size > MAX_LOGS) {
            logDeque.pollFirst()
        }
    }

    private fun log(
        level: LogLevel,
        category: LogCategory,
        tag: String,
        message: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val truncatedMessage = if (message.length > MAX_MESSAGE_LENGTH) {
            message.take(MAX_MESSAGE_LENGTH) + "..."
        } else {
            message
        }
        val entry = LogEntry(
            timestampMillis = timestamp,
            level = level,
            category = category,
            tag = tag,
            message = truncatedMessage
        )
        logDeque.addLast(entry)
        pruneExpiredLogs(timestamp)
        _logUpdates.tryEmit(Unit)
    }

    // Visible for testing to inject custom timestamps
    internal fun addEntryForTesting(entry: LogEntry) {
        logDeque.addLast(entry)
        pruneExpiredLogs(System.currentTimeMillis())
        _logUpdates.tryEmit(Unit)
    }

    fun getLogs(
        categoryFilter: LogCategory = LogCategory.ALL,
        onlyErrors: Boolean = false
    ): List<LogEntry> {
        pruneExpiredLogs()
        val list = logDeque.toList()
        return list.filter { entry ->
            val matchCategory = categoryFilter == LogCategory.ALL || entry.category == categoryFilter
            val matchLevel = !onlyErrors || entry.level == LogLevel.ERROR
            matchCategory && matchLevel
        }
    }

    fun getFormattedText(
        categoryFilter: LogCategory = LogCategory.ALL,
        onlyErrors: Boolean = false
    ): String {
        val entries = getLogs(categoryFilter, onlyErrors)
        if (entries.isEmpty()) {
            return "No logs recorded for category: ${categoryFilter.displayName} (Auto-capped to max $MAX_LOGS entries or 24h retention)"
        }
        val sb = StringBuilder()
        sb.append("=== Athan Alarm Diagnostic Logs (${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}) ===\n")
        sb.append("Filter: ${categoryFilter.displayName} | Total: ${entries.size} (Capped: max $MAX_LOGS entries, 24h retention)\n\n")
        entries.forEach { sb.append(it.toFormattedLine()).append("\n") }
        return sb.toString()
    }

    fun clear() {
        logDeque.clear()
        _logUpdates.tryEmit(Unit)
    }
}
