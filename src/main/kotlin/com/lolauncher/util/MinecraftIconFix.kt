package com.lolauncher.util

import java.io.File
import java.util.Base64

/**
 * Создаёт заглушки icon_16x16.png / icon_32x32.png, чтобы клиент не падал с FileNotFoundException.
 */
object MinecraftIconFix {

    private val MINI_PNG: ByteArray by lazy {
        Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
        )
    }

    fun ensureIcons(minecraftDir: File) {
        val iconsDir = File(minecraftDir, "icons")
        iconsDir.mkdirs()
        listOf("icon_16x16.png", "icon_32x32.png").forEach { name ->
            val file = File(iconsDir, name)
            if (!file.exists()) {
                try {
                    file.writeBytes(MINI_PNG)
                } catch (_: Exception) {
                }
            }
        }
    }
}
