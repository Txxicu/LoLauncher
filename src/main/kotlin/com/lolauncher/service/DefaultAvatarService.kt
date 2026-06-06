package com.lolauncher.service

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Дефолтная аватарка профиля (SkinMC).
 */
object DefaultAvatarService {

    const val DEFAULT_AVATAR_URL =
        "https://skinmc.net/api/v1/face/profile/c06f8906-4c8a-4911-9c29-ea1dbd1aab82/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedBytes: ByteArray? = null

    fun loadDefault(): ByteArray? {
        cachedBytes?.let { return it }
        return download(DEFAULT_AVATAR_URL)?.also { cachedBytes = it }
    }

    fun download(url: String): ByteArray? {
        return try {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "LoLauncher/1.0")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                if (bytes.size < 32) null else bytes
            }
        } catch (_: Exception) {
            null
        }
    }

    fun clearCache() {
        cachedBytes = null
    }
}
