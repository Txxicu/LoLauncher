package com.lolauncher.util

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Шифрованное хранение OAuth-токенов (аналог Android Keystore для Desktop).
 * AES-256-GCM, ключ привязан к машине.
 */
object SecureTokenStorage {

    @Serializable
    data class TokenEntry(
        val accountId: String,
        @SerialName("access") val encryptedAccess: String,
        @SerialName("refresh") val encryptedRefresh: String,
        val expiresAt: Long = 0
    )

    @Serializable
    private data class TokenStore(val entries: List<TokenEntry> = emptyList())

    data class DecryptedTokens(
        val accessToken: String,
        val refreshToken: String,
        val expiresAt: Long
    )

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val storeFile = File(SettingsManager.launcherDir, "tokens.enc")
    private val keyFile = File(SettingsManager.launcherDir, ".token_key")

    private fun machineSeed(): ByteArray {
        val parts = listOf(
            System.getProperty("user.name", ""),
            System.getenv("COMPUTERNAME") ?: System.getenv("HOSTNAME") ?: "",
            SettingsManager.launcherDir.absolutePath
        )
        return parts.joinToString("|").toByteArray(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        if (keyFile.exists()) {
            val raw = Base64.getDecoder().decode(keyFile.readText().trim())
            return SecretKeySpec(raw, "AES")
        }
        val seed = machineSeed()
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(seed)
        keyFile.writeText(Base64.getEncoder().encodeToString(digest))
        return SecretKeySpec(digest, "AES")
    }

    private fun encrypt(plain: String): String {
        if (plain.isBlank()) return ""
        val key = getOrCreateKey()
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    private fun decrypt(encoded: String): String {
        if (encoded.isBlank()) return ""
        val key = getOrCreateKey()
        val combined = Base64.getDecoder().decode(encoded)
        val iv = combined.copyOfRange(0, 12)
        val data = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }

    private fun loadStore(): TokenStore {
        if (!storeFile.exists()) return TokenStore()
        return try {
            val decrypted = decrypt(storeFile.readText().trim())
            json.decodeFromString<TokenStore>(decrypted)
        } catch (_: Exception) {
            TokenStore()
        }
    }

    private fun saveStore(store: TokenStore) {
        storeFile.parentFile?.mkdirs()
        val plain = json.encodeToString(store)
        storeFile.writeText(encrypt(plain))
    }

    fun saveTokens(accountId: String, accessToken: String, refreshToken: String, expiresAt: Long = 0) {
        val store = loadStore().entries.filter { it.accountId != accountId }
        val entry = TokenEntry(
            accountId = accountId,
            encryptedAccess = encrypt(accessToken),
            encryptedRefresh = encrypt(refreshToken),
            expiresAt = expiresAt
        )
        saveStore(TokenStore(store + entry))
    }

    fun getTokens(accountId: String): DecryptedTokens? {
        val entry = loadStore().entries.find { it.accountId == accountId } ?: return null
        return DecryptedTokens(
            accessToken = decrypt(entry.encryptedAccess),
            refreshToken = decrypt(entry.encryptedRefresh),
            expiresAt = entry.expiresAt
        )
    }

    fun deleteTokens(accountId: String) {
        val store = loadStore().entries.filter { it.accountId != accountId }
        saveStore(TokenStore(store))
    }

    fun updateAccessToken(accountId: String, accessToken: String, expiresAt: Long) {
        val tokens = getTokens(accountId) ?: return
        saveTokens(accountId, accessToken, tokens.refreshToken, expiresAt)
    }
}
