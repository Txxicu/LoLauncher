package com.lolauncher.service

import com.lolauncher.data.models.VersionJson
import com.lolauncher.util.LogService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/**
 * Установка и загрузка профилей Minecraft Forge.
 *
 * На maven.minecraftforge.net **нет** отдельного `forge-{version}.json` (HTTP 404).
 * Актуальный профиль лежит в `version.json` внутри `forge-{version}-installer.jar`.
 */
class ForgeService {

    companion object {
        private const val FORGE_PROMOTIONS =
            "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json"
        private const val FORGE_MAVEN_BASE = "https://maven.minecraftforge.net/net/minecraftforge/forge"
        private const val FORGE_BMCL_INSTALLER =
            "https://bmclapi2.bangbang93.com/forge/download/%s"
        private const val FORGE_BMCL_VERSIONS =
            "https://bmclapi2.bangbang93.com/forge/minecraft/%s"
        private const val INSTALLER_VERSION_JSON = "version.json"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** Полная Maven-версия: 1.12.2-14.23.5.2859 */
    fun forgeMavenVersion(mcVersion: String, forgeVersion: String): String =
        "$mcVersion-$forgeVersion"

    /** Рекомендуемая версия Forge для MC, например 14.23.5.2860 */
    fun getRecommendedForgeVersion(mcVersion: String): String {
        fetchPromotions()[mcVersion]?.get("recommended")?.let { return it }

        try {
            val request = Request.Builder().url(FORGE_BMCL_VERSIONS.format(mcVersion)).get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use
                    val versions = json.decodeFromString<List<ForgeBmclEntry>>(body)
                    versions.firstOrNull { it.mcversion == mcVersion }?.version?.let { return it }
                }
            }
        } catch (e: Exception) {
            LogService.warn("BMCL Forge API: ${e.message}")
        }

        throw Exception("Не найдена рекомендуемая версия Forge для Minecraft $mcVersion")
    }

    /**
     * Загружает Forge version JSON.
     * 1) installer.jar с Maven → version.json внутри архива
     * 2) зеркало BMCL (installer)
     * Старый URL `forge-{version}.json` на Maven не используется — всегда 404.
     */
    fun fetchForgeVersionJson(mcVersion: String, forgeVersion: String): VersionJson {
        val mavenVersion = forgeMavenVersion(mcVersion, forgeVersion)
        val errors = mutableListOf<String>()

        val mavenInstallerUrl = installerUrl(mavenVersion, mirror = false)
        try {
            return fetchVersionJsonFromInstaller(mavenInstallerUrl)
        } catch (e: Exception) {
            errors.add("Maven: ${e.message}")
            LogService.warn("Forge Maven installer: ${e.message}")
        }

        val bmclInstallerUrl = FORGE_BMCL_INSTALLER.format(mavenVersion)
        try {
            return fetchVersionJsonFromInstaller(bmclInstallerUrl)
        } catch (e: Exception) {
            errors.add("BMCL: ${e.message}")
            LogService.warn("Forge BMCL installer: ${e.message}")
        }

        throw Exception(
            "Не удалось получить профиль Forge $mavenVersion.\n" +
                "Проверьте версию и интернет.\n" +
                errors.joinToString("\n") { "• $it" } +
                "\n\nОжидаемый installer: $mavenInstallerUrl"
        )
    }

    private fun installerUrl(mavenVersion: String, mirror: Boolean): String =
        if (mirror) {
            FORGE_BMCL_INSTALLER.format(mavenVersion)
        } else {
            "$FORGE_MAVEN_BASE/$mavenVersion/forge-$mavenVersion-installer.jar"
        }

    private fun fetchVersionJsonFromInstaller(installerUrl: String): VersionJson {
        LogService.info("Загрузка Forge installer: $installerUrl")
        val request = Request.Builder().url(installerUrl).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code} — $installerUrl")
            }
            val bytes = response.body?.bytes() ?: throw Exception("Пустой ответ installer")
            return extractVersionJsonFromInstallerBytes(bytes, installerUrl)
        }
    }

    /** Читает version.json из байтов installer.jar (без записи на диск). */
    fun extractVersionJsonFromInstallerBytes(bytes: ByteArray, source: String): VersionJson {
        val temp = File.createTempFile("forge-installer-", ".jar")
        try {
            temp.writeBytes(bytes)
            ZipFile(temp).use { zip ->
                val entry = zip.getEntry(INSTALLER_VERSION_JSON)
                    ?: throw Exception("В installer нет $INSTALLER_VERSION_JSON ($source)")
                val text = zip.getInputStream(entry).bufferedReader().readText()
                return json.decodeFromString(VersionJson.serializer(), text)
            }
        } finally {
            try {
                temp.delete()
            } catch (_: Exception) {
            }
        }
    }

    fun buildForgeVersionId(mcVersion: String, forgeVersion: String): String =
        "forge-${forgeMavenVersion(mcVersion, forgeVersion)}"

    /**
     * Парсит forge-1.12.2-14.23.5.2859 → (1.12.2, 14.23.5.2859).
     * Нельзя делить по последнему «-» — ломает 1.7.10-10.13.4.1614.
     */
    fun parseForgeVersionId(versionId: String): Pair<String, String>? {
        if (!versionId.startsWith("forge-")) return null
        val full = versionId.removePrefix("forge-")
        val match = Regex("""^(\d+\.\d+(?:\.\d+)?)-(.+)$""").find(full) ?: return null
        val mc = match.groupValues[1]
        val forge = match.groupValues[2]
        return if (forge.isNotBlank()) mc to forge else null
    }

    fun resolveMcVersion(versionId: String): String {
        parseForgeVersionId(versionId)?.first?.let { return it }
        return versionId.removePrefix("forge-")
    }

    fun resolveMavenVersion(versionId: String): String {
        parseForgeVersionId(versionId)?.let { (mc, forge) ->
            return forgeMavenVersion(mc, forge)
        }
        return versionId.removePrefix("forge-")
    }

    private fun fetchPromotions(): Map<String, Map<String, String>> {
        val request = Request.Builder().url(FORGE_PROMOTIONS).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyMap()
            val body = response.body?.string() ?: return emptyMap()
            val root = json.parseToJsonElement(body).jsonObject
            val promos = root["promos"]?.jsonObject ?: return emptyMap()
            val result = mutableMapOf<String, MutableMap<String, String>>()
            promos.forEach { (key, value) ->
                val parts = key.split("-")
                if (parts.size >= 2) {
                    val mc = parts.dropLast(1).joinToString("-")
                    val kind = parts.last()
                    result.getOrPut(mc) { mutableMapOf() }[kind] = value.jsonPrimitive.content
                }
            }
            return result
        }
    }

    @kotlinx.serialization.Serializable
    private data class ForgeBmclEntry(
        val mcversion: String = "",
        val version: String = ""
    )
}
