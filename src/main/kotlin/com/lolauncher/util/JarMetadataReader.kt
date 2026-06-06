package com.lolauncher.util

import com.lolauncher.data.models.ModInfo
import com.lolauncher.data.models.ResourcePackInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.zip.ZipFile

/**
 * Чтение метаданных из .jar модов и .zip ресурс-паков.
 */
object JarMetadataReader {

    private val json = Json { ignoreUnknownKeys = true }

    fun readMod(file: File): ModInfo {
        val base = ModInfo(
            fileName = file.name,
            filePath = file.absolutePath,
            modLoader = detectModLoader(file.name),
            fileSize = file.length(),
            displayName = file.nameWithoutExtension
        )
        if (!file.exists()) return base

        return try {
            ZipFile(file).use { zip ->
                val entryNames = zip.entries().asSequence().map { it.name }.toSet()

                val fabric = if ("fabric.mod.json" in entryNames) readZipText(zip, "fabric.mod.json") else null
                if (fabric != null) {
                    val root = json.parseToJsonElement(fabric).jsonObject
                    return base.copy(
                        displayName = root["name"]?.jsonPrimitive?.content ?: base.displayName,
                        version = root["version"]?.jsonPrimitive?.content ?: "",
                        description = root["description"]?.jsonPrimitive?.content ?: "",
                        modLoader = "Fabric",
                        iconBytes = readZipBytes(zip, "assets/${root["id"]?.jsonPrimitive?.content}/icon.png")
                            ?: findFirstIcon(zip)
                    )
                }

                val quilt = if ("quilt.mod.json" in entryNames) readZipText(zip, "quilt.mod.json") else null
                if (quilt != null) {
                    val root = json.parseToJsonElement(quilt).jsonObject
                    val meta = root["metadata"]?.jsonObject
                    return base.copy(
                        displayName = meta?.get("name")?.jsonPrimitive?.content ?: base.displayName,
                        version = meta?.get("version")?.jsonPrimitive?.content ?: "",
                        description = meta?.get("description")?.jsonPrimitive?.content ?: "",
                        modLoader = "Quilt",
                        iconBytes = findFirstIcon(zip)
                    )
                }

                val neoforgeToml = if ("META-INF/neoforge.mods.toml" in entryNames) {
                    readZipText(zip, "META-INF/neoforge.mods.toml")
                } else null
                if (neoforgeToml != null) {
                    val name = """displayName\s*=\s*"(.+?)"""".toRegex().find(neoforgeToml)?.groupValues?.get(1)
                    val version = """version\s*=\s*"(.+?)"""".toRegex().find(neoforgeToml)?.groupValues?.get(1)
                    val desc = """description\s*=\s*'(.+?)'""".toRegex().find(neoforgeToml)?.groupValues?.get(1)
                        ?: """description\s*=\s*"(.+?)"""".toRegex().find(neoforgeToml)?.groupValues?.get(1)
                    return base.copy(
                        displayName = name ?: base.displayName,
                        version = version ?: "",
                        description = desc ?: "",
                        modLoader = "NeoForge",
                        iconBytes = findFirstIcon(zip)
                    )
                }

                val toml = if ("META-INF/mods.toml" in entryNames) readZipText(zip, "META-INF/mods.toml") else null
                if (toml != null) {
                    val name = """displayName\s*=\s*"(.+?)"""".toRegex().find(toml)?.groupValues?.get(1)
                    val version = """version\s*=\s*"(.+?)"""".toRegex().find(toml)?.groupValues?.get(1)
                    val desc = """description\s*=\s*'(.+?)'""".toRegex().find(toml)?.groupValues?.get(1)
                        ?: """description\s*=\s*"(.+?)"""".toRegex().find(toml)?.groupValues?.get(1)
                    return base.copy(
                        displayName = name ?: base.displayName,
                        version = version ?: "",
                        description = desc ?: "",
                        modLoader = "Forge",
                        iconBytes = findFirstIcon(zip)
                    )
                }

                val mcmod = readZipText(zip, "mcmod.info")
                if (mcmod != null) {
                    val name = """"name"\s*:\s*"(.+?)"""".toRegex().find(mcmod)?.groupValues?.get(1)
                    val version = """"version"\s*:\s*"(.+?)"""".toRegex().find(mcmod)?.groupValues?.get(1)
                    val desc = """"description"\s*:\s*"(.+?)"""".toRegex().find(mcmod)?.groupValues?.get(1)
                    return base.copy(
                        displayName = name ?: base.displayName,
                        version = version ?: "",
                        description = desc ?: "",
                        modLoader = "Forge"
                    )
                }

                base
            }
        } catch (_: Exception) {
            base
        }
    }

    fun readResourcePack(file: File): ResourcePackInfo {
        val base = ResourcePackInfo(
            fileName = file.name,
            filePath = file.absolutePath,
            fileSize = file.length(),
            displayName = file.nameWithoutExtension
        )
        if (!file.exists()) return base

        return try {
            ZipFile(file).use { zip ->
                val metaText = readZipText(zip, "pack.mcmeta")
                var name = base.displayName
                var desc = ""
                var format = 0
                if (metaText != null) {
                    val root = json.parseToJsonElement(metaText).jsonObject
                    val pack = root["pack"]?.jsonObject
                    name = pack?.get("description")?.jsonPrimitive?.content?.trim('"') ?: name
                    desc = name
                    format = pack?.get("pack_format")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                }
                base.copy(
                    displayName = name.ifBlank { base.displayName },
                    description = desc,
                    packFormat = format,
                    iconBytes = readZipBytes(zip, "pack.png")
                )
            }
        } catch (_: Exception) {
            base
        }
    }

    private fun readZipText(zip: ZipFile, path: String): String? =
        zip.getEntry(path)?.let { zip.getInputStream(it).bufferedReader().readText() }

    private fun readZipBytes(zip: ZipFile, path: String): ByteArray? =
        zip.getEntry(path)?.let { zip.getInputStream(it).readBytes() }

    private fun findFirstIcon(zip: ZipFile): ByteArray? =
        zip.entries().asSequence()
            .firstOrNull { it.name.endsWith("/icon.png", ignoreCase = true) }
            ?.let { zip.getInputStream(it).readBytes() }

    private fun detectModLoader(fileName: String): String {
        val lower = fileName.lowercase()
        return when {
            lower.contains("fabric") -> "Fabric"
            lower.contains("forge") -> "Forge"
            else -> "Unknown"
        }
    }
}
