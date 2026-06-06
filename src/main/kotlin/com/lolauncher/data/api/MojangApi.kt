package com.lolauncher.data.api

import com.lolauncher.data.models.*
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Клиент для работы с официальными API Mojang и связанными сервисами.
 * Загружает манифест версий, JSON версий, профили игроков и скины.
 */
object MojangApi {

    private const val VERSION_MANIFEST_URL =
        "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"
    private const val PROFILE_URL = "https://api.mojang.com/users/profiles/minecraft/"
    private const val SESSION_URL = "https://sessionserver.mojang.com/session/minecraft/profile/"

    /** Forge metadata */
    private const val FORGE_PROMOTIONS_URL =
        "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json"

    /** Fabric metadata */
    private const val FABRIC_LOADER_URL =
        "https://meta.fabricmc.net/v2/versions/loader"
    private const val FABRIC_GAME_URL =
        "https://meta.fabricmc.net/v2/versions/game"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Загружает полный манифест версий Minecraft.
     */
    fun fetchVersionManifest(): VersionManifest {
        val request = Request.Builder().url(VERSION_MANIFEST_URL).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Ошибка загрузки манифеста: HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw Exception("Пустой ответ манифеста")
            return json.decodeFromString(VersionManifest.serializer(), body)
        }
    }

    /**
     * Загружает JSON конкретной версии по URL из манифеста.
     */
    fun fetchVersionJson(url: String): VersionJson {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Ошибка загрузки версии: HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw Exception("Пустой ответ версии")
            return json.decodeFromString(VersionJson.serializer(), body)
        }
    }

    /**
     * Получает UUID игрока по нику (Mojang API).
     * Возвращает null, если игрок не найден.
     */
    fun fetchPlayerProfile(username: String): MojangProfile? {
        val request = Request.Builder()
            .url("$PROFILE_URL$username")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 204 || response.code == 404) return null
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            return json.decodeFromString(MojangProfile.serializer(), body)
        }
    }

    /**
     * Загружает список версий Fabric loader.
     */
    fun fetchFabricLoaders(): List<String> {
        return try {
            val request = Request.Builder().url(FABRIC_LOADER_URL).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                // Простой парсинг — берём stable версии
                val pattern = """"version"\s*:\s*"([^"]+)"""".toRegex()
                pattern.findAll(body).map { it.groupValues[1] }.distinct().take(20).toList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Загружает список поддерживаемых Fabric game versions.
     */
    fun fetchFabricGameVersions(): List<String> {
        return try {
            val request = Request.Builder().url(FABRIC_GAME_URL).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val pattern = """"version"\s*:\s*"([^"]+)"""".toRegex()
                pattern.findAll(body).map { it.groupValues[1] }.distinct().toList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Универсальная загрузка файла по URL.
     */
    fun downloadFile(url: String, onProgress: ((Long, Long) -> Unit)? = null): ByteArray {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Ошибка загрузки: HTTP ${response.code} — $url")
            }
            val body = response.body ?: throw Exception("Пустой ответ")
            val total = body.contentLength()
            val buffer = body.byteStream().readBytes()
            onProgress?.invoke(buffer.size.toLong(), total)
            return buffer
        }
    }
}
