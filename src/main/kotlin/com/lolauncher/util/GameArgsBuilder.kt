package com.lolauncher.util

import com.lolauncher.data.models.VersionJson
import com.lolauncher.data.models.PlayerProfile
import com.lolauncher.service.AuthService
import com.lolauncher.service.OptiFineService
import kotlinx.serialization.json.*
import java.io.File

/**
 * Формирование аргументов запуска Minecraft.
 * Исправляет пустой --assetsDir и другие проблемы старых version JSON.
 */
object GameArgsBuilder {

    private val FLAGS_WITH_VALUE = setOf(
        "--assetsDir", "--assetIndex", "--gameDir", "--version",
        "--accessToken", "--uuid", "--userType", "--userProperties",
        "--session", "--username", "--tweakClass", "--width", "--height"
    )

    /**
     * Собирает игровые аргументы с учётом LaunchWrapper и старых версий.
     */
    fun buildGameArgs(
        versionId: String,
        versionJson: VersionJson,
        profile: PlayerProfile,
        minecraftDir: File
    ): List<String> {
        if (usesLaunchWrapper(versionJson)) {
            return buildLaunchWrapperArgs(versionId, versionJson, profile, minecraftDir)
        }

        val assetsDir = resolveAssetsDir(versionId, minecraftDir, versionJson)
        val vars = buildVariableMap(versionId, versionJson, profile, minecraftDir, assetsDir)

        val args = mutableListOf<String>()

        if (versionJson.arguments != null && versionJson.arguments.game.isNotEmpty()) {
            versionJson.arguments.game.forEach { element ->
                parseArgumentElement(element, vars)?.let { args.addAll(it) }
            }
        } else {
            parseLegacyMinecraftArguments(versionJson.minecraftArguments, vars, args)
        }

        return removeEmptyOptionalGameArgs(
            sanitizeArgs(args, assetsDir, minecraftDir.absolutePath, profile.username)
        )
    }

    /**
     * Аргументы для net.minecraft.launchwrapper.Launch (1.6–1.12 и некоторые старые).
     */
    private fun buildLaunchWrapperArgs(
        versionId: String,
        versionJson: VersionJson,
        profile: PlayerProfile,
        minecraftDir: File
    ): List<String> {
        val assetsDir = resolveAssetsDir(versionId, minecraftDir, versionJson)
        val gameDir = minecraftDir.absolutePath

        val args = mutableListOf<String>()

        // Старый формат: ник первым аргументом без --username
        args.add(profile.username)
        args.add("--gameDir")
        args.add(gameDir)
        args.add("--assetsDir")
        args.add(assetsDir)
        args.add("--version")
        args.add(versionId)

        val minor = parseMinorVersion(versionId)
        if (minor >= 7 || versionId.contains("w", ignoreCase = true)) {
            args.add("--uuid")
            args.add(profile.uuid)
            args.add("--accessToken")
            args.add(profile.accessToken)
            args.add("--userType")
            args.add(if (profile.isOffline) "legacy" else "mojang")
        }

        // Forge / OptiFine tweak classes из version JSON
        collectTweakClasses(versionJson).forEach { tweak ->
            if (!args.contains(tweak)) {
                args.add("--tweakClass")
                args.add(tweak)
            }
        }

        return sanitizeArgs(args, assetsDir, gameDir, profile.username)
    }

    private fun usesLaunchWrapper(versionJson: VersionJson): Boolean =
        versionJson.mainClass.contains("launchwrapper", ignoreCase = true)

    private fun collectTweakClasses(versionJson: VersionJson): List<String> {
        val tweaks = mutableListOf<String>()
        versionJson.arguments?.game?.forEach { element ->
            val parsed = parseArgumentElement(element, emptyMap()) ?: return@forEach
            parsed.forEachIndexed { index, arg ->
                if (arg == "--tweakClass" && index + 1 < parsed.size) {
                    tweaks.add(parsed[index + 1])
                }
            }
        }
        if (versionJson.minecraftArguments?.contains("tweakClass") == true) {
            """--tweakClass\s+(\S+)""".toRegex().findAll(versionJson.minecraftArguments!!)
                .forEach { tweaks.add(it.groupValues[1]) }
        }
        return tweaks
    }

    /**
     * Папка ассетов: до 1.6 — resources, иначе assets.
     */
    fun resolveAssetsDir(versionId: String, minecraftDir: File, versionJson: VersionJson): String {
        val assetsDir = File(minecraftDir, "assets")
        val resourcesDir = File(minecraftDir, "resources")

        val minor = parseMinorVersion(resolveBaseVersionId(versionId))

        assetsDir.mkdirs()

        return when {
            minor in 0..5 -> {
                resourcesDir.mkdirs()
                if (resourcesDir.listFiles()?.isNotEmpty() == true) resourcesDir.absolutePath
                else assetsDir.absolutePath
            }
            assetsDir.exists() -> assetsDir.absolutePath
            versionJson.assets.isNotBlank() -> {
                assetsDir.mkdirs()
                assetsDir.absolutePath
            }
            resourcesDir.exists() -> resourcesDir.absolutePath
            else -> {
                assetsDir.mkdirs()
                assetsDir.absolutePath
            }
        }
    }

    private fun buildVariableMap(
        versionId: String,
        versionJson: VersionJson,
        profile: PlayerProfile,
        minecraftDir: File,
        assetsDir: String
    ): Map<String, String> {
        val gameDir = minecraftDir.absolutePath
        val base = mapOf(
            "\${auth_player_name}" to profile.username,
            "\${version_name}" to versionId,
            "\${game_directory}" to gameDir,
            "\${game_dir}" to gameDir,
            "\${assets_root}" to assetsDir,
            "\${assets_directory}" to assetsDir,
            "\${assetDirectory}" to assetsDir,
            "\${assets_dir}" to assetsDir,
            "\${assets_index_name}" to versionJson.assets.ifBlank { "legacy" },
            "\${auth_uuid}" to profile.uuid,
            "\${auth_access_token}" to profile.accessToken,
            "\${user_type}" to if (profile.isOffline) "legacy" else "mojang",
            "\${userType}" to if (profile.isOffline) "legacy" else "mojang",
            "\${version_type}" to versionJson.type,
            "\${clientid}" to AuthService.MICROSOFT_CLIENT_ID,
            "\${auth_xuid}" to "",
            "\${user_properties}" to "{}",
            "\${userProperties}" to "{}"
        )
        return base
    }

    private fun parseLegacyMinecraftArguments(
        template: String?,
        vars: Map<String, String>,
        args: MutableList<String>
    ) {
        if (template.isNullOrBlank()) return
        // Разбиваем с учётом кавычек
        template.split(Regex("\\s+")).forEach { token ->
            var resolved = token
            vars.forEach { (key, value) -> resolved = resolved.replace(key, value) }
            if (resolved.isNotBlank() && !resolved.startsWith("\${")) {
                args.add(resolved)
            }
        }
    }

    /** Парсит один элемент arguments (game/jvm) из version JSON */
    fun parseArgumentElement(element: JsonElement, vars: Map<String, String>): List<String>? {
        return when {
            element is JsonPrimitive && element.isString -> {
                val resolved = replaceVars(element.content, vars)
                if (resolved.isNotBlank() && !resolved.startsWith("\${")) listOf(resolved) else null
            }
            element is JsonArray -> {
                element.mapNotNull { item ->
                    if (item is JsonPrimitive && item.isString) {
                        val r = replaceVars(item.content, vars)
                        if (r.isNotBlank() && !r.startsWith("\${")) r else null
                    } else null
                }.takeIf { it.isNotEmpty() }
            }
            element is JsonObject -> {
                val rules = element["rules"]?.jsonArray
                if (rules != null && !checkRules(rules)) return null
                val value = element["value"]
                when {
                    value is JsonPrimitive && value.isString -> {
                        val r = replaceVars(value.content, vars)
                        if (r.isNotBlank() && !r.startsWith("\${")) listOf(r) else null
                    }
                    value is JsonArray ->
                        value.mapNotNull { v ->
                            if (v is JsonPrimitive && v.isString) {
                                val r = replaceVars(v.content, vars)
                                if (r.isNotBlank() && !r.startsWith("\${")) r else null
                            } else null
                        }.takeIf { it.isNotEmpty() }
                    else -> null
                }
            }
            else -> null
        }
    }

    /**
     * Убирает «висячие» флаги и подставляет значения по умолчанию.
     */
    fun sanitizeArgs(
        args: List<String>,
        assetsDir: String,
        gameDir: String,
        username: String
    ): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            if (isFlag(arg)) {
                result.add(arg)
                if (i + 1 < args.size && !isFlag(args[i + 1])) {
                    result.add(args[i + 1])
                    i += 2
                } else {
                    result.add(defaultForFlag(arg, assetsDir, gameDir, username))
                    i += 1
                }
            } else {
                result.add(arg)
                i += 1
            }
        }
        return result
    }

    private fun isFlag(arg: String): Boolean =
        arg.startsWith("--") || arg in FLAGS_WITH_VALUE

    private fun defaultForFlag(flag: String, assetsDir: String, gameDir: String, username: String): String =
        when {
            flag.equals("--assetsDir", ignoreCase = true) -> assetsDir
            flag.equals("--gameDir", ignoreCase = true) -> gameDir
            flag.equals("--username", ignoreCase = true) -> username
            else -> ""
        }

    private fun replaceVars(text: String, vars: Map<String, String>): String {
        var result = text
        vars.forEach { (key, value) -> result = result.replace(key, value) }
        // Оставшиеся ${...}/suffix — подставляем game_directory
        if (result.contains("\${game_directory}")) {
            result = result.replace("\${game_directory}", vars["\${game_directory}"] ?: "")
        }
        return result
    }

    /** Правила Mojang: применяется последнее совпавшее правило; без совпадения — не включать аргумент */
    fun checkRules(rules: JsonArray): Boolean {
        var allowed = false
        var matched = false
        rules.forEach { rule ->
            val obj = rule.jsonObject
            val action = obj["action"]?.jsonPrimitive?.content ?: return@forEach
            val os = obj["os"]?.jsonObject?.get("name")?.jsonPrimitive?.content
            val applies = os == null || os == PlatformUtils.osName
            if (applies) {
                matched = true
                allowed = action == "allow"
            }
        }
        return matched && allowed
    }

    /** Убирает JVM-флаги чужой ОС и пустые значения game-аргументов */
    fun filterJvmArgsForCurrentOs(args: List<String>): List<String> {
        val isMac = PlatformUtils.currentOS == PlatformUtils.OS.MACOS
        val macOnly = setOf("-XstartOnFirstThread")
        val winOnly = setOf("-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump")

        return args.filter { arg ->
            when {
                !isMac && arg in macOnly -> false
                isMac && arg in winOnly -> false
                else -> true
            }
        }
    }

    fun removeEmptyOptionalGameArgs(args: List<String>): List<String> {
        val skipIfEmpty = setOf("--width", "--height", "--quickPlayPath", "--quickPlaySingleplayer", "--quickPlayMultiplayer")
        val result = mutableListOf<String>()
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            if (arg in skipIfEmpty && i + 1 < args.size && args[i + 1].isBlank()) {
                i += 2
                continue
            }
            if (arg == "--demo" && i + 1 < args.size && args[i + 1].isBlank()) {
                i += 2
                continue
            }
            if (arg.isBlank()) {
                i++
                continue
            }
            result.add(arg)
            i++
        }
        return result
    }

    private fun parseMinorVersion(versionId: String): Int {
        val numeric = versionId.filter { it.isDigit() || it == '.' }
        val parts = numeric.split(".").filter { it.isNotEmpty() }
        return parts.getOrNull(1)?.toIntOrNull() ?: 0
    }

    private fun resolveBaseVersionId(versionId: String): String = when {
        versionId.startsWith("fabric-loader-") -> versionId.substringAfterLast("-")
        versionId.startsWith("forge-") -> versionId.removePrefix("forge-")
        versionId.startsWith(OptiFineService.OPTIFINE_PREFIX) ->
            versionId.removePrefix(OptiFineService.OPTIFINE_PREFIX).split(OptiFineService.SEP).firstOrNull() ?: versionId
        else -> versionId
    }
}
