package com.lolauncher.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Модели данных для работы с Mojang API, настройками и состоянием лаунчера.
 */

/** Тип версии Minecraft для фильтрации в UI */
enum class VersionType(val displayName: String) {
    RELEASE("Release"),
    SNAPSHOT("Snapshot"),
    OLD_BETA("Old Beta"),
    OLD_ALPHA("Old Alpha"),
    FORGE("Forge"),
    FABRIC("Fabric"),
    OPTIFINE("OptiFine"),
    QUILT("Quilt"),
    INSTALLED("Установленные"),
    ALL("Все")
}

/** Элемент списка версий в UI */
data class VersionItem(
    val id: String,
    val type: VersionType,
    val releaseTime: String = "",
    val isInstalled: Boolean = false,
    val modLoader: String? = null,
    /** Название в UI, например «Forge 1.12.2» */
    val displayName: String = id,
    /** Уникальный ключ для LazyColumn (гарантированно уникален) */
    val uniqueKey: String = id
)

/** Ответ Mojang version_manifest_v2.json */
@Serializable
data class VersionManifest(
    val latest: LatestVersions,
    val versions: List<ManifestVersion>
)

@Serializable
data class LatestVersions(
    val release: String,
    val snapshot: String
)

@Serializable
data class ManifestVersion(
    val id: String,
    val type: String,
    val url: String,
    @SerialName("releaseTime") val releaseTime: String = "",
    @SerialName("time") val time: String = ""
)

/** Полная информация о версии (version JSON) */
@Serializable
data class VersionJson(
    val id: String,
    val type: String = "release",
    @SerialName("inheritsFrom") val inheritsFrom: String? = null,
    val mainClass: String = "",
    val assets: String = "",
    @SerialName("minecraftArguments") val minecraftArguments: String? = null,
    val arguments: Arguments? = null,
    val libraries: List<Library> = emptyList(),
    val downloads: Downloads? = null,
    @SerialName("assetIndex") val assetIndex: AssetIndex? = null,
    @SerialName("javaVersion") val javaVersion: JavaVersionInfo? = null
)

@Serializable
data class Arguments(
    val game: List<kotlinx.serialization.json.JsonElement> = emptyList(),
    val jvm: List<kotlinx.serialization.json.JsonElement> = emptyList()
)

@Serializable
data class Library(
    val name: String,
    /** Базовый URL репозитория (старые версии, напр. 1.0–1.5) */
    val url: String? = null,
    val downloads: LibraryDownloads? = null,
    val rules: List<Rule>? = null,
    val natives: Map<String, String>? = null,
    @SerialName("extract") val extract: ExtractInfo? = null
)

@Serializable
data class Rule(
    val action: String,
    val os: OsRule? = null
)

@Serializable
data class OsRule(
    val name: String? = null,
    val arch: String? = null
)

@Serializable
data class LibraryDownloads(
    val artifact: Artifact? = null,
    val classifiers: Map<String, Artifact>? = null
)

@Serializable
data class Artifact(
    /** Может отсутствовать в старых version JSON (1.0, 1.1 и т.д.) */
    val path: String = "",
    val url: String = "",
    val sha1: String = "",
    val size: Long = 0
)

@Serializable
data class ExtractInfo(
    val exclude: List<String> = emptyList()
)

@Serializable
data class Downloads(
    val client: Artifact? = null
)

@Serializable
data class AssetIndex(
    val id: String,
    val sha1: String = "",
    val size: Long = 0,
    val url: String = "",
    @SerialName("totalSize") val totalSize: Long = 0
)

@Serializable
data class JavaVersionInfo(
    val component: String = "jre-legacy",
    val majorVersion: Int = 8
)

/** Тип аккаунта в менеджере */
enum class AccountType(val displayName: String) {
    OFFLINE("Offline"),
    MICROSOFT("Microsoft"),
    ELY_BY("Ely.by")
}

/** Сохранённый аккаунт (без токенов — они в SecureTokenStorage) */
@Serializable
data class StoredAccount(
    val id: String,
    val username: String,
    val uuid: String,
    val type: String = AccountType.OFFLINE.name,
    val useElySkin: Boolean = true
)

@Serializable
data class AccountsStore(
    val accounts: List<StoredAccount> = emptyList(),
    val activeAccountId: String? = null
)

/** Текстуры скина и плаща */
data class SkinTextures(
    val skinBytes: ByteArray? = null,
    val cloakBytes: ByteArray? = null,
    val previewBytes: ByteArray? = null,
    val source: String = "none"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SkinTextures) return false
        return source == other.source
    }

    override fun hashCode(): Int = source.hashCode()
}

/** Профиль игрока (офлайн или Microsoft) */
data class PlayerProfile(
    val username: String,
    val uuid: String,
    val accessToken: String = "offline_token",
    val isOffline: Boolean = true,
    val accountType: AccountType = AccountType.OFFLINE,
    val accountId: String? = null,
    val skinUrl: String? = null,
    val skinBytes: ByteArray? = null,
    val cloakBytes: ByteArray? = null,
    val previewBytes: ByteArray? = null
)

/** Ответ Mojang API для получения UUID по нику */
@Serializable
data class MojangProfile(
    val id: String,
    val name: String
)

/** Установленный мод */
data class ModInfo(
    val fileName: String,
    val filePath: String,
    val modLoader: String,
    val fileSize: Long = 0,
    val displayName: String = fileName,
    val version: String = "",
    val description: String = "",
    val iconBytes: ByteArray? = null
)

/** Установленный ресурс-пак */
data class ResourcePackInfo(
    val fileName: String,
    val filePath: String,
    val fileSize: Long = 0,
    val displayName: String = fileName,
    val description: String = "",
    val packFormat: Int = 0,
    val iconBytes: ByteArray? = null
)

/** Тип загрузчика для отображения в UI */
enum class LoaderKind(val label: String) {
    VANILLA("Vanilla"),
    FORGE("Forge"),
    FABRIC("Fabric"),
    OPTIFINE("OptiFine")
}

/** Действие лаунчера при запуске Minecraft */
enum class LauncherOnGameLaunch(val displayName: String) {
    NOTHING("Ничего не делать"),
    HIDE("Скрыть LoLauncher"),
    CLOSE("Закрыть LoLauncher")
}

/** Настройки лаунчера (сохраняются в JSON) */
@Serializable
data class LauncherSettings(
    val username: String = "Player",
    val minecraftDir: String = "",
    val selectedVersion: String = "",
    val ramMinMb: Int = 1024,
    val ramMaxMb: Int = 4096,
    val isDarkTheme: Boolean = true,
    val javaPath: String = "",
    val autoJavaInstall: Boolean = true,
    val disableAnimations: Boolean = false,
    val lastVersionType: String = VersionType.RELEASE.name,
    val windowWidth: Int = 1200,
    val windowHeight: Int = 750,
    val showSnapshots: Boolean = true,
    val launcherOnGameLaunch: String = LauncherOnGameLaunch.NOTHING.name,
    val askLauncherOnGameLaunch: Boolean = true,
    val showLaunchConsole: Boolean = false,
    /** Использовать своё фото вместо скина Ely.by */
    val useCustomAvatar: Boolean = false
)

/** Запись в консоли запуска */
data class LaunchLogEntry(
    val timestamp: String,
    val level: String,
    val message: String
)

/** Результат предзапусковой проверки */
data class LaunchDiagnostic(
    val ok: Boolean,
    val checks: List<LaunchCheck> = emptyList(),
    val errorSummary: String? = null
)

data class LaunchCheck(
    val name: String,
    val passed: Boolean,
    val detail: String
)

/** Состояние загрузки */
data class DownloadProgress(
    val fileName: String = "",
    val currentBytes: Long = 0,
    val totalBytes: Long = 0,
    val status: DownloadStatus = DownloadStatus.IDLE,
    val message: String = ""
) {
    val percent: Float
        get() = if (totalBytes > 0) (currentBytes.toFloat() / totalBytes) * 100f else 0f
}

enum class DownloadStatus {
    IDLE, DOWNLOADING, EXTRACTING, VERIFYING, COMPLETE, ERROR
}

/** Статус запуска игры */
enum class LaunchStatus {
    IDLE, PREPARING, DOWNLOADING, INSTALLING, LAUNCHING, RUNNING, ERROR, STOPPED
}

/** Информация о Java */
data class JavaInfo(
    val path: String,
    val version: String,
    val majorVersion: Int,
    val isValid: Boolean
)
