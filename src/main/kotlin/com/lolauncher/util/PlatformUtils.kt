package com.lolauncher.util

/**
 * Утилиты для определения текущей операционной системы.
 * Используется при выборе нативных библиотек Minecraft.
 */
object PlatformUtils {

    enum class OS { WINDOWS, LINUX, MACOS, UNKNOWN }

    val currentOS: OS
        get() {
            val os = System.getProperty("os.name").lowercase()
            return when {
                os.contains("win") -> OS.WINDOWS
                os.contains("mac") || os.contains("darwin") -> OS.MACOS
                os.contains("linux") || os.contains("unix") -> OS.LINUX
                else -> OS.UNKNOWN
            }
        }

    /** Имя ОС для правил библиотек Mojang */
    val osName: String
        get() = when (currentOS) {
            OS.WINDOWS -> "windows"
            OS.LINUX -> "linux"
            OS.MACOS -> "osx"
            OS.UNKNOWN -> "windows"
        }

    /** Архитектура процессора */
    val osArch: String
        get() = System.getProperty("os.arch")

    /** Расширение нативных библиотек */
    val nativeExtension: String
        get() = when (currentOS) {
            OS.WINDOWS -> ".dll"
            OS.LINUX -> ".so"
            OS.MACOS -> ".dylib"
            OS.UNKNOWN -> ".dll"
        }

    /** Ключ natives в version JSON */
    val nativeKey: String
        get() = when (currentOS) {
            OS.WINDOWS -> "windows"
            OS.LINUX -> "linux"
            OS.MACOS -> "osx"
            OS.UNKNOWN -> "windows"
        }
}
