package com.lolauncher.service

import com.lolauncher.data.models.VersionItem
import com.lolauncher.data.models.VersionJson
import com.lolauncher.data.models.VersionType
import com.lolauncher.util.VersionDisplayNames
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Сервис поддержки OptiFine через BMCLAPI.
 */
class OptiFineService(private val minecraftDir: File) {

    companion object {
        private const val OPTIFINE_VERSION_LIST =
            "https://bmclapi2.bangbang93.com/optifine/versionList"
        private const val OPTIFINE_DOWNLOAD_BASE =
            "https://bmclapi2.bangbang93.com/optifine"
        const val OPTIFINE_PREFIX = "optifine-"
        const val SEP = "__"
    }

    @Serializable
    data class OptiFineVersion(
        val mcversion: String,
        val type: String,
        val patch: String
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * Загружает список версий OptiFine с уникальными ID (включая patch).
     */
    fun fetchOptiFineVersions(): List<VersionItem> {
        return try {
            val request = Request.Builder().url(OPTIFINE_VERSION_LIST).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val versions = json.decodeFromString<List<OptiFineVersion>>(body)
                versions.map { v ->
                    val id = buildVersionId(v.mcversion, v.type, v.patch)
                    VersionItem(
                        id = id,
                        type = VersionType.OPTIFINE,
                        releaseTime = v.mcversion,
                        isInstalled = isOptiFineInstalled(id),
                        modLoader = "optifine",
                        displayName = VersionDisplayNames.format(id, VersionType.OPTIFINE)
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Уникальный ID: optifine-{mc}__{type}__{patch} */
    fun buildVersionId(mcVersion: String, type: String, patch: String): String =
        "$OPTIFINE_PREFIX$mcVersion$SEP$type$SEP$patch"

    /** Парсит mcVersion, type, patch из ID */
    fun parseVersionId(versionId: String): Triple<String, String, String>? {
        if (!versionId.startsWith(OPTIFINE_PREFIX)) return null
        val parts = versionId.removePrefix(OPTIFINE_PREFIX).split(SEP)
        if (parts.size != 3) return null
        return Triple(parts[0], parts[1], parts[2])
    }

    fun isOptiFineInstalled(versionId: String): Boolean {
        val versionDir = File(minecraftDir, "versions/$versionId")
        val jar = File(versionDir, "$versionId.jar")
        val jsonFile = File(versionDir, "$versionId.json")
        val optifineJar = File(versionDir, "OptiFine.jar")
        return jar.exists() && jsonFile.exists() && optifineJar.exists()
    }

    fun downloadOptiFineJar(versionId: String, mcVersion: String, type: String): File {
        val versionDir = File(minecraftDir, "versions/$versionId")
        versionDir.mkdirs()
        val optifineJar = File(versionDir, "OptiFine.jar")
        if (optifineJar.exists()) return optifineJar

        val url = "$OPTIFINE_DOWNLOAD_BASE/$mcVersion/$type"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Ошибка загрузки OptiFine: HTTP ${response.code}")
            }
            val bytes = response.body?.bytes() ?: throw Exception("Пустой ответ OptiFine")
            optifineJar.writeBytes(bytes)
        }
        return optifineJar
    }

    fun buildOptiFineVersionJson(
        versionId: String,
        baseJson: VersionJson,
        optifineJar: File
    ): VersionJson {
        val optifineLibrary = com.lolauncher.data.models.Library(
            name = "optifine:OptiFine:${versionId.removePrefix(OPTIFINE_PREFIX)}",
            downloads = com.lolauncher.data.models.LibraryDownloads(
                artifact = com.lolauncher.data.models.Artifact(
                    path = "optifine/${optifineJar.name}",
                    url = optifineJar.toURI().toString(),
                    sha1 = "",
                    size = optifineJar.length()
                )
            )
        )

        val tweakArgs = listOf(
            kotlinx.serialization.json.JsonPrimitive("--tweakClass"),
            kotlinx.serialization.json.JsonPrimitive("optifine.OptiFineTweaker")
        )

        val gameArgs = if (baseJson.arguments != null) {
            baseJson.arguments.game + tweakArgs
        } else {
            tweakArgs
        }

        return baseJson.copy(
            id = versionId,
            libraries = baseJson.libraries + optifineLibrary,
            arguments = baseJson.arguments?.copy(game = gameArgs)
                ?: com.lolauncher.data.models.Arguments(game = tweakArgs)
        )
    }
}
