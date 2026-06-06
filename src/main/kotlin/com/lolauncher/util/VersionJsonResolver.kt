package com.lolauncher.util

import com.lolauncher.data.models.Arguments
import com.lolauncher.data.models.VersionJson
import com.lolauncher.service.FabricService
import com.lolauncher.service.ForgeService
import com.lolauncher.service.OptiFineService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File

/**
 * Разрешение цепочки inheritsFrom и слияние version JSON (Forge/Fabric/Vanilla).
 */
object VersionJsonResolver {

    private val json = Json { ignoreUnknownKeys = true }

    fun loadLocalOrFetch(
        versionId: String,
        minecraftDir: File,
        fetcher: (String) -> VersionJson
    ): VersionJson {
        val local = File(minecraftDir, "versions/$versionId/$versionId.json")
        val base = if (local.exists()) {
            json.decodeFromString(VersionJson.serializer(), local.readText())
        } else {
            fetcher(versionId)
        }
        return resolveInheritance(base, minecraftDir, fetcher)
    }

    fun resolveInheritance(
        versionJson: VersionJson,
        minecraftDir: File,
        fetcher: (String) -> VersionJson
    ): VersionJson {
        val parentId = versionJson.inheritsFrom ?: return versionJson
        val parent = loadParent(parentId, minecraftDir, fetcher)
        val mergedParent = resolveInheritance(parent, minecraftDir, fetcher)
        return merge(mergedParent, versionJson)
    }

    private fun loadParent(
        parentId: String,
        minecraftDir: File,
        fetcher: (String) -> VersionJson
    ): VersionJson {
        val local = File(minecraftDir, "versions/$parentId/$parentId.json")
        return if (local.exists()) {
            json.decodeFromString(VersionJson.serializer(), local.readText())
        } else {
            fetcher(parentId)
        }
    }

    fun merge(parent: VersionJson, child: VersionJson): VersionJson = child.copy(
        libraries = dedupeLibraries(parent.libraries + child.libraries),
        arguments = mergeArguments(parent.arguments, child.arguments),
        mainClass = child.mainClass.ifBlank { parent.mainClass },
        assets = child.assets.ifBlank { parent.assets },
        assetIndex = child.assetIndex ?: parent.assetIndex,
        downloads = child.downloads ?: parent.downloads,
        javaVersion = child.javaVersion ?: parent.javaVersion,
        minecraftArguments = child.minecraftArguments ?: parent.minecraftArguments
    )

    private fun dedupeLibraries(libraries: List<com.lolauncher.data.models.Library>): List<com.lolauncher.data.models.Library> {
        val seen = mutableSetOf<String>()
        return libraries.filter { lib ->
            if (lib.name in seen) false else {
                seen.add(lib.name)
                true
            }
        }
    }

    private fun mergeArguments(parent: Arguments?, child: Arguments?): Arguments? {
        if (parent == null) return child
        if (child == null) return parent
        return Arguments(
            game = parent.game + child.game,
            jvm = parent.jvm + child.jvm
        )
    }

    fun resolveBaseMcVersion(versionId: String): String = when {
        versionId.startsWith("fabric-loader-") ->
            FabricService().parseFabricVersionId(versionId)?.second ?: versionId
        versionId.startsWith("forge-") -> ForgeService().resolveMcVersion(versionId)
        versionId.startsWith(OptiFineService.OPTIFINE_PREFIX) ->
            versionId.removePrefix(OptiFineService.OPTIFINE_PREFIX).split(OptiFineService.SEP).firstOrNull() ?: versionId
        else -> versionId
    }

    fun detectLoaderKind(versionId: String, versionJson: VersionJson): com.lolauncher.data.models.LoaderKind {
        return when {
            versionId.startsWith(OptiFineService.OPTIFINE_PREFIX) -> com.lolauncher.data.models.LoaderKind.OPTIFINE
            versionId.startsWith("forge-") ||
                versionJson.libraries.any { it.name.contains("minecraftforge", ignoreCase = true) } ->
                com.lolauncher.data.models.LoaderKind.FORGE
            versionId.startsWith("fabric-loader-") ||
                versionJson.libraries.any { it.name.contains("fabric", ignoreCase = true) } ->
                com.lolauncher.data.models.LoaderKind.FABRIC
            else -> com.lolauncher.data.models.LoaderKind.VANILLA
        }
    }
}
