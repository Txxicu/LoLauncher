package com.lolauncher.service

import com.lolauncher.data.models.*
import com.lolauncher.util.GameArgsBuilder
import com.lolauncher.util.LibraryPresenceChecker
import com.lolauncher.util.JavaVersionResolver
import com.lolauncher.util.LogService
import com.lolauncher.util.MinecraftIconFix
import com.lolauncher.util.VersionJsonResolver
import java.io.File

/**
 * Предзапусковая проверка: Java, библиотеки, assets, профиль модлоадера.
 */
class LaunchPreparer(
    private val minecraftDir: File,
    private val downloadService: DownloadService,
    private val versionFetcher: (String) -> VersionJson
) {

    fun prepare(
        versionId: String,
        rawJson: VersionJson,
        javaInfo: JavaInfo?,
        onLog: (String, String) -> Unit = { _, msg -> LogService.info(msg) }
    ): LaunchDiagnostic {
        val checks = mutableListOf<LaunchCheck>()
        val merged = VersionJsonResolver.resolveInheritance(rawJson, minecraftDir, versionFetcher)
        val baseMc = VersionJsonResolver.resolveBaseMcVersion(versionId)
        val loader = VersionJsonResolver.detectLoaderKind(versionId, merged)

        MinecraftIconFix.ensureIcons(minecraftDir)

        onLog("INFO", "Проверка Java...")
        val javaReq = JavaVersionResolver.resolve(versionId, merged)
        val javaOk = javaInfo != null && (
            (javaReq.legacyOnly && javaInfo.majorVersion in 7..8) ||
                (!javaReq.legacyOnly && javaInfo.majorVersion >= javaReq.requiredMajor)
            )
        checks.add(
            LaunchCheck(
                "Java ${javaReq.requiredMajor}${if (javaReq.legacyOnly) " (только 8)" else "+"}",
                javaOk,
                javaInfo?.let { "Java ${it.version} (${it.path})" }
                    ?: "Java не найдена — установите JDK ${javaReq.requiredMajor}"
            )
        )

        onLog("INFO", "Проверка профиля ${loader.label}...")
        checks.add(
            LaunchCheck(
                "Профиль ${loader.label}",
                merged.mainClass.isNotBlank(),
                merged.mainClass.ifBlank { "mainClass не задан" }
            )
        )

        onLog("INFO", "Загрузка библиотек...")
        try {
            downloadService.downloadLibraries(merged, reportProgress = false)
            downloadService.extractNatives(versionId, merged)
        } catch (e: Exception) {
            onLog("WARN", "Библиотеки: ${e.message}")
        }

        val missingLibs = findMissingLibraries(merged)
        checks.add(
            LaunchCheck(
                "Библиотеки",
                missingLibs.isEmpty(),
                if (missingLibs.isEmpty()) "Все библиотеки на месте"
                else "Отсутствует: ${missingLibs.take(3).joinToString()}${if (missingLibs.size > 3) "..." else ""}"
            )
        )

        onLog("INFO", "Загрузка assets...")
        try {
            downloadService.downloadAssets(merged, reportProgress = false)
            downloadService.syncLegacyResources(baseMc, merged)
        } catch (e: Exception) {
            onLog("WARN", "Assets: ${e.message}")
        }

        val assetsDir = GameArgsBuilder.resolveAssetsDir(versionId, minecraftDir, merged)
        val assetsOk = File(assetsDir).exists() && (
            merged.assetIndex == null ||
                File(minecraftDir, "assets/indexes/${merged.assetIndex!!.id}.json").exists()
            )
        checks.add(
            LaunchCheck(
                "Assets",
                assetsOk,
                assetsDir
            )
        )

        val clientJar = resolveClientJar(versionId, merged)
        checks.add(
            LaunchCheck(
                "Client JAR",
                clientJar.exists() && clientJar.length() > 1024,
                clientJar.absolutePath
            )
        )

        if (loader == LoaderKind.FORGE) {
            val hasForgeLib = merged.libraries.any {
                it.name.contains("minecraftforge", ignoreCase = true)
            }
            checks.add(
                LaunchCheck("Forge libraries", hasForgeLib, if (hasForgeLib) "Forge библиотеки найдены" else "Forge не установлен")
            )
        }

        val ok = checks.all { it.passed }
        val summary = if (!ok) checks.filter { !it.passed }.joinToString("\n") { "• ${it.name}: ${it.detail}" } else null
        return LaunchDiagnostic(ok = ok, checks = checks, errorSummary = summary)
    }

    private fun resolveClientJar(versionId: String, merged: VersionJson): File {
        val versionDir = File(minecraftDir, "versions/$versionId")
        val local = File(versionDir, "$versionId.jar")
        if (local.exists()) return local
        val parentId = merged.inheritsFrom
        if (!parentId.isNullOrBlank()) {
            val parentJar = File(minecraftDir, "versions/$parentId/$parentId.jar")
            if (parentJar.exists()) {
                versionDir.mkdirs()
                parentJar.copyTo(local, overwrite = true)
            }
        }
        return local
    }

    private fun findMissingLibraries(versionJson: VersionJson): List<String> {
        val librariesDir = File(minecraftDir, "libraries")
        return LibraryPresenceChecker.findMissing(versionJson.libraries, librariesDir, downloadService)
    }
}
