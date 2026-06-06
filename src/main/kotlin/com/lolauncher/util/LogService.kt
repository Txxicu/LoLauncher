package com.lolauncher.util

import com.lolauncher.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Сервис логирования ошибок лаунчера.
 */
object LogService {

    private val logDir: File
        get() = File(SettingsManager.launcherDir, "logs").also { it.mkdirs() }

    private val logFile: File
        get() = File(logDir, "launcher.log")

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    val APP_NAME: String get() = BuildConfig.APP_NAME
    val APP_VERSION: String get() = BuildConfig.VERSION_NAME

    fun info(message: String) = write("INFO", message)
    fun warn(message: String) = write("WARN", message)

    fun error(message: String, throwable: Throwable? = null) {
        val fullMessage = if (throwable != null) {
            "$message\n${stackTraceToString(throwable)}"
        } else message
        write("ERROR", fullMessage)
    }

    fun logLaunchCommand(versionId: String, command: List<String>) {
        info("Запуск Minecraft $versionId")
        info("Команда: ${command.joinToString(" ")}")
    }

    fun logProcessOutput(line: String) = write("GAME", line)
    fun getLogFilePath(): String = logFile.absolutePath
    fun getGameOutputLogPath(): String = File(logDir, "game-output.log").absolutePath
    fun getLogsDirectory(): String = logDir.absolutePath

    fun readGameOutputLog(): String {
        val file = File(logDir, "game-output.log")
        return if (file.exists()) file.readText() else ""
    }

    private fun write(level: String, message: String) {
        try {
            val timestamp = LocalDateTime.now().format(formatter)
            logFile.appendText("[$timestamp] [$level] $message\n")
        } catch (e: Exception) {
            System.err.println("Не удалось записать лог: ${e.message}")
        }
    }

    private fun stackTraceToString(throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }
}
