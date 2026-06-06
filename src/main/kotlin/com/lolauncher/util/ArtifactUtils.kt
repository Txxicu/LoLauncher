package com.lolauncher.util

import com.lolauncher.data.models.Artifact
import com.lolauncher.data.models.Library

/**
 * Утилиты для работы с артефактами Minecraft.
 * Поддерживает старые version JSON (1.0–1.5), где отсутствует поле path.
 */
object ArtifactUtils {

    const val LIBRARY_BASE_URL = "https://libraries.minecraft.net/"
    const val FORGE_MAVEN_BASE = "https://maven.minecraftforge.net/"

    /**
     * Forge 1.13+ публикует основной jar только с суффиксом -universal.
     * Определяем по координатам: net.minecraftforge:forge:{mc}-{forge}
     * (без classifier в name, т.е. parts.size == 3)
     */
    private fun isForgeMainArtifact(name: String): Boolean {
        val parts = name.split(":")
        return parts.size == 3 &&
                parts[0] == "net.minecraftforge" &&
                parts[1] == "forge"
    }

    /**
     * Определяет MC-версию из Forge координат и проверяет,
     * нужен ли суффикс -universal (Forge для MC 1.13+).
     */
    private fun forgeNeedsUniversal(name: String): Boolean {
        if (!isForgeMainArtifact(name)) return false
        val version = name.split(":").getOrNull(2) ?: return false
        // version вида "1.16.5-36.2.34" — берём mc-часть до первого '-'
        val mcPart = version.substringBefore("-")
        val minor = mcPart.split(".").getOrNull(1)?.toIntOrNull() ?: return false
        return minor >= 13
    }

    /**
     * Преобразует Maven-координаты в путь: group:artifact:version[:classifier]
     * Пример: net.minecraft:launchwrapper:1.5 → net/minecraft/launchwrapper/1.5/launchwrapper-1.5.jar
     */
    fun mavenPathFromName(name: String, classifier: String? = null): String {
        val parts = name.split(":")
        if (parts.size < 3) return name.replace('.', '/')

        val groupPath = parts[0].replace('.', '/')
        val artifactId = parts[1]
        val version = parts[2]

        // Для Forge 1.13+ без явного classifier — добавляем -universal
        val effectiveClassifier = when {
            classifier != null -> classifier
            parts.size >= 4 && parts[3].isNotBlank() -> parts[3]
            forgeNeedsUniversal(name) -> "universal"
            else -> null
        }

        val classSuffix = if (effectiveClassifier != null) "-$effectiveClassifier" else ""
        val extension = if (parts.size >= 5 && parts[4].isNotBlank()) parts[4] else "jar"
        return "$groupPath/$artifactId/$version/$artifactId-$version$classSuffix.$extension"
    }

    /**
     * Возвращает локальный путь артефакта.
     */
    fun resolvePath(artifact: Artifact, libraryName: String? = null, classifier: String? = null): String {
        if (artifact.path.isNotBlank()) return artifact.path
        if (libraryName != null) return mavenPathFromName(libraryName, classifier)
        val fromUrl = pathFromUrl(artifact.url)
        if (fromUrl.isNotBlank()) return fromUrl
        return "unknown/${artifact.sha1.ifBlank { "artifact" }}.jar"
    }

    /**
     * Возвращает URL для скачивания артефакта.
     */
    fun resolveUrl(
        artifact: Artifact,
        libraryName: String? = null,
        libraryBaseUrl: String? = null,
        classifier: String? = null
    ): String {
        if (artifact.url.isNotBlank()) return artifact.url
        val path = resolvePath(artifact, libraryName, classifier)
        if (path.startsWith("net/minecraftforge/")) {
            return if (FORGE_MAVEN_BASE.endsWith("/")) "$FORGE_MAVEN_BASE$path" else "$FORGE_MAVEN_BASE/$path"
        }
        val base = libraryBaseUrl?.takeIf { it.isNotBlank() } ?: LIBRARY_BASE_URL
        return if (base.endsWith("/")) "$base$path" else "$base/$path"
    }

    /**
     * Все артефакты библиотеки (artifact + classifiers).
     * Нужно для Forge 1.17+ (:client, :universal и т.д.).
     */
    fun resolveAllLibraryArtifacts(library: Library): List<ResolvedArtifact> {
        val results = mutableListOf<ResolvedArtifact>()
        val seen = mutableSetOf<String>()

        fun add(artifact: Artifact, classifier: String? = null) {
            val path = resolvePath(artifact, library.name, classifier)
            if (path in seen) return
            seen.add(path)
            results.add(
                ResolvedArtifact(
                    path = path,
                    url = resolveUrl(artifact, library.name, library.url, classifier),
                    sha1 = artifact.sha1
                )
            )
        }

        // 1. Добавляем основной артефакт из блока downloads
        library.downloads?.artifact?.let { add(it) }

        // ХАК ДЛЯ FORGE: Если это главный артефакт Forge 1.13+,
        // принудительно генерируем и добавляем к загрузкам universal-файл
        if (library.downloads?.artifact != null && isForgeMainArtifact(library.name) && forgeNeedsUniversal(library.name)) {
            add(library.downloads.artifact, "universal")
        }

        // 2. Добавляем классификаторы (например, нативные библиотеки или специфичные сборки)
        library.downloads?.classifiers?.forEach { (key, artifact) -> add(artifact, key) }

        // 3. Обработка старого формата JSON (где вообще нет блока downloads, только name)
        if (results.isEmpty() && library.name.isNotBlank()) {
            val basePath = mavenPathFromName(library.name)
            val baseUrl = if (basePath.startsWith("net/minecraftforge/")) FORGE_MAVEN_BASE else LIBRARY_BASE_URL

            val mainArtifact = ResolvedArtifact(path = basePath, url = "$baseUrl$basePath")
            if (mainArtifact.path !in seen) {
                seen.add(mainArtifact.path)
                results.add(mainArtifact)
            }

            // Если в старом формате JSON пришел Forge 1.13+, добавляем для него пару в виде universal-пути
            if (isForgeMainArtifact(library.name) && forgeNeedsUniversal(library.name)) {
                val universalPath = mavenPathFromName(library.name, "universal")
                if (universalPath !in seen) {
                    seen.add(universalPath)
                    results.add(ResolvedArtifact(path = universalPath, url = "$baseUrl$universalPath"))
                }
            }
        }
        return results
    }

    /**
     * Разрешает основной артефакт библиотеки (поддержка старого формата без downloads).
     */
    fun resolveLibraryArtifact(library: Library): ResolvedArtifact? =
        resolveAllLibraryArtifacts(library).firstOrNull()

    /**
     * Разрешает classifier-артефакт (natives).
     */
    fun resolveClassifierArtifact(
        library: Library,
        classifierKey: String,
        artifact: Artifact
    ): ResolvedArtifact {
        return ResolvedArtifact(
            path = resolvePath(artifact, library.name, classifierKey),
            url = resolveUrl(artifact, library.name, library.url, classifierKey),
            sha1 = artifact.sha1
        )
    }

    private fun pathFromUrl(url: String): String {
        if (url.isBlank()) return ""
        return when {
            url.contains("libraries.minecraft.net/") ->
                url.substringAfter("libraries.minecraft.net/")
            url.contains("piston-data.mojang.com/") ->
                url.substringAfter("piston-data.mojang.com/")
            url.contains("piston-meta.mojang.com/") ->
                url.substringAfter("piston-meta.mojang.com/")
            else -> url.substringAfterLast('/')
        }
    }
}

/** Разрешённый артефакт с путём, URL и хешем */
data class ResolvedArtifact(
    val path: String,
    val url: String,
    val sha1: String = ""
)