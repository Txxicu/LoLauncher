package com.lolauncher.util

import java.io.File
import java.nio.file.Paths

/**
 * Утилита перезапуска лаунчера.
 * Используется при смене темы для применения изменений.
 */
object AppRestart {

    /**
     * Перезапускает лаунчер.
     * Пробует gradlew run (режим разработки) или java -jar (собранный дистрибутив).
     */
    fun restart(onError: (String) -> Unit) {
        try {
            val userDir = System.getProperty("user.dir")
            val gradlewBat = File(userDir, "gradlew.bat")
            val gradlewSh = File(userDir, "gradlew")

            when {
                gradlewBat.exists() -> {
                    LogService.info("Перезапуск через gradlew.bat")
                    ProcessBuilder("cmd", "/c", "start", "", gradlewBat.absolutePath, "run")
                        .directory(File(userDir))
                        .start()
                }
                gradlewSh.exists() -> {
                    LogService.info("Перезапуск через gradlew")
                    ProcessBuilder(gradlewSh.absolutePath, "run")
                        .directory(File(userDir))
                        .start()
                }
                else -> {
                    val javaBin = Paths.get(System.getProperty("java.home"), "bin", "java").toString()
                    val classPath = System.getProperty("java.class.path")
                    LogService.info("Перезапуск через java -cp")
                    ProcessBuilder(javaBin, "-cp", classPath, "com.lolauncher.MainKt")
                        .start()
                }
            }

            // Завершаем текущий процесс
            kotlin.system.exitProcess(0)
        } catch (e: Exception) {
            LogService.error("Ошибка перезапуска лаунчера", e)
            onError("Не удалось перезапустить: ${e.message}")
        }
    }
}
