package com.lolauncher.service

import com.lolauncher.data.api.MojangApi
import com.lolauncher.data.models.*
import com.lolauncher.util.LogService
import com.lolauncher.util.VersionDisplayNames
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Сервис управления версиями Minecraft.
 * Кэширует манифест и URL версий для корректной установки.
 */
class VersionService(val minecraftDir: File) {

    private var manifest: VersionManifest? = null
    private val versionUrlMap = mutableMapOf<String, String>()
    private val optiFineService = OptiFineService(minecraftDir)
    private val forgeService = ForgeService()
    private val fabricService = FabricService()
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private var installedIdsCache: Set<String>? = null

    /** Сбрасывает кэш установленных версий (после установки/удаления). */
    fun invalidateInstalledCache() {
        installedIdsCache = null
    }

    /**
     * Сканирует папку versions и возвращает ID физически присутствующих версий.
     */
    fun refreshInstalledIds(): Set<String> {
        val versionsDir = File(minecraftDir, "versions")
        if (!versionsDir.exists()) {
            installedIdsCache = emptySet()
            return emptySet()
        }
        val ids = versionsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir -> dir.name.takeIf { isPhysicallyInstalled(it) } }
            ?.toSet()
            ?: emptySet()
        installedIdsCache = ids
        return ids
    }

    fun getInstalledVersionIds(): Set<String> = installedIdsCache ?: refreshInstalledIds()

    /**
     * Проверяет фактическое наличие файлов версии на диске.
     * Не требует natives — только json + jar (свой или родительский для Forge/Fabric).
     */
    fun isPhysicallyInstalled(versionId: String): Boolean {
        if (versionId.isBlank()) return false
        if (versionId.startsWith(OptiFineService.OPTIFINE_PREFIX)) {
            return optiFineService.isOptiFineInstalled(versionId)
        }

        val versionDir = File(minecraftDir, "versions/$versionId")
        val jsonFile = File(versionDir, "$versionId.json")
        if (!jsonFile.exists()) return false

        val ownJar = File(versionDir, "$versionId.jar")
        if (ownJar.exists() && ownJar.length() > 1024) return true

        if (versionId.startsWith("forge-")) {
            val mc = forgeService.resolveMcVersion(versionId)
            val parentJar = File(minecraftDir, "versions/$mc/$mc.jar")
            return parentJar.exists() && parentJar.length() > 1024
        }
        if (versionId.startsWith("fabric-loader-")) {
            val game = fabricService.resolveGameVersion(versionId)
            val gameJar = File(minecraftDir, "versions/$game/$game.jar")
            return gameJar.exists() && gameJar.length() > 1024
        }
        return false
    }

    fun syncInstalledFlag(item: VersionItem): VersionItem =
        item.copy(isInstalled = item.id in getInstalledVersionIds())

    fun syncInstalledFlags(items: List<VersionItem>): List<VersionItem> {
        val installed = refreshInstalledIds()
        return items.map { it.copy(isInstalled = it.id in installed) }
    }

    /**
     * Обновляет список версий с серверов Mojang и модлоадеров.
     */
    suspend fun refreshVersions(): List<VersionItem> {
        refreshInstalledIds()
        loadManifest(force = true)

        val items = mutableListOf<VersionItem>()

        manifest!!.versions.forEach { v ->
            val type = mapMojangType(v.type)
            items.add(
                VersionItem(
                    id = v.id,
                    type = type,
                    releaseTime = v.releaseTime,
                    isInstalled = isVersionInstalled(v.id),
                    displayName = VersionDisplayNames.format(v.id, type)
                )
            )
        }

        items.addAll(buildFabricVersions())
        items.addAll(buildForgeVersions(manifest!!.versions))

        // OptiFine
        try {
            items.addAll(optiFineService.fetchOptiFineVersions())
        } catch (e: Exception) {
            LogService.warn("OptiFine API недоступен: ${e.message}")
        }

        return syncInstalledFlags(assignUniqueKeys(deduplicateById(items)))
            .sortedByDescending { it.releaseTime }
    }

    /**
     * Загружает официальный манифест Minecraft и заполняет карту URL.
     */
    fun loadManifest(force: Boolean = false) {
        if (!force && manifest != null && versionUrlMap.isNotEmpty()) return
        try {
            manifest = MojangApi.fetchVersionManifest()
            versionUrlMap.clear()
            manifest!!.versions.forEach { v ->
                versionUrlMap[v.id] = v.url
            }
            LogService.info("Манифест загружен: ${versionUrlMap.size} версий")
        } catch (e: Exception) {
            LogService.error("Ошибка загрузки манифеста", e)
            throw Exception("Не удалось загрузить манифест версий Minecraft: ${e.message}")
        }
    }

    /**
     * Удаляет дубликаты по id, оставляя первое вхождение.
     */
    private fun deduplicateById(items: List<VersionItem>): List<VersionItem> {
        val seen = mutableSetOf<String>()
        return items.filter { item ->
            if (item.id in seen) {
                LogService.warn("Дубликат версии пропущен: ${item.id}")
                false
            } else {
                seen.add(item.id)
                true
            }
        }
    }

    /**
     * Назначает уникальный ключ каждому элементу для LazyColumn.
     */
    fun assignUniqueKeys(items: List<VersionItem>): List<VersionItem> {
        val usedKeys = mutableSetOf<String>()
        return items.mapIndexed { index, item ->
            var key = "${item.id}_$index"
            var suffix = 0
            while (key in usedKeys) {
                suffix++
                key = "${item.id}_${index}_$suffix"
            }
            usedKeys.add(key)
            item.copy(uniqueKey = key)
        }
    }

    fun filterVersions(versions: List<VersionItem>, type: VersionType): List<VersionItem> {
        return when (type) {
            VersionType.ALL -> versions
            VersionType.INSTALLED -> versions.filter { it.isInstalled }
            else -> versions.filter { it.type == type }
        }
    }

    /**
     * Сканирует папку versions и возвращает установленные версии (в т.ч. не из каталога).
     */
    fun scanInstalledVersions(): List<VersionItem> {
        val versionsDir = File(minecraftDir, "versions")
        if (!versionsDir.exists()) return emptyList()

        refreshInstalledIds()

        return versionsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val id = dir.name
                if (!isPhysicallyInstalled(id)) return@mapNotNull null

                val type = detectVersionType(id)
                VersionItem(
                    id = id,
                    type = type,
                    releaseTime = "",
                    isInstalled = true,
                    modLoader = when (type) {
                        VersionType.FORGE -> "forge"
                        VersionType.FABRIC -> "fabric"
                        VersionType.OPTIFINE -> "optifine"
                        else -> null
                    },
                    displayName = VersionDisplayNames.format(id, type)
                )
            }
            ?.sortedByDescending { it.displayName }
            ?: emptyList()
    }

    private fun buildFabricVersions(): List<VersionItem> {
        val items = mutableListOf<VersionItem>()
        var stableLoader = "0.16.14"
        val gameVersions = linkedSetOf<String>()

        try {
            val fabricLoaders = MojangApi.fetchFabricLoaders()
            stableLoader = fabricLoaders.firstOrNull() ?: stableLoader
            MojangApi.fetchFabricGameVersions().take(80).forEach { gameVersions.add(it) }
        } catch (e: Exception) {
            LogService.warn("Fabric API недоступен: ${e.message}")
        }

        VersionDisplayNames.popularFabricMcVersions().forEach { gameVersions.add(it) }

        gameVersions.forEach { gameVer ->
            val id = "fabric-loader-$stableLoader-$gameVer"
            items.add(
                VersionItem(
                    id = id,
                    type = VersionType.FABRIC,
                    releaseTime = gameVer,
                    isInstalled = isVersionInstalled(id),
                    modLoader = "fabric",
                    displayName = "Fabric $gameVer"
                )
            )
        }
        return items
    }

    private fun buildForgeVersions(manifestVersions: List<ManifestVersion>): List<VersionItem> {
        val items = mutableListOf<VersionItem>()
        val mcVersions = linkedSetOf<String>()

        VersionDisplayNames.popularForgeMcVersions().forEach { mcVersions.add(it) }
        manifestVersions
            .filter { it.type == "release" }
            .take(40)
            .forEach { mcVersions.add(it.id) }

        mcVersions.forEach { mc ->
            val id = "forge-$mc"
            val releaseTime = manifestVersions.find { it.id == mc }?.releaseTime ?: ""
            items.add(
                VersionItem(
                    id = id,
                    type = VersionType.FORGE,
                    releaseTime = releaseTime,
                    isInstalled = isVersionInstalled(id),
                    modLoader = "forge",
                    displayName = "Forge $mc"
                )
            )
        }
        return items
    }

    private fun detectVersionType(versionId: String): VersionType = when {
        versionId.startsWith("forge-") -> VersionType.FORGE
        versionId.startsWith("fabric-loader-") -> VersionType.FABRIC
        versionId.startsWith(OptiFineService.OPTIFINE_PREFIX) -> VersionType.OPTIFINE
        versionId.contains("w") || versionId.contains("snapshot", ignoreCase = true) -> VersionType.SNAPSHOT
        else -> VersionType.RELEASE
    }

    fun resolveBaseVersionId(versionId: String): String {
        return when {
            versionId.startsWith("fabric-loader-") -> {
                val parts = versionId.removePrefix("fabric-loader-").split("-")
                parts.lastOrNull() ?: versionId
            }
            versionId.startsWith("forge-") -> versionId.removePrefix("forge-")
            versionId.startsWith(OptiFineService.OPTIFINE_PREFIX) ->
                optiFineService.parseVersionId(versionId)?.first ?: versionId
            else -> versionId
        }
    }

    fun getVersionUrl(versionId: String): String? {
        loadManifest()
        val baseId = resolveBaseVersionId(versionId)
        return versionUrlMap[baseId]
    }

    /**
     * Загружает JSON версии. Безопасно обрабатывает отсутствие версии.
     */
    fun fetchVersionJson(versionId: String): VersionJson {
        loadManifest()

        return when {
            versionId.startsWith("forge-") -> fetchForgeVersionJson(versionId)
            versionId.startsWith("fabric-loader-") -> fetchFabricVersionJson(versionId)
            versionId.startsWith(OptiFineService.OPTIFINE_PREFIX) -> fetchOptiFineVersionJson(versionId)
            else -> fetchVanillaVersionJson(versionId)
        }
    }

    private fun fetchVanillaVersionJson(versionId: String): VersionJson {
        val url = versionUrlMap[versionId]
        if (url.isNullOrBlank()) {
            throw Exception("Версия '$versionId' не найдена в манифесте Minecraft")
        }
        LogService.info("Загрузка vanilla JSON: $versionId")
        return MojangApi.fetchVersionJson(url).copy(id = versionId)
    }

    private fun fetchForgeVersionJson(versionId: String): VersionJson {
        val (mcVersion, forgeVersion) = forgeService.parseForgeVersionId(versionId)
            ?: run {
                val mc = forgeService.resolveMcVersion(versionId)
                val fv = forgeService.getRecommendedForgeVersion(mc)
                mc to fv
            }

        LogService.info("Загрузка Forge $mcVersion ($forgeVersion)")
        val forgeJson = forgeService.fetchForgeVersionJson(mcVersion, forgeVersion)
        return forgeJson.copy(id = versionId)
    }

    private fun fetchFabricVersionJson(versionId: String): VersionJson {
        val (loader, game) = fabricService.parseFabricVersionId(versionId)
            ?: throw Exception("Некорректный ID Fabric: $versionId")
        LogService.info("Загрузка Fabric $game (loader $loader)")
        return fabricService.fetchFabricProfile(game, loader).copy(id = versionId)
    }

    /** Устанавливает базовую vanilla-версию для Forge/Fabric/OptiFine */
    fun ensureBaseVersionInstalled(versionId: String) {
        val baseId = resolveBaseVersionId(versionId)
        if (baseId == versionId) return
        if (isVersionInstalled(baseId)) return
        val baseJson = fetchVanillaVersionJson(baseId)
        DownloadService(minecraftDir).prepareVersion(baseId, baseJson)
        saveVersionJson(baseId, baseJson)
    }

    private fun fetchOptiFineVersionJson(versionId: String): VersionJson {
        val baseId = resolveBaseVersionId(versionId)
        val url = versionUrlMap[baseId]
            ?: throw Exception("Базовая версия OptiFine '$baseId' не найдена")
        val baseJson = MojangApi.fetchVersionJson(url)
        val parsed = optiFineService.parseVersionId(versionId)
            ?: throw Exception("Некорректный ID OptiFine: $versionId")
        val (mcVersion, type, _) = parsed
        val optifineJar = optiFineService.downloadOptiFineJar(versionId, mcVersion, type)
        return optiFineService.buildOptiFineVersionJson(versionId, baseJson, optifineJar)
    }

    private fun downloadServiceForInstall(): DownloadService = DownloadService(minecraftDir)

    fun saveVersionJson(versionId: String, versionJson: VersionJson) {
        val versionDir = File(minecraftDir, "versions/$versionId")
        versionDir.mkdirs()
        File(versionDir, "$versionId.json").writeText(json.encodeToString(versionJson))
    }

    /**
     * Удаляет папку версии из .minecraft/versions.
     * Не затрагивает родительские vanilla-версии (Forge/Fabric используют общий jar базовой версии).
     */
    fun deleteVersion(versionId: String): Boolean {
        if (versionId.isBlank()) return false
        if (!isPhysicallyInstalled(versionId)) return false
        val versionDir = File(minecraftDir, "versions/$versionId")
        if (!versionDir.exists() || !versionDir.isDirectory) return false
        return try {
            val deleted = versionDir.deleteRecursively()
            if (deleted) invalidateInstalledCache()
            deleted
        } catch (e: Exception) {
            LogService.error("Ошибка удаления версии $versionId", e)
            false
        }
    }

    /** Версия установлена, если её файлы физически есть в папке versions. */
    fun isVersionInstalled(versionId: String): Boolean = isPhysicallyInstalled(versionId)

    private fun mapMojangType(type: String): VersionType = when (type) {
        "release" -> VersionType.RELEASE
        "snapshot" -> VersionType.SNAPSHOT
        "old_beta" -> VersionType.OLD_BETA
        "old_alpha" -> VersionType.OLD_ALPHA
        else -> VersionType.RELEASE
    }
}
