package com.lolauncher.service

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Загрузка скинов через Ely.by (альтернатива Crafatar).
 */
object ElySkinService {

    private const val AVATAR_BASE = "https://skinsystem.ely.by/avatars"
    private const val HEAD_BASE = "https://skinsystem.ely.by/head"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * URL аватара на Ely.by.
     */
    fun getAvatarUrl(uuid: String, size: Int = 128): String {
        val clean = uuid.replace("-", "")
        return "$AVATAR_BASE/$clean?size=$size"
    }

    /**
     * Загружает байты аватара, пробуя несколько URL Ely.by.
     */
    fun downloadAvatar(uuid: String, username: String, size: Int = 128): ByteArray? {
        val cleanUuid = uuid.replace("-", "")
        val urls = listOf(
            "$AVATAR_BASE/$cleanUuid?size=$size",
            "$AVATAR_BASE/$uuid?size=$size",
            "$HEAD_BASE/$cleanUuid?size=$size",
            "https://cdn.ely.by/render/head/$username?size=$size&scale=10"
        )

        for (url in urls) {
            try {
                val bytes = downloadUrl(url)
                if (bytes.isNotEmpty()) return bytes
            } catch (_: Exception) { }
        }
        return null
    }

    private fun downloadUrl(url: String): ByteArray {
        val request = Request.Builder().url(url).get()
            .header("User-Agent", "LoLauncher/1.0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            return response.body?.bytes() ?: throw Exception("Пустой ответ")
        }
    }
}
