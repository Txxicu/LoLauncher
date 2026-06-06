package com.lolauncher.service

import com.lolauncher.data.models.ResourcePackInfo
import com.lolauncher.util.JarMetadataReader
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Управление ресурс-паками в .minecraft/resourcepacks.
 */
class ResourcePackService(private val minecraftDir: File) {

    private val scanCache = ConcurrentHashMap<String, Pair<Long, ResourcePackInfo>>()

    fun getInstalledResourcePacks(): List<ResourcePackInfo> {
        val dir = resourcePacksDir()
        if (!dir.exists()) return emptyList()

        val files = dir.listFiles()
            ?.filter { it.isFile && (it.extension.equals("zip", true) || it.name.endsWith(".zip.disabled")) }
            ?: return emptyList()

        return files.parallelStream()
            .map { file ->
                val mtime = file.lastModified()
                val cached = scanCache[file.absolutePath]
                if (cached != null && cached.first == mtime) {
                    cached.second
                } else {
                    val info = JarMetadataReader.readResourcePack(file)
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

  fun deleteResourcePack(filePath: String): Boolean =
      try {
        File(filePath).delete()
      } catch (_: Exception) {
        false
      }

  fun resourcePacksDir(): File = File(minecraftDir, "resourcepacks").also { it.mkdirs() }

  fun openResourcePacksFolder(): File = resourcePacksDir()

  fun importResourcePack(sourceFile: File): Boolean =
      try {
        val target = File(resourcePacksDir(), sourceFile.name)
        sourceFile.copyTo(target, overwrite = true)
        true
      } catch (_: Exception) {
        false
      }
}
