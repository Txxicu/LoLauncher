package com.lolauncher.service

import com.lolauncher.data.models.JavaInfo
import com.lolauncher.util.PlatformUtils
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Сервис обнаружения и установки Java.
 */
class JavaService {

    var onProgress: ((String) -> Unit)? = null

    /**
     * @param requiredMajor минимальная (или точная при legacyOnly) мажорная версия
     * @param legacyOnly если true — только Java 7/8 (для LaunchWrapper / MC ≤1.12)
     */
    fun findJava(requiredMajor: Int = 17, customPath: String = "", legacyOnly: Boolean = false): JavaInfo? {
        val candidates = mutableListOf<JavaInfo>()

        fun tryAdd(info: JavaInfo?) {
            if (info != null && matchesRequirement(info, requiredMajor, legacyOnly)) {
                candidates.add(info)
            }
        }

        if (customPath.isNotBlank()) {
            tryAdd(checkJavaPath(customPath))
        }

        System.getenv("JAVA_HOME")?.let { path ->
            tryAdd(checkJavaPath(findJavaExecutable(path)))
        }

        tryAdd(checkJavaPath("java"))

        if (PlatformUtils.currentOS == PlatformUtils.OS.WINDOWS) {
            val bases = listOf(
                System.getenv("ProgramFiles"),
                System.getenv("ProgramFiles(x86)"),
                "C:\\Program Files\\Java",
                "C:\\Program Files\\Eclipse Adoptium",
                "C:\\Program Files\\Microsoft",
                "C:\\Program Files\\Amazon Corretto",
                File(System.getenv("APPDATA") ?: "", "LoLauncher/java").absolutePath
            ).filterNotNull()

            for (base in bases) {
                val dir = File(base)
                if (!dir.exists()) continue
                dir.walkTopDown().maxDepth(4).forEach { file ->
                    if (file.name.equals("java.exe", ignoreCase = true) ||
                        (file.name == "java" && file.parentFile?.name == "bin")
                    ) {
                        tryAdd(checkJavaPath(file.absolutePath))
                    }
                }
            }
        }

        return selectBestCandidate(candidates, requiredMajor, legacyOnly)
    }

    private fun matchesRequirement(info: JavaInfo, requiredMajor: Int, legacyOnly: Boolean): Boolean {
        return if (legacyOnly) {
            info.majorVersion in 7..8
        } else {
            info.majorVersion >= requiredMajor
        }
    }

    private fun selectBestCandidate(
        candidates: List<JavaInfo>,
        requiredMajor: Int,
        legacyOnly: Boolean
    ): JavaInfo? {
        if (candidates.isEmpty()) return null
        return if (legacyOnly) {
            candidates.minByOrNull { it.majorVersion }
        } else {
            candidates.filter { it.majorVersion >= requiredMajor }
                .minByOrNull { it.majorVersion }
        }
    }

    fun installJava(requiredMajor: Int = 17): JavaInfo? {
        onProgress?.invoke("Загрузка Java $requiredMajor...")

        return try {
            val installDir = File(
                System.getenv("APPDATA") ?: System.getProperty("user.home"),
                "LoLauncher/java/jdk-$requiredMajor"
            )

            if (installDir.exists()) {
                val existing = findJavaExecutable(installDir.absolutePath)
                checkJavaPath(existing)?.let { info ->
                    if (info.majorVersion == requiredMajor || info.majorVersion >= requiredMajor) return info
                }
            }

            val apiUrl = "https://api.adoptium.net/v3/binary/latest/$requiredMajor/ga/" +
                    "windows/x64/jdk/hotspot/normal/eclipse?project=jdk"

            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .build()

            onProgress?.invoke("Скачивание JDK $requiredMajor...")
            val request = Request.Builder().url(apiUrl).get().build()
            val zipFile = File(installDir.parentFile, "jdk-$requiredMajor.zip")

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    onProgress?.invoke("Ошибка загрузки Java: HTTP ${response.code}")
                    return null
                }
                val bytes = response.body?.bytes() ?: return null
                zipFile.parentFile?.mkdirs()
                zipFile.writeBytes(bytes)
            }

            onProgress?.invoke("Распаковка Java...")
            extractZip(zipFile, installDir.parentFile!!)
            zipFile.delete()

            findJava(requiredMajor, legacyOnly = requiredMajor <= 8)
        } catch (e: Exception) {
            onProgress?.invoke("Ошибка установки Java: ${e.message}")
            null
        }
    }

    fun checkJavaPath(javaPath: String): JavaInfo? {
        return try {
            val process = ProcessBuilder(javaPath, "-version")
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            val versionRegex = """version "(.+?)"""".toRegex()
            val match = versionRegex.find(output)
            val versionStr = match?.groupValues?.get(1) ?: "unknown"
            val major = parseMajorVersion(versionStr)

            JavaInfo(
                path = javaPath,
                version = versionStr,
                majorVersion = major,
                isValid = true
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseMajorVersion(version: String): Int {
        return try {
            if (version.startsWith("1.")) {
                val minor = version.removePrefix("1.").substringBefore(".").substringBefore("-")
                return minor.toInt()
            }
            version.substringBefore(".").substringBefore("-").toInt()
        } catch (_: Exception) {
            8
        }
    }

    private fun findJavaExecutable(basePath: String): String {
        return when (PlatformUtils.currentOS) {
            PlatformUtils.OS.WINDOWS -> File(basePath, "bin/java.exe").absolutePath
            else -> File(basePath, "bin/java").absolutePath
        }
    }

    private fun extractZip(zipFile: File, targetDir: File) {
        java.util.zip.ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                if (entry.isDirectory) outFile.mkdirs()
                else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zis.copyTo(it) }
                }
                entry = zis.nextEntry
            }
        }
    }
}
