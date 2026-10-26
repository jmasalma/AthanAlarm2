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

    private const val MAX_LOGS = 500
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

    private fun log(level: LogLevel, category: LogCategory, tag: String, message: String) {
        val entry = LogEntry(
            timestampMillis = System.currentTimeMillis(),
            level = level,
            category = category,
            tag = tag,
            message = message
        )
        logDeque.addLast(entry)
        while (logDeque.size > MAX_LOGS) {
            logDeque.pollFirst()
        }
        _logUpdates.tryEmit(Unit)
    }

    fun getLogs(
        categoryFilter: LogCategory = LogCategory.ALL,
        onlyErrors: Boolean = false
    ): List<LogEntry> {
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
            return "No logs recorded for category: ${categoryFilter.displayName}"
        }
        val sb = StringBuilder()
        sb.append("=== Athan Alarm Diagnostic Logs (${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}) ===\n")
        sb.append("Filter: ${categoryFilter.displayName} | Total: ${entries.size}\n\n")
        entries.forEach { sb.append(it.toFormattedLine()).append("\n") }
        return sb.toString()
    }

    fun clear() {
        logDeque.clear()
        _logUpdates.tryEmit(Unit)
    }
}
