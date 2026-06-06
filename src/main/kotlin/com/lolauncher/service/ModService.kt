package com.lolauncher.service

import com.lolauncher.data.models.ModInfo
import com.lolauncher.util.JarMetadataReader
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Сервис управления модами.
 * Сканирует папки mods для Forge/Fabric/NeoForge, позволяет удалять моды.
 */
class ModService(private val minecraftDir: File) {

    private val scanCache = ConcurrentHashMap<String, Pair<Long, ModInfo>>()

    /**
     * Возвращает список установленных модов (параллельное чтение + кэш по mtime).
     */
    fun getInstalledMods(): List<ModInfo> {
        val modsDir = File(minecraftDir, "mods")
        if (!modsDir.exists()) return emptyList()

        val files = modsDir.listFiles()
            ?.filter { it.isFile && (it.extension.equals("jar", true) || it.name.endsWith(".jar.disabled")) }
            ?: return emptyList()

        return files.parallelStream()
            .map { file ->
                val mtime = file.lastModified()
                val cached = scanCache[file.absolutePath]
                if (cached != null && cached.first == mtime) {
                    cached.second
                } else {
                    val info = JarMetadataReader.readMod(file)
                    scanCache[file.absolutePath] = mtime to info
                    info
                }
            }
            .sorted { a, b -> a.displayName.lowercase().compareTo(b.displayName.lowercase()) }
            .toList()
    }

    fun invalidateCache() {
        scanCache.clear()
    }

    /**
     * Удаляет мод по пути к файлу.
     */
    fun deleteMod(filePath: String): Boolean {
        return try {
            File(filePath).delete()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Включает/отключает мод (переименование .jar <-> .jar.disabled).
     */
    fun toggleMod(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            val newName = if (file.name.endsWith(".disabled")) {
                file.name.removeSuffix(".disabled")
            } else {
                "${file.name}.disabled"
            }
            file.renameTo(File(file.parent, newName))
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Открывает папку mods в проводнике.
     */
    fun openModsFolder(): File {
        val modsDir = File(minecraftDir, "mods")
        modsDir.mkdirs()
        return modsDir
    }

    /**
     * Копирует jar-файл мода в папку mods.
     */
    fun importMod(sourceFile: File): Boolean {
        return try {
            val modsDir = openModsFolder()
            val target = File(modsDir, sourceFile.name)
            sourceFile.copyTo(target, overwrite = true)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Определяет модлоадер по имени файла (эвристика).
     */
    private fun detectModLoader(fileName: String): String {
        val lower = fileName.lowercase()
        return when {
            lower.contains("fabric") -> "Fabric"
            lower.contains("optifine") -> "OptiFine"
            lower.contains("forge") -> "Forge"
            lower.contains("quilt") -> "Quilt"
            else -> "Unknown"
        }
    }
}
