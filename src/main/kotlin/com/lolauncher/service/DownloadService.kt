package com.lolauncher.service

import com.lolauncher.data.models.*
import com.lolauncher.util.ArtifactUtils
import com.lolauncher.util.LogService
import com.lolauncher.util.PlatformUtils
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Сервис загрузки файлов Minecraft: клиент, библиотеки, ассеты, нативные библиотеки.
 * Поддерживает старые version JSON без поля path.
 */
class InstallCancelledException : Exception("Установка отменена")

class DownloadService(private val minecraftDir: File) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    var onProgress: ((DownloadProgress) -> Unit)? = null

    @Volatile
    var installCancelled: Boolean = false
        private set

    fun resetCancel() {
        installCancelled = false
    }

    fun cancelInstall() {
        installCancelled = true
    }

    private fun ensureNotCancelled() {
        if (installCancelled) throw InstallCancelledException()
    }

    fun prepareVersion(versionId: String, versionJson: VersionJson) {
        resetCancel()
        ensureNotCancelled()
        val versionDir = File(minecraftDir, "versions/$versionId")
        versionDir.mkdirs()

        // Сначала устанавливаем родительскую версию (inheritsFrom)
        versionJson.inheritsFrom?.let { parentId ->
            val parentDir = File(minecraftDir, "versions/$parentId")
            val parentJsonFile = File(parentDir, "$parentId.json")
            if (!parentJsonFile.exists()) {
                LogService.warn("Родительская версия $parentId не установлена — установите базовую версию")
            } else {
                val parentJson = Json { ignoreUnknownKeys = true }
                    .decodeFromString(VersionJson.serializer(), parentJsonFile.readText())
                downloadLibraries(parentJson)
                downloadAssets(parentJson)
                extractNatives(parentId, parentJson)
            }
        }

        saveVersionJson(versionId, versionJson, versionDir)
        ensureClientJar(versionId, versionJson, versionDir)
        downloadLibraries(versionJson)
        downloadAssets(versionJson)
        extractNatives(versionId, versionJson)
        syncLegacyResources(
            com.lolauncher.util.VersionJsonResolver.resolveBaseMcVersion(versionId),
            versionJson
        )

        onProgress?.invoke(
            DownloadProgress(
                status = DownloadStatus.COMPLETE,
                message = "Версия $versionId установлена"
            )
        )
    }

    private fun ensureClientJar(versionId: String, versionJson: VersionJson, versionDir: File) {
        val clientJar = File(versionDir, "$versionId.jar")
        if (clientJar.exists() && clientJar.length() > 1024) return

        val download = versionJson.downloads?.client
        if (download != null && download.url.isNotBlank()) {
            downloadClient(versionId, versionJson, versionDir)
            return
        }

        val parentId = versionJson.inheritsFrom
        if (!parentId.isNullOrBlank()) {
            val parentJar = File(minecraftDir, "versions/$parentId/$parentId.jar")
            if (parentJar.exists()) {
                parentJar.copyTo(clientJar, overwrite = true)
                return
            }
        }
        throw Exception("Client.jar не найден для $versionId")
    }

    private fun saveVersionJson(versionId: String, versionJson: VersionJson, versionDir: File) {
        val jsonFile = File(versionDir, "$versionId.json")
        if (!jsonFile.exists()) {
            val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
            jsonFile.writeText(json.encodeToString(VersionJson.serializer(), versionJson))
        }
    }

    private fun downloadClient(versionId: String, versionJson: VersionJson, versionDir: File) {
        val clientJar = File(versionDir, "$versionId.jar")
        if (clientJar.exists()) return

        val download = versionJson.downloads?.client
            ?: throw Exception("Нет данных для загрузки клиента версии $versionId")

        val clientUrl = download.url.ifBlank {
            throw Exception("URL клиента не указан для версии $versionId")
        }

        onProgress?.invoke(
            DownloadProgress(
                fileName = "$versionId.jar",
                status = DownloadStatus.DOWNLOADING,
                message = "Загрузка клиента..."
            )
        )

        downloadToFile(clientUrl, clientJar, download.sha1)
    }

    fun downloadLibraries(versionJson: VersionJson, reportProgress: Boolean = true) {
        val librariesDir = File(minecraftDir, "libraries")
        var downloaded = 0
        val total = versionJson.libraries.count { isLibraryAllowed(it) }

        versionJson.libraries.forEach { library ->
            ensureNotCancelled()
            if (!isLibraryAllowed(library)) return@forEach

            val artifacts = ArtifactUtils.resolveAllLibraryArtifacts(library)
            if (artifacts.isEmpty()) return@forEach

            val isForge = library.name.contains("minecraftforge", ignoreCase = true)

            artifacts.forEach { resolved ->
                ensureNotCancelled()
                val libFile = File(librariesDir, resolved.path)
                if (libFile.exists()) return@forEach

                if (reportProgress) {
                    onProgress?.invoke(
                        DownloadProgress(
                            fileName = resolved.path.substringAfterLast('/'),
                            currentBytes = downloaded.toLong(),
                            totalBytes = total.toLong(),
                            status = DownloadStatus.DOWNLOADING,
                            message = "Библиотеки: $downloaded / $total"
                        )
                    )
                }

                libFile.parentFile?.mkdirs()
                try {
                    // Для Forge пропускаем SHA1-проверку — Maven иногда отдаёт
                    // -universal вместо bare jar с другим содержимым
                    downloadToFile(resolved.url, libFile, if (isForge) "" else resolved.sha1)
                } catch (e: InstallCancelledException) {
                    throw e
                } catch (e: Exception) {
                    if (isForge) {
                        // Fallback: пробуем -universal суффикс (Forge 1.13+)
                        val universalResolved = tryForgeUniversalFallback(resolved, librariesDir)
                        if (universalResolved != null) {
                            LogService.info("Forge universal fallback: ${universalResolved.url}")
                        } else {
                            LogService.error("Forge библиотека ${library.name}: ${e.message}")
                            throw Exception("Не удалось скачать ${library.name}: ${e.message}")
                        }
                    } else {
                        LogService.warn("Пропуск библиотеки ${library.name}: ${e.message}")
                    }
                }
            }
            downloaded++
        }
    }

    /**
     * Если обычный Forge jar вернул 404 — пробуем -universal вариант.
     * Скачивает файл и кладёт под его реальным именем (с суффиксом -universal),
     * чтобы не затирать оригинальный путь и правильно подгружаться в classpath.
     */
    private fun tryForgeUniversalFallback(
        resolved: com.lolauncher.util.ResolvedArtifact,
        librariesDir: File
    ): com.lolauncher.util.ResolvedArtifact? {
        if (resolved.url.contains("-universal")) return null // уже universal, не зацикливаемся

        val universalUrl = resolved.url.replace(".jar", "-universal.jar")
        val universalPath = resolved.path.replace(".jar", "-universal.jar")
        val libFile = File(librariesDir, universalPath)

        return try {
            downloadToFile(universalUrl, libFile, sha1 = "")
            LogService.info("Forge: скачан universal → ${libFile.name}")
            resolved.copy(url = universalUrl, path = universalPath)
        } catch (e: Exception) {
            LogService.warn("Forge universal fallback не удался: ${e.message}")
            null
        }
    }

    fun downloadAssets(versionJson: VersionJson, reportProgress: Boolean = true) {
        val assetIndex = versionJson.assetIndex ?: return
        val indexesDir = File(minecraftDir, "assets/indexes")
        val objectsDir = File(minecraftDir, "assets/objects")
        indexesDir.mkdirs()
        objectsDir.mkdirs()

        val indexFile = File(indexesDir, "${assetIndex.id}.json")
        if (!indexFile.exists()) {
            val indexUrl = assetIndex.url.ifBlank {
                "https://piston-meta.mojang.com/v1/packages/${assetIndex.sha1}/${assetIndex.id}.json"
            }
            if (reportProgress) {
                onProgress?.invoke(
                    DownloadProgress(
                        fileName = "assets index",
                        status = DownloadStatus.DOWNLOADING,
                        message = "Загрузка индекса ассетов..."
                    )
                )
            }
            downloadToFile(indexUrl, indexFile, assetIndex.sha1)
        }

        try {
            val indexContent = indexFile.readText()
            val jsonObj = Json.parseToJsonElement(indexContent).jsonObject
            val objects = jsonObj["objects"]?.jsonObject ?: return

            var downloaded = 0
            val total = objects.size

            objects.entries.forEach { (_, value) ->
                ensureNotCancelled()
                val obj = value.jsonObject
                val hash = obj["hash"]?.jsonPrimitive?.content ?: return@forEach
                val hashPrefix = hash.substring(0, 2)
                val objectFile = File(objectsDir, "$hashPrefix/$hash")

                if (!objectFile.exists()) {
                    objectFile.parentFile?.mkdirs()
                    val url = "https://resources.download.minecraft.net/$hashPrefix/$hash"
                    try {
                        downloadToFile(url, objectFile, hash)
                    } catch (_: Exception) { }
                }

                downloaded++
                if (reportProgress && downloaded % 100 == 0) {
                    onProgress?.invoke(
                        DownloadProgress(
                            currentBytes = downloaded.toLong(),
                            totalBytes = total.toLong(),
                            status = DownloadStatus.DOWNLOADING,
                            message = "Ассеты: $downloaded / $total"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            LogService.warn("Ошибка загрузки ассетов: ${e.message}")
        }
    }

    /**
     * Синхронизирует legacy resources (1.0–1.5) из современного asset index Mojang.
     * Устраняет обращения к устаревшему MinecraftResources (S3).
     */
    fun syncLegacyResources(baseMcVersion: String, versionJson: VersionJson) {
        val minor = baseMcVersion.filter { it.isDigit() || it == '.' }
            .split(".").getOrNull(1)?.toIntOrNull() ?: return
        if (minor > 5) return

        val resourcesDir = File(minecraftDir, "resources")
        resourcesDir.mkdirs()
        val assetsDir = File(minecraftDir, "assets")
        val indexId = versionJson.assets.ifBlank { "pre-1.6" }
        val indexFile = File(assetsDir, "indexes/$indexId.json")
        if (!indexFile.exists()) return

        try {
            val indexContent = indexFile.readText()
            val jsonObj = Json.parseToJsonElement(indexContent).jsonObject
            val objects = jsonObj["objects"]?.jsonObject ?: return
            val objectsDir = File(assetsDir, "objects")

            objects.entries.forEach { (legacyPath, value) ->
                val obj = value.jsonObject
                val hash = obj["hash"]?.jsonPrimitive?.content ?: return@forEach
                val source = File(objectsDir, "${hash.substring(0, 2)}/$hash")
                if (!source.exists()) return@forEach
                val target = File(resourcesDir, legacyPath)
                if (!target.exists()) {
                    target.parentFile?.mkdirs()
                    source.copyTo(target, overwrite = true)
                }
            }
            LogService.info("Legacy resources synchronized for $baseMcVersion")
        } catch (e: Exception) {
            LogService.warn("Legacy resources: ${e.message}")
        }
    }

    fun extractNatives(versionId: String, versionJson: VersionJson) {
        val nativesDir = File(minecraftDir, "versions/$versionId/natives")
        if (nativesDir.exists() && nativesDir.listFiles()?.isNotEmpty() == true) return

        nativesDir.mkdirs()
        onProgress?.invoke(
            DownloadProgress(status = DownloadStatus.EXTRACTING, message = "Извлечение natives...")
        )

        versionJson.libraries.forEach { library ->
            if (!isLibraryAllowed(library)) return@forEach
            val natives = library.natives ?: return@forEach
            val nativeKey = PlatformUtils.nativeKey
            val nativeClassifier = natives[nativeKey]?.replace("\${arch}", PlatformUtils.osArch)
                ?: return@forEach

            val classifierArtifact = library.downloads?.classifiers?.get(nativeClassifier)
            if (classifierArtifact != null) {
                val resolved = ArtifactUtils.resolveClassifierArtifact(library, nativeClassifier, classifierArtifact)
                val libFile = File(minecraftDir, "libraries/${resolved.path}")
                if (!libFile.exists()) {
                    libFile.parentFile?.mkdirs()
                    try {
                        downloadToFile(resolved.url, libFile, resolved.sha1)
                    } catch (_: Exception) {
                        return@forEach
                    }
                }
                extractNativesFromJar(libFile, nativesDir, library.extract?.exclude ?: emptyList())
            } else {
                // Старый формат: natives могут быть в основном jar
                val resolved = ArtifactUtils.resolveLibraryArtifact(library) ?: return@forEach
                val libFile = File(minecraftDir, "libraries/${resolved.path}")
                if (libFile.exists()) {
                    extractNativesFromJar(libFile, nativesDir, library.extract?.exclude ?: emptyList())
                }
            }
        }
    }

    private fun extractNativesFromJar(jarFile: File, targetDir: File, exclude: List<String>) {
        try {
            ZipInputStream(jarFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name
                        val shouldExclude = exclude.any { name.startsWith(it) }
                        val isNative = name.endsWith(PlatformUtils.nativeExtension)

                        if (!shouldExclude && isNative) {
                            val outFile = File(targetDir, name.substringAfterLast('/'))
                            outFile.outputStream().use { zis.copyTo(it) }
                        }
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            println("Ошибка извлечения natives из ${jarFile.name}: ${e.message}")
        }
    }

    fun isLibraryAllowed(library: Library): Boolean {
        val rules = library.rules ?: return true
        var allowed = false
        rules.forEach { rule ->
            val osMatch = rule.os?.name?.let { it == PlatformUtils.osName } ?: true
            if (osMatch) {
                allowed = rule.action == "allow"
            }
        }
        return allowed
    }

    private fun downloadToFile(url: String, target: File, sha1: String = "") {
        ensureNotCancelled()
        if (url.isBlank()) {
            throw Exception("Пустой URL для ${target.name}")
        }
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: $url")
            }
            val bytes = response.body?.bytes() ?: throw Exception("Пустой ответ")

            if (sha1.isNotBlank()) {
                val actualSha1 = sha1(bytes)
                if (actualSha1 != sha1) {
                    throw Exception("SHA1 не совпадает для ${target.name}")
                }
            }

            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
        }
    }

    private fun sha1(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }
}