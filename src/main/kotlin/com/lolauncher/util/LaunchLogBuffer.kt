package com.lolauncher.util

import com.lolauncher.data.models.LaunchLogEntry
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Буфер логов запуска для консоли в реальном времени.
 */
object LaunchLogBuffer {

    private val entries = CopyOnWriteArrayList<LaunchLogEntry>()
    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val listeners = CopyOnWriteArrayList<(LaunchLogEntry) -> Unit>()

    fun clear() {
        entries.clear()
    }

    fun add(level: String, message: String) {
        val entry = LaunchLogEntry(
            timestamp = LocalDateTime.now().format(formatter),
            level = level,
            message = message
        )
        entries.add(entry)
        if (entries.size > 5000) entries.removeAt(0)
        listeners.forEach { it(entry) }
        when (level) {
            "ERROR" -> LogService.error(message)
            "WARN" -> LogService.warn(message)
            "GAME" -> LogService.logProcessOutput(message)
            else -> LogService.info(message)
        }
    }

    fun getEntries(): List<LaunchLogEntry> = entries.toList()

    fun getLastLines(count: Int = 30): List<LaunchLogEntry> =
        entries.takeLast(count)

    fun addListener(listener: (LaunchLogEntry) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (LaunchLogEntry) -> Unit) {
        listeners.remove(listener)
    }

    fun asText(): String = entries.joinToString("\n") { "[${it.timestamp}] [${it.level}] ${it.message}" }
}
