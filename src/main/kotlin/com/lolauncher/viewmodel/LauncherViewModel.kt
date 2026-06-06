package com.lolauncher.viewmodel

import com.lolauncher.data.models.*
import com.lolauncher.service.*
import com.lolauncher.util.AppRestart
import com.lolauncher.ui.components.AvatarOption
import com.lolauncher.util.AvatarStorage
import com.lolauncher.util.JavaVersionResolver
import com.lolauncher.util.LaunchLogBuffer
import com.lolauncher.util.LogService
import com.lolauncher.util.SettingsManager
import com.lolauncher.util.VersionJsonResolver
import java.net.URI
import kotlinx.coroutines.*
import java.awt.Desktop
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileSystemView

/**
 * ViewModel лаунчера — связывает UI с бизнес-логикой.
 */
class LauncherViewModel {

    var settings: LauncherSettings = SettingsManager.load()
        private set

    var versions: List<VersionItem> = emptyList()
        private set

    var filteredVersions: List<VersionItem> = emptyList()
        private set

    var selectedVersion: VersionItem? = null
        private set

    var selectedVersionType: VersionType = try {
        VersionType.valueOf(settings.lastVersionType)
    } catch (_: Exception) {
        VersionType.RELEASE
    }
        private set

    var playerProfile: PlayerProfile? = null
        private set

    var mods: List<ModInfo> = emptyList()
        private set

    var resourcePacks: List<ResourcePackInfo> = emptyList()
        private set

    var downloadProgress: DownloadProgress = DownloadProgress()
        private set

    var launchStatus: LaunchStatus = LaunchStatus.IDLE
        private set

    var launchMessage: String = ""
        private set

    var javaInfo: JavaInfo? = null
        private set

    var isLoading: Boolean = false
        private set

    var isInstalling: Boolean = false
        private set

    var statusMessage: String = "Добро пожаловать в ${com.lolauncher.BuildConfig.APP_NAME}!"
        private set

    var skinImageBytes: ByteArray? = null
        private set

    var showAvatarPicker: Boolean = false
        private set

    var avatarPickerOptions: List<AvatarOption> = emptyList()
        private set

    var accounts: List<StoredAccount> = emptyList()
        private set

    var activeAccountId: String? = null
        private set

    var activeSkinTextures: SkinTextures = SkinTextures()
        private set

    var isAccountAuthInProgress: Boolean = false
        private set

    /** Сообщение об ошибке для диалога */
    var errorDialogMessage: String? = null
        private set

    /** Поиск по списку версий */
    var versionSearchQuery: String = ""
        private set

    /** Состояние диалога входа Microsoft (Device Code) */
    var microsoftAuthState: MicrosoftAuthUiState = MicrosoftAuthUiState()
        private set

    var showLaunchActionDialog: Boolean = false
        private set

    var pendingLaunchAction: LauncherOnGameLaunch = LauncherOnGameLaunch.NOTHING
        private set

    var rememberLaunchActionChoice: Boolean = false
        private set

    var launchConsoleVisible: Boolean = false
        private set

    var launchLogEntries: List<LaunchLogEntry> = emptyList()
        private set

    var launchConsoleError: String? = null
        private set

    /** Действия с окном лаунчера (устанавливаются из Main.kt) */
    var windowActions: LauncherWindowActions? = null

    private var launcherWasHiddenForGame = false

    private val stateListeners = mutableListOf<() -> Unit>()

    private val minecraftDir: File
        get() = File(settings.minecraftDir)

    // Кэшированные сервисы — не пересоздавать, иначе теряется манифест версий
    private var cachedMinecraftDir: String = ""
    private var _versionService: VersionService? = null
    private var _downloadService: DownloadService? = null
    private var _modService: ModService? = null
    private var _gameLauncher: GameLauncher? = null
    private var _optiFineService: OptiFineService? = null
    private var _resourcePackService: ResourcePackService? = null

    private val versionService: VersionService get() = getOrCreateService(
        cachedMinecraftDir, settings.minecraftDir,
        { _versionService }, { _versionService = it }, ::VersionService
    )
    private val downloadService: DownloadService get() = getOrCreateService(
        cachedMinecraftDir, settings.minecraftDir,
        { _downloadService }, { _downloadService = it }, ::DownloadService
    )
    private val modService: ModService get() = getOrCreateService(
        cachedMinecraftDir, settings.minecraftDir,
        { _modService }, { _modService = it }, ::ModService
    )
    private val gameLauncher: GameLauncher get() = getOrCreateService(
        cachedMinecraftDir, settings.minecraftDir,
        { _gameLauncher }, { _gameLauncher = it }, ::GameLauncher
    )
    private val optiFineService: OptiFineService get() = getOrCreateService(
        cachedMinecraftDir, settings.minecraftDir,
        { _optiFineService }, { _optiFineService = it }, ::OptiFineService
    )
    private val resourcePackService: ResourcePackService get() = getOrCreateService(
        cachedMinecraftDir, settings.minecraftDir,
        { _resourcePackService }, { _resourcePackService = it }, ::ResourcePackService
    )

    private val skinService = SkinService()
    private val authService = AuthService(skinService)
    private val accountManager = AccountManager()
    private val javaService = JavaService()
    private val microsoftAuthService = MicrosoftAuthService()
    private var microsoftAuthJob: Job? = null

    private fun <T> getOrCreateService(
        cachedDir: String,
        currentDir: String,
        getter: () -> T?,
        setter: (T?) -> Unit,
        factory: (File) -> T
    ): T {
        if (getter() == null || cachedDir != currentDir) {
            setter(factory(File(currentDir)))
            cachedMinecraftDir = currentDir
        }
        return getter()!!
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        setupCallbacks()
        scope.launch { initialize() }
    }

    fun addStateListener(listener: () -> Unit) {
        stateListeners.add(listener)
    }

    fun removeStateListener(listener: () -> Unit) {
        stateListeners.remove(listener)
    }

    private fun setupCallbacks() {
        downloadService.onProgress = { progress ->
            downloadProgress = progress
            notifyStateChanged()
        }

        gameLauncher.onStatusChange = { status, message ->
            launchStatus = status
            launchMessage = message
            if (status == LaunchStatus.LAUNCHING || status == LaunchStatus.RUNNING) {
                downloadProgress = DownloadProgress(status = DownloadStatus.IDLE)
            }
            if (status == LaunchStatus.RUNNING) {
                applyLauncherOnGameLaunch()
            }
            if (status == LaunchStatus.ERROR) {
                launchConsoleError = message
                if (settings.showLaunchConsole) launchConsoleVisible = true
            }
            if (status == LaunchStatus.STOPPED || status == LaunchStatus.ERROR) {
                restoreLauncherWindowIfNeeded()
            }
            notifyStateChanged()
        }

        gameLauncher.onLogLine = { _, _ ->
            launchLogEntries = LaunchLogBuffer.getEntries()
            notifyStateChanged()
        }

        LaunchLogBuffer.addListener {
            launchLogEntries = LaunchLogBuffer.getEntries()
            notifyStateChanged()
        }

        javaService.onProgress = { message ->
            statusMessage = message
            notifyStateChanged()
        }
    }

    private suspend fun initialize() {
        isLoading = true
        statusMessage = "Инициализация..."
        notifyStateChanged()

        withContext(Dispatchers.IO) {
            javaInfo = javaService.findJava(17, settings.javaPath)
        }

        accountManager.migrateFromLegacyUsername(settings.username)
        refreshAccountsInternal()
        applyActiveAccount()

        refreshAllInternal()

        isLoading = false
        statusMessage = "Готово"
        notifyStateChanged()
    }

    fun refreshAll() {
        scope.launch { refreshAllInternal() }
    }

    private suspend fun refreshAllInternal() {
        isLoading = true
        statusMessage = "Обновление данных..."
        notifyStateChanged()

        try {
                versions = versionService.refreshVersions()

                if (settings.selectedVersion.isNotBlank()) {
                    selectedVersion = versions.find { it.id == settings.selectedVersion }
                }
                applyVersionFilters()
                if (selectedVersion == null && settings.selectedVersion.isNotBlank()) {
                    selectedVersion = filteredVersions.find { it.id == settings.selectedVersion }
                }
            if (selectedVersion == null && filteredVersions.isNotEmpty()) {
                selectedVersion = filteredVersions.first()
            }

            mods = modService.getInstalledMods()
            resourcePacks = resourcePackService.getInstalledResourcePacks()
            refreshAccountsInternal()
            accountManager.getActiveAccount()?.let { account ->
                playerProfile = withContext(Dispatchers.IO) {
                    accountManager.resolveProfile(account)
                }
                activeSkinTextures = withContext(Dispatchers.IO) {
                    accountManager.loadTexturesForAccount(account)
                }
                loadSkinImage()
            }

            javaInfo = javaService.findJava(17, settings.javaPath)
            statusMessage = "Обновлено: ${versions.size} версий, ${mods.size} модов"
        } catch (e: Exception) {
            LogService.error("Ошибка обновления", e)
            statusMessage = "Ошибка обновления: ${e.message}"
        }

        isLoading = false
        notifyStateChanged()
    }

    fun setVersionType(type: VersionType) {
        selectedVersionType = type
        applyVersionFilters()
        settings = settings.copy(lastVersionType = type.name)
        SettingsManager.save(settings)
        notifyStateChanged()
    }

    fun setVersionSearch(query: String) {
        versionSearchQuery = query
        applyVersionFilters()
        notifyStateChanged()
    }

    private fun applyVersionFilters() {
        versionService.refreshInstalledIds()
        var list = when (selectedVersionType) {
            VersionType.INSTALLED -> versionService.scanInstalledVersions()
            else -> versionService.syncInstalledFlags(
                versionService.filterVersions(versions, selectedVersionType)
            )
        }

        if (!settings.showSnapshots && selectedVersionType != VersionType.SNAPSHOT) {
            list = list.filter { it.type != VersionType.SNAPSHOT }
        }

        val query = versionSearchQuery.trim().lowercase()
        if (query.isNotEmpty()) {
            list = list.filter {
                it.id.lowercase().contains(query) ||
                    it.displayName.lowercase().contains(query)
            }
        }
        filteredVersions = versionService.assignUniqueKeys(list)
    }

    fun selectVersion(version: VersionItem) {
        selectedVersion = version
        settings = settings.copy(selectedVersion = version.id)
        SettingsManager.save(settings)
        notifyStateChanged()
    }

    fun deleteVersion(version: VersionItem) {
        if (!version.isInstalled || !versionService.isPhysicallyInstalled(version.id)) {
            showError("Версия «${version.displayName}» не установлена")
            return
        }
        scope.launch {
            isLoading = true
            statusMessage = "Удаление ${version.displayName}..."
            notifyStateChanged()
            val deleted = withContext(Dispatchers.IO) {
                versionService.deleteVersion(version.id)
            }
            if (!deleted) {
                isLoading = false
                showError("Не удалось удалить версию «${version.displayName}»")
                return@launch
            }
            if (selectedVersion?.id == version.id) {
                selectedVersion = null
                settings = settings.copy(selectedVersion = "")
                SettingsManager.save(settings)
            }
            versionService.invalidateInstalledCache()
            versions = versionService.syncInstalledFlags(versions)
            applyVersionFilters()
            if (selectedVersion == null && filteredVersions.isNotEmpty()) {
                selectVersion(filteredVersions.first())
            }
            isLoading = false
            statusMessage = "Версия «${version.displayName}» удалена"
            notifyStateChanged()
        }
    }

    fun isSelectedVersionInstalled(): Boolean {
        val version = selectedVersion ?: return false
        return version.isInstalled && versionService.isPhysicallyInstalled(version.id)
    }

    private fun refreshAccountsInternal() {
        accounts = accountManager.getAccounts()
        activeAccountId = accountManager.getActiveAccount()?.id
    }

    fun applyActiveAccount() {
        scope.launch {
            try {
                val account = accountManager.getActiveAccount()
                if (account != null) {
                    playerProfile = withContext(Dispatchers.IO) {
                        accountManager.resolveProfile(account)
                    }
                    activeSkinTextures = withContext(Dispatchers.IO) {
                        accountManager.loadTexturesForAccount(account)
                    }
                    settings = settings.copy(username = playerProfile?.username ?: settings.username)
                    SettingsManager.save(settings)
                } else {
                    playerProfile = null
                    activeSkinTextures = SkinTextures()
                }
                loadSkinImage()
            } catch (e: Exception) {
                LogService.error("Ошибка активного аккаунта", e)
            }
            refreshAccountsInternal()
            notifyStateChanged()
        }
    }

    fun addOfflineAccount(username: String) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    accountManager.addOfflineAccount(username, AccountType.OFFLINE)
                }
                applyActiveAccount()
                statusMessage = "Аккаунт $username добавлен"
            } catch (e: Exception) {
                showError(e.message ?: "Ошибка добавления аккаунта")
            }
        }
    }

    fun addElyAccount(username: String) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    accountManager.addOfflineAccount(username, AccountType.ELY_BY)
                }
                applyActiveAccount()
                statusMessage = "Ely.by аккаунт $username добавлен"
            } catch (e: Exception) {
                showError(e.message ?: "Ошибка добавления аккаунта")
            }
        }
    }

    fun addMicrosoftAccount() {
        scope.launch {
            isAccountAuthInProgress = true
            statusMessage = "Ожидание авторизации Microsoft..."
            notifyStateChanged()
            try {
                withContext(Dispatchers.IO) {
                    accountManager.addMicrosoftAccount()
                }
                applyActiveAccount()
                statusMessage = "Microsoft аккаунт добавлен"
            } catch (e: Exception) {
                LogService.error("Microsoft OAuth", e)
                showError("Ошибка Microsoft: ${e.message}")
            }
            isAccountAuthInProgress = false
            notifyStateChanged()
        }
    }

    fun activateAccount(accountId: String) {
        accountManager.setActiveAccount(accountId)
        applyActiveAccount()
        statusMessage = "Аккаунт активирован"
    }

    fun editAccount(accountId: String, username: String) {
        scope.launch {
            val updated = withContext(Dispatchers.IO) {
                accountManager.updateAccount(accountId, username)
            }
            if (updated == null) {
                showError("Не удалось обновить аккаунт")
                return@launch
            }
            applyActiveAccount()
            statusMessage = "Аккаунт обновлён"
        }
    }

    fun deleteAccount(accountId: String) {
        accountManager.removeAccount(accountId)
        applyActiveAccount()
        statusMessage = "Аккаунт удалён"
    }

    private fun loadSkinImage() {
        if (settings.useCustomAvatar) {
            skinImageBytes = AvatarStorage.load()
            notifyStateChanged()
            return
        }
        val profile = playerProfile
        if (profile?.previewBytes != null || profile?.skinBytes != null) {
            skinImageBytes = profile.previewBytes ?: profile.skinBytes
            notifyStateChanged()
            return
        }
        scope.launch {
            try {
                skinImageBytes = withContext(Dispatchers.IO) {
                    DefaultAvatarService.loadDefault()
                }
            } catch (e: Exception) {
                LogService.warn("Не удалось загрузить аватар: ${e.message}")
                skinImageBytes = null
            }
            notifyStateChanged()
        }
    }

    fun openAvatarPicker() {
        scope.launch {
            val defaultBytes = withContext(Dispatchers.IO) { DefaultAvatarService.loadDefault() }
            avatarPickerOptions = buildAvatarOptions(defaultBytes)
            showAvatarPicker = true
            notifyStateChanged()
        }
    }

    fun dismissAvatarPicker() {
        showAvatarPicker = false
        notifyStateChanged()
    }

    private fun buildAvatarOptions(defaultBytes: ByteArray?): List<AvatarOption> {
        val customBytes = AvatarStorage.load()
        return listOf(
            AvatarOption(
                id = "default",
                title = "По умолчанию",
                subtitle = "Стандартный аватар LoLauncher",
                imageBytes = defaultBytes,
                isSelected = !settings.useCustomAvatar
            ),
            AvatarOption(
                id = "custom",
                title = if (customBytes != null) "Своё фото" else "Загрузить фото",
                subtitle = if (customBytes != null) "Ваше изображение" else "Выбрать файл с компьютера",
                imageBytes = customBytes,
                isSelected = settings.useCustomAvatar,
                isUpload = customBytes == null
            )
        )
    }

    fun selectAvatarOption(option: AvatarOption) {
        when (option.id) {
            "default" -> resetAvatarToDefault()
            "custom" -> {
                if (AvatarStorage.hasCustomAvatar()) {
                    settings = settings.copy(useCustomAvatar = true)
                    SettingsManager.save(settings)
                    skinImageBytes = AvatarStorage.load()
                    statusMessage = "Используется своё фото"
                    notifyStateChanged()
                } else {
                    pickCustomAvatar()
                }
            }
        }
        dismissAvatarPicker()
    }

    /** Выбор своего фото для аватара */
    fun pickCustomAvatar() {
        try {
            val chooser = JFileChooser()
            chooser.dialogTitle = "Выберите изображение"
            chooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
                "Изображения", "png", "jpg", "jpeg", "gif", "webp"
            )
            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return

            val file = chooser.selectedFile
            if (!AvatarStorage.saveFromFile(file)) {
                showError("Не удалось загрузить изображение")
                return
            }
            settings = settings.copy(useCustomAvatar = true)
            SettingsManager.save(settings)
            skinImageBytes = AvatarStorage.load()
            statusMessage = "Фото профиля обновлено"
            notifyStateChanged()
        } catch (e: Exception) {
            showError("Ошибка загрузки фото: ${e.message}")
        }
    }

    fun resetAvatarToDefault() {
        AvatarStorage.clear()
        settings = settings.copy(useCustomAvatar = false)
        SettingsManager.save(settings)
        DefaultAvatarService.clearCache()
        loadSkinImage()
        statusMessage = "Аватарка сброшена"
        notifyStateChanged()
    }

    fun cancelInstall() {
        if (!isInstalling) return
        installJob?.cancel()
        downloadService.cancelInstall()
        isInstalling = false
        launchStatus = LaunchStatus.IDLE
        launchMessage = "Установка отменена"
        statusMessage = "Установка отменена"
        downloadProgress = DownloadProgress(status = DownloadStatus.IDLE)
        notifyStateChanged()
    }

    private var debounceJob: Job? = null
    private var installJob: Job? = null

    fun updateUsername(username: String) {
        settings = settings.copy(username = username)
        SettingsManager.save(settings)
        notifyStateChanged()
    }

    /** Установка выбранной версии */
    fun installVersion() {
        val version = selectedVersion
        if (version == null) {
            showError("Выберите версию Minecraft")
            return
        }

        if (isInstalling || isLoading) return

        installJob?.cancel()
        installJob = scope.launch {
            try {
                isInstalling = true
                launchStatus = LaunchStatus.INSTALLING
                launchMessage = "Установка ${version.id}..."
                downloadProgress = DownloadProgress(status = DownloadStatus.DOWNLOADING, message = "Подготовка...")
                downloadService.resetCancel()
                notifyStateChanged()

                val versionJson = withContext(Dispatchers.IO) {
                    versionService.fetchVersionJson(version.id)
                }
                ensureActive()

                withContext(Dispatchers.IO) {
                    versionService.ensureBaseVersionInstalled(version.id)
                    ensureActive()
                    downloadService.prepareVersion(version.id, versionJson)
                    ensureActive()
                    versionService.saveVersionJson(version.id, versionJson)

                    if (version.id.startsWith(OptiFineService.OPTIFINE_PREFIX)) {
                        val parsed = optiFineService.parseVersionId(version.id)
                            ?: throw Exception("Некорректный ID OptiFine")
                        val (mcVersion, type, _) = parsed
                        optiFineService.downloadOptiFineJar(version.id, mcVersion, type)
                    }
                }

                versionService.invalidateInstalledCache()
                versions = versionService.syncInstalledFlags(versions)
                applyVersionFilters()
                selectedVersion = versions.find { it.id == version.id }
                    ?: selectedVersion?.copy(isInstalled = true)

                launchStatus = LaunchStatus.IDLE
                launchMessage = "Версия ${version.id} установлена!"
                statusMessage = "Версия ${version.id} успешно установлена"
                downloadProgress = DownloadProgress(status = DownloadStatus.COMPLETE, message = "Установлено")
                LogService.info("Версия ${version.id} установлена")
            } catch (e: CancellationException) {
                downloadService.cancelInstall()
                launchStatus = LaunchStatus.IDLE
                launchMessage = "Установка отменена"
                statusMessage = "Установка отменена"
                downloadProgress = DownloadProgress(status = DownloadStatus.IDLE)
            } catch (e: InstallCancelledException) {
                launchStatus = LaunchStatus.IDLE
                launchMessage = "Установка отменена"
                statusMessage = "Установка отменена"
                downloadProgress = DownloadProgress(status = DownloadStatus.IDLE)
            } catch (e: Exception) {
                LogService.error("Ошибка установки версии ${version.id}", e)
                launchStatus = LaunchStatus.ERROR
                launchMessage = "Ошибка установки: ${e.message}"
                showError("Не удалось установить версию:\n${e.message}")
            } finally {
                isInstalling = false
                installJob = null
                notifyStateChanged()
            }
        }
    }

    fun requestLaunchGame() {
        val version = selectedVersion
        if (version == null) {
            showError("Выберите версию Minecraft")
            return
        }
        if (!versionService.isVersionInstalled(version.id)) {
            showError("Версия не установлена. Нажмите «Установить».")
            return
        }

        pendingLaunchAction = try {
            LauncherOnGameLaunch.valueOf(settings.launcherOnGameLaunch)
        } catch (_: Exception) {
            LauncherOnGameLaunch.NOTHING
        }
        rememberLaunchActionChoice = false

        if (settings.askLauncherOnGameLaunch) {
            showLaunchActionDialog = true
            notifyStateChanged()
        } else {
            launchGame()
        }
    }

    fun updatePendingLaunchAction(action: LauncherOnGameLaunch) {
        pendingLaunchAction = action
        notifyStateChanged()
    }

    fun updateRememberLaunchActionChoice(remember: Boolean) {
        rememberLaunchActionChoice = remember
        notifyStateChanged()
    }

    fun confirmLaunchActionDialog() {
        showLaunchActionDialog = false
        if (rememberLaunchActionChoice) {
            settings = settings.copy(
                launcherOnGameLaunch = pendingLaunchAction.name,
                askLauncherOnGameLaunch = false
            )
            SettingsManager.save(settings)
        }
        launchGame()
    }

    fun dismissLaunchActionDialog() {
        showLaunchActionDialog = false
        notifyStateChanged()
    }

  private fun applyLauncherOnGameLaunch() {
        when (pendingLaunchAction) {
            LauncherOnGameLaunch.HIDE -> {
                windowActions?.hide?.invoke()
                launcherWasHiddenForGame = true
            }
            LauncherOnGameLaunch.CLOSE -> windowActions?.close?.invoke()
            LauncherOnGameLaunch.NOTHING -> Unit
        }
    }

    private fun restoreLauncherWindowIfNeeded() {
        if (launcherWasHiddenForGame) {
            windowActions?.show?.invoke()
            launcherWasHiddenForGame = false
        }
    }

    /** Запуск Minecraft (только если версия установлена) */
    fun launchGame() {
        val version = selectedVersion
        if (version == null) {
            showError("Выберите версию Minecraft")
            return
        }

        if (!versionService.isVersionInstalled(version.id)) {
            showError("Версия не установлена. Нажмите «Установить».")
            return
        }

        val username = settings.username.ifBlank { "Player" }

        scope.launch {
            try {
                val profile = withContext(Dispatchers.IO) {
                    accountManager.resolveActiveProfile()
                        ?: playerProfile
                        ?: skinService.createOfflineProfile(username)
                }
                LaunchLogBuffer.clear()
                launchConsoleError = null
                launchLogEntries = emptyList()
                if (settings.showLaunchConsole) {
                    launchConsoleVisible = true
                }
                launchStatus = LaunchStatus.PREPARING
                launchMessage = "Подготовка к запуску..."
                downloadProgress = DownloadProgress(status = DownloadStatus.IDLE)
                notifyStateChanged()

                val rawJson = withContext(Dispatchers.IO) {
                    val jsonFile = File(minecraftDir, "versions/${version.id}/${version.id}.json")
                    if (jsonFile.exists()) {
                        kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                            .decodeFromString(VersionJson.serializer(), jsonFile.readText())
                    } else {
                        versionService.fetchVersionJson(version.id)
                    }
                }

                val mergedJson = VersionJsonResolver.resolveInheritance(rawJson, minecraftDir) { id ->
                    versionService.fetchVersionJson(id)
                }

                val javaReq = JavaVersionResolver.resolve(version.id, mergedJson)
                LaunchLogBuffer.add("INFO", "Требуется Java ${javaReq.requiredMajor}${if (javaReq.legacyOnly) " (только 8)" else "+"}")

                var java = javaService.findJava(
                    javaReq.requiredMajor,
                    settings.javaPath,
                    javaReq.legacyOnly
                )

                if (java == null && settings.autoJavaInstall) {
                    launchMessage = "Установка Java ${javaReq.requiredMajor}..."
                    LaunchLogBuffer.add("INFO", "Автоустановка Java ${javaReq.requiredMajor}...")
                    notifyStateChanged()
                    java = javaService.installJava(javaReq.requiredMajor)
                    if (javaReq.legacyOnly) {
                        java = javaService.findJava(8, settings.javaPath, legacyOnly = true)
                    } else if (java == null || java.majorVersion < javaReq.requiredMajor) {
                        java = javaService.findJava(javaReq.requiredMajor, settings.javaPath, legacyOnly = false)
                    }
                }

                if (java == null) {
                    val hint = buildJavaErrorHint(version.id, javaReq)
                    LaunchLogBuffer.add("ERROR", hint)
                    launchStatus = LaunchStatus.ERROR
                    launchMessage = hint
                    showError(hint)
                    if (settings.showLaunchConsole) launchConsoleVisible = true
                    return@launch
                }

                if (javaReq.legacyOnly && java.majorVersion > 8) {
                    val msg = "Для ${version.displayName} нужна Java 8, найдена Java ${java.majorVersion}."
                    LaunchLogBuffer.add("ERROR", msg)
                    launchStatus = LaunchStatus.ERROR
                    launchMessage = msg
                    showError(msg)
                    return@launch
                }
                if (!javaReq.legacyOnly && java.majorVersion < javaReq.requiredMajor) {
                    val msg = "Для ${version.displayName} нужна Java ${javaReq.requiredMajor}+ " +
                            "(сейчас Java ${java.majorVersion}). Включите автоустановку Java в настройках " +
                            "или установите JDK ${javaReq.requiredMajor}."
                    LaunchLogBuffer.add("ERROR", msg)
                    showError(msg)
                    return@launch
                }

                javaInfo = java
                LaunchLogBuffer.add("INFO", "Java ${java.version} (${java.path})")

                val preparer = LaunchPreparer(minecraftDir, downloadService) { id ->
                    versionService.fetchVersionJson(id)
                }
                val diagnostic = withContext(Dispatchers.IO) {
                    preparer.prepare(version.id, rawJson, java) { level, msg ->
                        LaunchLogBuffer.add(level, msg)
                    }
                }
                launchLogEntries = LaunchLogBuffer.getEntries()

                if (!diagnostic.ok) {
                    val msg = "Проверка перед запуском не пройдена:\n${diagnostic.errorSummary}"
                    LaunchLogBuffer.add("ERROR", msg)
                    showError(msg)
                    if (settings.showLaunchConsole) launchConsoleVisible = true
                    return@launch
                }

                val optiFineJar = if (version.id.startsWith(OptiFineService.OPTIFINE_PREFIX)) {
                    File(minecraftDir, "versions/${version.id}/OptiFine.jar")
                } else null

                val error = withContext(Dispatchers.IO) {
                    gameLauncher.launch(
                        versionId = version.id,
                        versionJson = rawJson,
                        profile = profile,
                        javaPath = java.path,
                        ramMinMb = settings.ramMinMb,
                        ramMaxMb = settings.ramMaxMb,
                        optiFineJar = optiFineJar,
                        versionFetcher = { id -> versionService.fetchVersionJson(id) }
                    )
                }

                if (error != null) {
                    launchConsoleError = error
                    showError(error)
                    if (settings.showLaunchConsole) launchConsoleVisible = true
                }
            } catch (e: Exception) {
                LogService.error("Ошибка запуска игры", e)
                launchStatus = LaunchStatus.ERROR
                launchMessage = "Ошибка: ${e.message}"
                showError("Ошибка запуска:\n${e.message}\n\nПодробности: ${LogService.getLogFilePath()}")
            }
            notifyStateChanged()
        }
    }

    fun stopGame() {
        gameLauncher.stop()
    }

    fun dismissError() {
        errorDialogMessage = null
        notifyStateChanged()
    }

    private fun showError(message: String) {
        errorDialogMessage = message
        statusMessage = message.lines().first()
        LogService.error(message)
        notifyStateChanged()
    }

    fun selectMinecraftFolder(): String? {
        return try {
            val chooser = JFileChooser(FileSystemView.getFileSystemView().homeDirectory)
            chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            chooser.dialogTitle = "Выберите папку Minecraft"
            chooser.selectedFile = minecraftDir

            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                val path = chooser.selectedFile.absolutePath
                settings = settings.copy(minecraftDir = path)
                SettingsManager.save(settings)
                scope.launch { refreshAllInternal() }
                notifyStateChanged()
                path
            } else null
        } catch (e: Exception) {
            showError("Ошибка выбора папки: ${e.message}")
            null
        }
    }

    fun updateSettings(newSettings: LauncherSettings) {
        val snapshotsChanged = settings.showSnapshots != newSettings.showSnapshots
        settings = newSettings
        SettingsManager.save(settings)
        if (snapshotsChanged) applyVersionFilters()
        notifyStateChanged()
    }

    /** Запрос смены темы — вызывается из UI с диалогом подтверждения */
    fun confirmThemeChange(newDarkTheme: Boolean) {
        settings = settings.copy(isDarkTheme = newDarkTheme)
        SettingsManager.save(settings)
        LogService.info("Смена темы на ${if (newDarkTheme) "тёмную" else "светлую"}, перезапуск...")
        AppRestart.restart { msg -> showError(msg) }
    }

    fun deleteMod(mod: ModInfo) {
        if (modService.deleteMod(mod.filePath)) {
            modService.invalidateCache()
            mods = modService.getInstalledMods()
            statusMessage = "Мод ${mod.fileName} удалён"
            notifyStateChanged()
        }
    }

    fun openModsFolder() {
        val folder = modService.openModsFolder()
        try {
            Desktop.getDesktop().open(folder)
        } catch (e: Exception) {
            showError("Не удалось открыть папку: ${e.message}")
        }
    }

    fun importMods(files: List<File>) {
        var added = 0
        files.forEach { file ->
            if (file.isFile && file.extension.equals("jar", true)) {
                if (modService.importMod(file)) added++
            }
        }
        modService.invalidateCache()
        mods = modService.getInstalledMods()
        statusMessage = if (added > 0) "Добавлено модов: $added" else "Нет подходящих .jar файлов"
        notifyStateChanged()
    }

    fun refreshResourcePacks() {
        resourcePacks = resourcePackService.getInstalledResourcePacks()
        notifyStateChanged()
    }

    fun openResourcePacksFolder() {
        try {
            Desktop.getDesktop().open(resourcePackService.openResourcePacksFolder())
        } catch (e: Exception) {
            showError("Не удалось открыть папку: ${e.message}")
        }
    }

    fun importResourcePacks(files: List<File>) {
        var added = 0
        files.forEach { file ->
            if (file.isFile && file.extension.equals("zip", true)) {
                if (resourcePackService.importResourcePack(file)) added++
            }
        }
        resourcePacks = resourcePackService.getInstalledResourcePacks()
        statusMessage = if (added > 0) "Добавлено ресурс-паков: $added" else "Нет подходящих .zip файлов"
        notifyStateChanged()
    }

    fun deleteResourcePack(pack: ResourcePackInfo) {
        if (resourcePackService.deleteResourcePack(pack.filePath)) {
            resourcePacks = resourcePackService.getInstalledResourcePacks()
            statusMessage = "Ресурс-пак ${pack.fileName} удалён"
            notifyStateChanged()
        }
    }

    fun applyWindowSize(width: Int, height: Int) {
        windowActions?.resize?.invoke(width, height)
    }

    fun dismissLaunchConsole() {
        launchConsoleVisible = false
        notifyStateChanged()
    }

    fun openLastLaunchLog() {
        LaunchLogBuffer.clear()
        val fileLog = LogService.readGameOutputLog()
        if (fileLog.isNotBlank()) {
            fileLog.lines().forEach { LaunchLogBuffer.add("GAME", it) }
        } else {
            LaunchLogBuffer.add("INFO", "Лог запуска пуст. Запустите игру для записи лога.")
        }
        launchLogEntries = LaunchLogBuffer.getEntries()
        launchConsoleVisible = true
        notifyStateChanged()
    }

    fun openLogsFolder() {
        try {
            Desktop.getDesktop().open(File(LogService.getLogsDirectory()))
        } catch (e: Exception) {
            showError("Не удалось открыть папку логов: ${e.message}")
        }
    }

    private fun buildJavaErrorHint(versionId: String, req: JavaVersionResolver.JavaRequirement): String =
        if (req.legacyOnly) {
            "Для $versionId нужна Java 8 (64-bit).\n" +
                "Скачайте JDK 8 с adoptium.net или включите автоустановку Java в настройках."
        } else {
            "Для $versionId нужна Java ${req.requiredMajor}+.\n" +
                "Установите JDK ${req.requiredMajor} или включите автоустановку в настройках."
        }

    fun loginMicrosoft() {
        microsoftAuthJob?.cancel()
        microsoftAuthJob = scope.launch {
            try {
                val deviceCode = withContext(Dispatchers.IO) {
                    microsoftAuthService.requestDeviceCode()
                }
                microsoftAuthState = MicrosoftAuthUiState(
                    visible = true,
                    userCode = deviceCode.userCode,
                    verificationUri = MicrosoftAuthService.MICROSOFT_LINK,
                    message = deviceCode.message,
                    isPolling = true
                )
                notifyStateChanged()

                try {
                    Desktop.getDesktop().browse(URI(MicrosoftAuthService.MICROSOFT_LINK))
                } catch (e: Exception) {
                    LogService.warn("Не удалось открыть браузер: ${e.message}")
                }

                val expiresAt = System.currentTimeMillis() + deviceCode.expiresIn * 1000L
                var interval = deviceCode.interval.coerceAtLeast(3)

                while (System.currentTimeMillis() < expiresAt && isActive) {
                    delay(interval * 1000L)
                    try {
                        withContext(Dispatchers.IO) {
                            microsoftAuthService.pollForToken(deviceCode.deviceCode, interval)
                        }
                        microsoftAuthState = microsoftAuthState.copy(
                            visible = false,
                            isPolling = false
                        )
                        statusMessage =
                            "Microsoft: вход подтверждён. Используйте ник аккаунта в офлайн-режиме."
                        return@launch
                    } catch (_: MicrosoftAuthService.AuthPendingException) {
                        continue
                    }
                }
                microsoftAuthState = microsoftAuthState.copy(
                    isPolling = false,
                    error = "Время ожидания истекло"
                )
            } catch (e: Exception) {
                LogService.error("Ошибка Microsoft входа", e)
                microsoftAuthState = MicrosoftAuthUiState(
                    visible = true,
                    error = e.message ?: "Ошибка входа"
                )
            }
            notifyStateChanged()
        }
    }

    fun dismissMicrosoftAuth() {
        microsoftAuthJob?.cancel()
        microsoftAuthState = MicrosoftAuthUiState()
        notifyStateChanged()
    }

    fun openModrinth() {
        openModrinthUrl(
            ModrinthService.MODS_DISCOVER_URL,
            "Modrinth открыт — скачайте .jar и поместите в папку mods"
        )
    }

    fun openModrinthResourcePacks() {
        openModrinthUrl(
            ModrinthService.RESOURCEPACKS_DISCOVER_URL,
            "Modrinth открыт — скачайте .zip и поместите в resourcepacks"
        )
    }

    fun openModrinthShaders() {
        openModrinthUrl(
            ModrinthService.SHADERS_DISCOVER_URL,
            "Modrinth открыт — скачайте шейдеры и поместите в shaderpacks"
        )
    }

    private fun openModrinthUrl(url: String, successMessage: String) {
        try {
            Desktop.getDesktop().browse(URI(url))
            statusMessage = successMessage
            notifyStateChanged()
        } catch (e: Exception) {
            showError("Не удалось открыть Modrinth: ${e.message}")
        }
    }

    fun checkJava() {
        scope.launch {
            javaInfo = javaService.findJava(17, settings.javaPath)
            statusMessage = if (javaInfo != null) {
                "Java ${javaInfo!!.version} (${javaInfo!!.path})"
            } else {
                "Java не найдена"
            }
            notifyStateChanged()
        }
    }

    fun installJava() {
        scope.launch {
            javaInfo = javaService.installJava(17)
            if (javaInfo != null) {
                settings = settings.copy(javaPath = javaInfo!!.path)
                SettingsManager.save(settings)
            }
            notifyStateChanged()
        }
    }

    private fun notifyStateChanged() {
        scope.launch(Dispatchers.Main.immediate) {
            stateListeners.toList().forEach { it() }
        }
    }

    fun dispose() {
        installJob?.cancel()
        microsoftAuthJob?.cancel()
        scope.cancel()
        stateListeners.clear()
    }
}

/** Колбэки управления окном лаунчера */
data class LauncherWindowActions(
    val hide: () -> Unit = {},
    val show: () -> Unit = {},
    val close: () -> Unit = {},
    val resize: (Int, Int) -> Unit = { _, _ -> }
)

/** UI-состояние диалога Microsoft Device Code */
data class MicrosoftAuthUiState(
    val visible: Boolean = false,
    val userCode: String = "",
    val verificationUri: String = MicrosoftAuthService.MICROSOFT_LINK,
    val message: String = "",
    val isPolling: Boolean = false,
    val error: String? = null
)
