package com.lolauncher.data.api

import com.lolauncher.data.models.MojangProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * API Ely.by — authserver и skinsystem.
 */
object ElyByApi {

    const val AUTH_BASE = "https://authserver.ely.by"
    const val SKIN_BASE = "http://skinsystem.ely.by"

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Serializable
    data class ElyProfile(
        val id: String = "",
        val name: String = ""
    )

    fun profileUrl(nickname: String): String = "$SKIN_BASE/profile/$nickname"
    fun texturesUrl(nickname: String): String = "$SKIN_BASE/textures/$nickname"
    fun skinUrl(nickname: String): String = "$SKIN_BASE/skins/$nickname.png"
    fun cloakUrl(nickname: String): String = "$SKIN_BASE/cloaks/$nickname.png"
    fun renderUrl(nickname: String, size: Int = 256): String =
        "https://skins.ely.by/render/$nickname?size=$size&scale=10"

    /** GET /api/users/profiles/minecraft/{username} */
    fun fetchUuid(username: String): MojangProfile? {
        val request = Request.Builder()
            .url("$AUTH_BASE/api/users/profiles/minecraft/$username")
            .get()
            .header("User-Agent", "LoLauncher/1.0")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.code == 204 || response.code == 404 || !response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val profile = json.decodeFromString<ElyProfile>(body)
                MojangProfile(id = profile.id, name = profile.name.ifBlank { username })
            }
        } catch (_: Exception) {
            null
        }
    }

    fun downloadBytes(url: String): ByteArray? {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", "LoLauncher/1.0")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                if (bytes.size < 32) null else bytes
            }
        } catch (_: Exception) {
            null
        }
    }

    fun downloadSkin(nickname: String): ByteArray? = downloadBytes(skinUrl(nickname))
    fun downloadCloak(nickname: String): ByteArray? = downloadBytes(cloakUrl(nickname))
    fun downloadPreview(nickname: String): ByteArray? = downloadBytes(renderUrl(nickname))
}
