package com.lolauncher.service



import com.lolauncher.BuildConfig

import com.lolauncher.data.models.*

import com.lolauncher.util.ArtifactUtils

import com.lolauncher.util.GameArgsBuilder

import com.lolauncher.util.LaunchLogBuffer

import com.lolauncher.util.LogService

import com.lolauncher.util.PlatformUtils

import com.lolauncher.util.VersionJsonResolver

import kotlinx.serialization.json.*

import java.io.BufferedReader

import java.io.File

import java.io.InputStreamReader



/**

 * Сервис запуска Minecraft.

 */

class GameLauncher(private val minecraftDir: File) {



    var onStatusChange: ((LaunchStatus, String) -> Unit)? = null

    var onLogLine: ((String, String) -> Unit)? = null

    private var gameProcess: Process? = null



    fun launch(

        versionId: String,

        versionJson: VersionJson,

        profile: PlayerProfile,

        javaPath: String,

        ramMinMb: Int,

        ramMaxMb: Int,

        optiFineJar: File? = null,

        versionFetcher: (String) -> VersionJson = { id -> versionJson }

    ): String? {

        onStatusChange?.invoke(LaunchStatus.PREPARING, "Подготовка к запуску...")

        log("INFO", "Подготовка запуска $versionId")



        val mergedJson = VersionJsonResolver.resolveInheritance(versionJson, minecraftDir, versionFetcher)

        val loader = VersionJsonResolver.detectLoaderKind(versionId, mergedJson)

        log("INFO", "Тип профиля: ${loader.label}, mainClass: ${mergedJson.mainClass}")



        val versionDir = File(minecraftDir, "versions/$versionId")

        val clientJar = resolveClientJar(versionId, mergedJson, versionDir)



        if (!clientJar.exists()) {

            val msg = "Client.jar не найден: ${clientJar.absolutePath}"

            log("ERROR", msg)

            onStatusChange?.invoke(LaunchStatus.ERROR, msg)

            return msg

        }



        if (!File(javaPath).exists() && javaPath != "java") {

            val msg = "Java не найдена: $javaPath"

            log("ERROR", msg)

            onStatusChange?.invoke(LaunchStatus.ERROR, msg)

            return msg

        }



        val nativesDir = File(versionDir, "natives")

        if (!nativesDir.exists() || nativesDir.listFiles().isNullOrEmpty()) {

            val baseId = mergedJson.inheritsFrom

            if (!baseId.isNullOrBlank()) {

                val parentNatives = File(minecraftDir, "versions/$baseId/natives")

                if (parentNatives.exists()) nativesDir.mkdirs()

            }

        }

        nativesDir.mkdirs()



        val classpath = buildClasspath(mergedJson, clientJar, optiFineJar, versionDir)

        if (classpath.isBlank()) {

            val msg = "Classpath пуст — библиотеки не найдены"

            log("ERROR", msg)

            onStatusChange?.invoke(LaunchStatus.ERROR, msg)

            return msg

        }

        log("INFO", "Classpath: ${classpath.split(File.pathSeparator).size} элементов")



        val jvmArgs = buildJvmArgs(mergedJson, ramMinMb, ramMaxMb, nativesDir.absolutePath, classpath)

        val gameArgs = GameArgsBuilder.buildGameArgs(versionId, mergedJson, profile, minecraftDir)

        val assetsDir = gameArgs.getOrNull(gameArgs.indexOf("--assetsDir") + 1) ?: ""

        if (assetsDir.isBlank()) {

            val msg = "assetsDir пуст — запуск отменён"

            log("ERROR", msg)

            onStatusChange?.invoke(LaunchStatus.ERROR, msg)

            return msg

        }

        log("INFO", "assetsDir: $assetsDir")



        val mainClass = mergedJson.mainClass.ifBlank { "net.minecraft.client.main.Main" }

        val command = mutableListOf<String>()

        command.add(javaPath)

        command.addAll(jvmArgs)

        command.add(mainClass)

        command.addAll(gameArgs)



        LogService.logLaunchCommand(versionId, command)

        log("INFO", "Команда: ${command.joinToString(" ")}")

        onStatusChange?.invoke(LaunchStatus.LAUNCHING, "Запуск ${loader.label} $versionId...")



        return try {

            val logFile = File(LogService.getGameOutputLogPath())



            val processBuilder = ProcessBuilder(command)

                .directory(minecraftDir)

                .redirectErrorStream(true)



            gameProcess = processBuilder.start()



            Thread({

                try {

                    BufferedReader(InputStreamReader(gameProcess!!.inputStream)).use { reader ->

                        var line: String?

                        while (reader.readLine().also { line = it } != null) {

                            log("GAME", line!!)

                            logFile.appendText("$line\n")

                        }

                    }

                } catch (e: Exception) {

                    log("ERROR", "Ошибка чтения вывода: ${e.message}")

                }

            }, "GameOutputReader").apply { isDaemon = true; start() }



            Thread.sleep(1500)

            if (gameProcess?.isAlive == false) {

                val exitCode = gameProcess?.exitValue() ?: -1

                val lastLines = LaunchLogBuffer.getLastLines(10).joinToString("\n") { it.message }

                val msg = "Minecraft завершился сразу (код $exitCode).\n$lastLines"

                log("ERROR", msg)

                onStatusChange?.invoke(LaunchStatus.ERROR, msg)

                return msg

            }



            Thread({

                try {

                    val exitCode = gameProcess?.waitFor() ?: -1

                    log("INFO", "Minecraft завершён с кодом $exitCode")

                    onStatusChange?.invoke(LaunchStatus.STOPPED, "Minecraft завершён (код $exitCode)")

                } catch (e: Exception) {

                    log("ERROR", "Ошибка мониторинга: ${e.message}")

                }

            }, "GameProcessMonitor").apply { isDaemon = true; start() }



            onStatusChange?.invoke(LaunchStatus.RUNNING, "Minecraft запущен!")

            null

        } catch (e: Exception) {

            val msg = "Ошибка запуска: ${e.message}"

            log("ERROR", msg)

            onStatusChange?.invoke(LaunchStatus.ERROR, msg)

            msg

        }

    }



    private fun log(level: String, message: String) {

        LaunchLogBuffer.add(level, message)

        onLogLine?.invoke(level, message)

    }



    fun stop() {

        gameProcess?.destroyForcibly()

        gameProcess = null

        onStatusChange?.invoke(LaunchStatus.STOPPED, "Игра остановлена")

    }



    fun isRunning(): Boolean = gameProcess?.isAlive == true



    private fun resolveClientJar(versionId: String, merged: VersionJson, versionDir: File): File {

        val local = File(versionDir, "$versionId.jar")

        if (local.exists() && local.length() > 1024) return local

        merged.inheritsFrom?.let { parentId ->

            val parentJar = File(minecraftDir, "versions/$parentId/$parentId.jar")

            if (parentJar.exists()) {

                versionDir.mkdirs()

                parentJar.copyTo(local, overwrite = true)

                return local

            }

        }

        return local

    }



    private fun buildClasspath(

        versionJson: VersionJson,

        clientJar: File,

        optiFineJar: File?,

        versionDir: File

    ): String {

        val paths = mutableListOf<String>()

        val librariesDir = File(minecraftDir, "libraries")

        val downloadService = DownloadService(minecraftDir)



        versionJson.libraries.forEach { library ->

            if (!downloadService.isLibraryAllowed(library)) return@forEach



            ArtifactUtils.resolveAllLibraryArtifacts(library).forEach { resolved ->
                val libFile = File(librariesDir, resolved.path)
                if (libFile.exists()) paths.add(libFile.absolutePath)
            }



            val natives = library.natives

            if (natives != null) {

                val nativeKey = PlatformUtils.nativeKey

                val classifier = natives[nativeKey]?.replace("\${arch}", PlatformUtils.osArch)

                if (classifier != null) {

                    val nativeArtifact = library.downloads?.classifiers?.get(classifier)

                    if (nativeArtifact != null) {

                        val resolved = ArtifactUtils.resolveClassifierArtifact(library, classifier, nativeArtifact)

                        val nativeFile = File(librariesDir, resolved.path)

                        if (nativeFile.exists()) paths.add(nativeFile.absolutePath)

                    }

                }

            }

        }



        optiFineJar?.let { if (it.exists()) paths.add(it.absolutePath) }

        val localOptiFine = File(versionDir, "OptiFine.jar")

        if (localOptiFine.exists() && paths.none { it.contains("OptiFine", true) }) {

            paths.add(localOptiFine.absolutePath)

        }

        if (clientJar.exists()) paths.add(clientJar.absolutePath)



        return paths.filter { File(it).exists() }.joinToString(File.pathSeparator)

    }



    private fun buildJvmArgs(

        versionJson: VersionJson,

        ramMinMb: Int,

        ramMaxMb: Int,

        nativesPath: String,

        classpath: String

    ): List<String> {

        val args = mutableListOf<String>()



        args.add("-Xms${ramMinMb}M")

        args.add("-Xmx${ramMaxMb}M")

        args.add("-Djava.library.path=$nativesPath")

        args.add("-Dminecraft.launcher.brand=${BuildConfig.APP_NAME}")

        args.add("-Dminecraft.launcher.version=${BuildConfig.VERSION_NAME}")



        val jvmVars = mapOf(

            "\${natives_directory}" to nativesPath,

            "\${launcher_name}" to BuildConfig.APP_NAME,

            "\${launcher_version}" to BuildConfig.VERSION_NAME,

            "\${classpath}" to classpath,

            "\${library_directory}" to File(minecraftDir, "libraries").absolutePath,

            "\${version_name}" to versionJson.id

        )



        var hasClasspath = false

        versionJson.arguments?.jvm?.forEach { element ->

            GameArgsBuilder.parseArgumentElement(element, jvmVars)?.let { parsed ->

                args.addAll(parsed)

                if (parsed.any { it == "-cp" || it == "--classpath" }) hasClasspath = true

            }

        }



        if (!hasClasspath) {

            args.add("-cp")

            args.add(classpath)

        }



        return dedupeJvmArgs(GameArgsBuilder.filterJvmArgsForCurrentOs(args))

    }

    private fun dedupeJvmArgs(args: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<String>()
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            val key = when {
                arg.startsWith("-Xms") -> "-Xms"
                arg.startsWith("-Xmx") -> "-Xmx"
                arg.startsWith("-Djava.library.path=") -> "-Djava.library.path"
                arg.startsWith("-Dminecraft.launcher.brand=") -> "-Dminecraft.launcher.brand"
                arg.startsWith("-Dminecraft.launcher.version=") -> "-Dminecraft.launcher.version"
                arg == "-cp" || arg == "--classpath" -> "-cp"
                else -> arg
            }
            if (key in seen) {
                if (key == "-cp" && i + 1 < args.size) i += 2 else i += 1
                continue
            }
            seen.add(key)
            result.add(arg)
            if ((arg == "-cp" || arg == "--classpath") && i + 1 < args.size) {
                result.add(args[i + 1])
                i += 2
            } else {
                i += 1
            }
        }
        return result
    }

}


