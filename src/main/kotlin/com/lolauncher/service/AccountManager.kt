package com.lolauncher.service

import com.lolauncher.data.api.ElyByApi
import com.lolauncher.data.models.AccountType
import com.lolauncher.data.models.AccountsStore
import com.lolauncher.data.models.PlayerProfile
import com.lolauncher.data.models.StoredAccount
import com.lolauncher.util.LogService
import com.lolauncher.util.SecureTokenStorage
import com.lolauncher.util.SettingsManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Менеджер аккаунтов: хранение, активация, профили.
 */
class AccountManager(
    private val skinTextureService: SkinTextureService = SkinTextureService(),
    private val skinService: SkinService = SkinService(),
    private val microsoftOAuth: MicrosoftOAuthService = MicrosoftOAuthService()
) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val storeFile = File(SettingsManager.launcherDir, "accounts.json")

    private var store: AccountsStore = loadStore()

    fun getAccounts(): List<StoredAccount> = store.accounts

    fun getActiveAccount(): StoredAccount? =
        store.activeAccountId?.let { id -> store.accounts.find { it.id == id } }

    fun addOfflineAccount(username: String, type: AccountType = AccountType.OFFLINE): StoredAccount {
        val nick = username.trim()
        require(nick.length >= 2) { "Ник слишком короткий" }
        val uuid = when (type) {
            AccountType.ELY_BY -> ElyByApi.fetchUuid(nick)?.id?.let { formatUuid(it) }
                ?: skinService.generateOfflineUuid(nick)
            else -> skinService.generateOfflineUuid(nick)
        }
        val account = StoredAccount(
            id = UUID.randomUUID().toString(),
            username = nick,
            uuid = uuid,
            type = type.name,
            useElySkin = type == AccountType.ELY_BY || type == AccountType.OFFLINE
        )
        store = store.copy(accounts = store.accounts + account)
        if (store.activeAccountId == null) {
            store = store.copy(activeAccountId = account.id)
        }
        saveStore()
        return account
    }

    fun addMicrosoftAccount(): StoredAccount {
        val (profile, tokens) = microsoftOAuth.authenticateInteractive()
        val account = StoredAccount(
            id = UUID.randomUUID().toString(),
            username = profile.username,
            uuid = profile.uuid,
            type = AccountType.MICROSOFT.name,
            useElySkin = false
        )
        val expiresAt = System.currentTimeMillis() + tokens.expiresIn * 1000L
        SecureTokenStorage.saveTokens(account.id, tokens.accessToken, tokens.refreshToken, expiresAt)
        store = store.copy(accounts = store.accounts + account, activeAccountId = account.id)
        saveStore()
        return account
    }

    fun setActiveAccount(accountId: String) {
        if (store.accounts.none { it.id == accountId }) return
        store = store.copy(activeAccountId = accountId)
        saveStore()
    }

    fun updateAccount(accountId: String, username: String): StoredAccount? {
        val account = store.accounts.find { it.id == accountId } ?: return null
        if (account.type == AccountType.MICROSOFT.name) return account
        val nick = username.trim()
        if (nick.length < 2) return null
        val uuid = when (AccountType.valueOf(account.type)) {
            AccountType.ELY_BY -> ElyByApi.fetchUuid(nick)?.id?.let { formatUuid(it) }
                ?: skinService.generateOfflineUuid(nick)
            else -> skinService.generateOfflineUuid(nick)
        }
        val updated = account.copy(username = nick, uuid = uuid)
        store = store.copy(accounts = store.accounts.map { if (it.id == accountId) updated else it })
        saveStore()
        return updated
    }

    fun removeAccount(accountId: String) {
        SecureTokenStorage.deleteTokens(accountId)
        val remaining = store.accounts.filter { it.id != accountId }
        val newActive = when {
            store.activeAccountId != accountId -> store.activeAccountId
            remaining.isNotEmpty() -> remaining.first().id
            else -> null
        }
        store = AccountsStore(remaining, newActive)
        saveStore()
    }

    fun resolveProfile(account: StoredAccount): PlayerProfile {
        return when (AccountType.valueOf(account.type)) {
            AccountType.MICROSOFT -> {
                val tokens = SecureTokenStorage.getTokens(account.id)
                    ?: throw Exception("Токены Microsoft не найдены")
                microsoftOAuth.resolveProfile(account.id, tokens).copy(
                    accountId = account.id,
                    accountType = AccountType.MICROSOFT
                )
            }
            AccountType.ELY_BY -> buildOfflineProfile(account, preferEly = true)
            AccountType.OFFLINE -> buildOfflineProfile(account, preferEly = account.useElySkin)
        }
    }

    fun resolveActiveProfile(): PlayerProfile? {
        val account = getActiveAccount() ?: return null
        return try {
            resolveProfile(account)
        } catch (e: Exception) {
            LogService.error("Ошибка профиля ${account.username}", e)
            null
        }
    }

    fun loadTexturesForAccount(account: StoredAccount) =
        skinTextureService.loadTextures(
            username = account.username,
            uuid = account.uuid,
            preferEly = account.useElySkin || account.type == AccountType.ELY_BY.name
        )

    private fun buildOfflineProfile(account: StoredAccount, preferEly: Boolean): PlayerProfile {
        val textures = skinTextureService.loadTextures(account.username, account.uuid, preferEly)
        val type = AccountType.valueOf(account.type)
        return PlayerProfile(
            username = account.username,
            uuid = account.uuid,
            accessToken = "offline_token",
            isOffline = true,
            accountType = type,
            accountId = account.id,
            skinUrl = if (preferEly) ElyByApi.skinUrl(account.username) else null,
            skinBytes = textures.skinBytes,
            cloakBytes = textures.cloakBytes,
            previewBytes = textures.previewBytes ?: textures.skinBytes
        )
    }

    fun migrateFromLegacyUsername(username: String) {
        if (store.accounts.isNotEmpty()) return
        if (username.isBlank()) return
        addOfflineAccount(username, AccountType.OFFLINE)
        saveStore()
    }

    private fun loadStore(): AccountsStore {
        if (!storeFile.exists()) return AccountsStore()
        return try {
            json.decodeFromString<AccountsStore>(storeFile.readText())
        } catch (_: Exception) {
            AccountsStore()
        }
    }

    private fun saveStore() {
        try {
            storeFile.parentFile?.mkdirs()
            storeFile.writeText(json.encodeToString(store))
        } catch (e: Exception) {
            LogService.error("Ошибка сохранения аккаунтов", e)
        }
    }

    private fun formatUuid(raw: String): String {
        if (raw.contains("-")) return raw
        if (raw.length < 32) return raw
        return "${raw.substring(0, 8)}-${raw.substring(8, 12)}-" +
            "${raw.substring(12, 16)}-${raw.substring(16, 20)}-${raw.substring(20)}"
    }
}
