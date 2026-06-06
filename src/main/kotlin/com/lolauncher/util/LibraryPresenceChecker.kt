package com.lolauncher.util

import com.lolauncher.data.models.Library
import com.lolauncher.service.DownloadService
import java.io.File

/**
 * Проверяет наличие библиотек с учётом natives/classifiers и OS rules.
 * Не требует основной .jar для библиотек вроде jinput-platform (только natives).
 */
object LibraryPresenceChecker {

    /** Библиотеки, у которых часто нет главного артефакта — только natives по ОС */
    private val NATIVES_ONLY_HINTS = listOf(
        "jinput-platform",
        "lwjgl-platform",
        "text2speech"
    )

    fun findMissing(
        libraries: List<Library>,
        librariesDir: File,
        downloadService: DownloadService
    ): List<String> {
        return libraries.mapNotNull { library ->
            if (!downloadService.isLibraryAllowed(library)) return@mapNotNull null
            if (isPresent(library, librariesDir, downloadService)) null else library.name
        }
    }

    fun isPresent(library: Library, librariesDir: File, downloadService: DownloadService): Boolean {
        if (!downloadService.isLibraryAllowed(library)) return true

        val artifacts = ArtifactUtils.resolveAllLibraryArtifacts(library)
        if (artifacts.isNotEmpty()) {
            if (artifacts.any { File(librariesDir, it.path).exists() }) return true
            if (library.name.contains("minecraftforge", ignoreCase = true)) return false
        }

        // Только natives / classifiers (jinput-platform, lwjgl-platform, …)
        if (library.natives != null) {
            val nativeKey = PlatformUtils.nativeKey
            val classifierKey = library.natives[nativeKey]?.replace("\${arch}", PlatformUtils.osArch)
            if (classifierKey != null) {
                val nativeArtifact = library.downloads?.classifiers?.get(classifierKey)
                if (nativeArtifact != null) {
                    val resolved = ArtifactUtils.resolveClassifierArtifact(library, classifierKey, nativeArtifact)
                    val nativeFile = File(librariesDir, resolved.path)
                    if (nativeFile.exists()) return true
                }
            }
            // На текущей ОС natives не объявлены — не блокируем запуск
            if (library.natives[nativeKey] == null) return true
        }

        // Нет downloads — старый формат, не блокируем
        if (library.downloads == null && library.natives == null) return true

        return isNativesOnlyLibrary(library)
    }

    private fun isNativesOnlyLibrary(library: Library): Boolean {
        val name = library.name.lowercase()
        if (NATIVES_ONLY_HINTS.any { name.contains(it) }) return true
        return library.natives != null && library.downloads?.artifact == null &&
            !library.downloads?.classifiers.isNullOrEmpty()
    }
}
