package com.lolauncher.service

import com.lolauncher.data.models.VersionJson
import com.lolauncher.util.LogService
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Установка и загрузка профилей Fabric.
 */
class FabricService {

    companion object {
        private const val PROFILE_URL =
            "https://meta.fabricmc.net/v2/versions/loader/%s/%s/profile/json"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    fun parseFabricVersionId(versionId: String): Pair<String, String>? {
        if (!versionId.startsWith("fabric-loader-")) return null
        val rest = versionId.removePrefix("fabric-loader-")
        val loader = rest.substringBeforeLast("-")
        val game = rest.substringAfterLast("-")
        return if (loader.isNotBlank() && game.isNotBlank()) loader to game else null
    }

    fun buildFabricVersionId(loaderVersion: String, gameVersion: String): String =
        "fabric-loader-$loaderVersion-$gameVersion"

    fun fetchFabricProfile(gameVersion: String, loaderVersion: String): VersionJson {
        val url = PROFILE_URL.format(gameVersion, loaderVersion)
        LogService.info("Загрузка Fabric profile: $url")
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Fabric profile недоступен (HTTP ${response.code})")
            }
            val body = response.body?.string() ?: throw Exception("Пустой ответ Fabric")
            return json.decodeFromString(VersionJson.serializer(), body)
        }
    }

    fun resolveGameVersion(versionId: String): String =
        parseFabricVersionId(versionId)?.second ?: versionId
}
