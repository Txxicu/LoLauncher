package com.lolauncher.util

import java.io.File
import javax.imageio.ImageIO

/**
 * Локальный аватар пользователя (своё фото вместо скина).
 */
object AvatarStorage {

    private fun avatarFile(): File = File(SettingsManager.launcherDir, "avatar.png")

    fun load(): ByteArray? {
        val file = avatarFile()
        if (!file.exists() || file.length() < 32) return null
        return try {
            file.readBytes()
        } catch (_: Exception) {
            null
        }
    }

    fun saveFromFile(source: File): Boolean {
        return try {
            val image = ImageIO.read(source) ?: return false
            val out = avatarFile()
            out.parentFile?.mkdirs()
            ImageIO.write(image, "png", out)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun clear() {
        try {
            avatarFile().delete()
        } catch (_: Exception) {
        }
    }

    fun hasCustomAvatar(): Boolean = avatarFile().exists()
}
