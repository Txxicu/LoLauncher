package com.lolauncher.util

import com.lolauncher.data.models.LauncherSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Менеджер настроек лаунчера.
 * Сохраняет и загружает пользовательские настройки в JSON-файл.
 */
object SettingsManager {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /** Папка данных лаунчера: %APPDATA%/LoLauncher */
    val launcherDir: File by lazy {
        val appData = System.getenv("APPDATA")
            ?: System.getProperty("user.home")
        File(appData, "LoLauncher").also { it.mkdirs() }
    }

    private val settingsFile: File
        get() = File(launcherDir, "settings.json")

    /**
     * Возвращает путь к папке Minecraft по умолчанию.
     */
    fun defaultMinecraftDir(): String {
        val appData = System.getenv("APPDATA")
            ?: System.getProperty("user.home")
        return File(appData, ".minecraft").absolutePath
    }

    /**
     * Загружает настройки из файла или возвращает значения по умолчанию.
     */
    fun load(): LauncherSettings {
        return try {
            if (settingsFile.exists()) {
                val content = settingsFile.readText()
                val settings = json.decodeFromString<LauncherSettings>(content)
                // Если путь к Minecraft пуст — подставляем дефолтный
                if (settings.minecraftDir.isBlank()) {
                    settings.copy(minecraftDir = defaultMinecraftDir())
                } else {
                    settings
                }
            } else {
                LauncherSettings(minecraftDir = defaultMinecraftDir())
            }
        } catch (e: Exception) {
            println("Ошибка загрузки настроек: ${e.message}")
            LauncherSettings(minecraftDir = defaultMinecraftDir())
        }
    }

    /**
     * Сохраняет настройки в JSON-файл.
     */
    fun save(settings: LauncherSettings) {
        try {
            settingsFile.writeText(json.encodeToString(settings))
        } catch (e: Exception) {
            println("Ошибка сохранения настроек: ${e.message}")
        }
    }
}
