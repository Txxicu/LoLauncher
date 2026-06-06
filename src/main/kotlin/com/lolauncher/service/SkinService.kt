package com.lolauncher.service

import com.lolauncher.data.api.ElyByApi
import com.lolauncher.data.api.MojangApi
import com.lolauncher.data.models.AccountType
import com.lolauncher.data.models.PlayerProfile
import java.util.UUID

/**
 * Сервис загрузки скинов и профилей игроков.
 * Приоритет: Ely.by → Mojang.
 */
class SkinService(private val skinTextureService: SkinTextureService = SkinTextureService()) {

    fun loadProfile(username: String, preferEly: Boolean = true): PlayerProfile {
        if (username.isBlank()) {
            return createOfflineProfile("Player", preferEly)
        }

        val elyProfile = if (preferEly) ElyByApi.fetchUuid(username) else null
        if (elyProfile != null) {
            val formattedUuid = formatUuid(elyProfile.id)
            val textures = skinTextureService.loadTextures(username, formattedUuid, preferEly = true)
            return PlayerProfile(
                username = elyProfile.name,
                uuid = formattedUuid,
                isOffline = true,
                accountType = AccountType.ELY_BY,
                skinUrl = ElyByApi.skinUrl(username),
                skinBytes = textures.skinBytes,
                cloakBytes = textures.cloakBytes,
                previewBytes = textures.previewBytes
            )
        }

        val mojangProfile = try {
            MojangApi.fetchPlayerProfile(username)
        } catch (_: Exception) {
            null
        }

        return if (mojangProfile != null) {
            val formattedUuid = formatUuid(mojangProfile.id)
            val textures = skinTextureService.loadTextures(mojangProfile.name, formattedUuid, preferEly = false)
            PlayerProfile(
                username = mojangProfile.name,
                uuid = formattedUuid,
                isOffline = false,
                accountType = AccountType.OFFLINE,
                skinUrl = ElySkinService.getAvatarUrl(formattedUuid),
                skinBytes = textures.skinBytes,
                cloakBytes = textures.cloakBytes,
                previewBytes = textures.previewBytes
            )
        } else {
            createOfflineProfile(username, preferEly)
        }
    }

    fun createOfflineProfile(username: String, preferEly: Boolean = true): PlayerProfile {
        val offlineUuid = generateOfflineUuid(username)
        val textures = skinTextureService.loadTextures(username, offlineUuid, preferEly)
        return PlayerProfile(
            username = username,
            uuid = offlineUuid,
            accessToken = "offline_token",
            isOffline = true,
            accountType = if (preferEly) AccountType.ELY_BY else AccountType.OFFLINE,
            skinUrl = if (preferEly) ElyByApi.skinUrl(username) else ElySkinService.getAvatarUrl(offlineUuid),
            skinBytes = textures.skinBytes,
            cloakBytes = textures.cloakBytes,
            previewBytes = textures.previewBytes
        )
    }

    fun generateOfflineUuid(username: String): String {
        return UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray()).toString()
    }

    private fun formatUuid(raw: String): String {
        if (raw.contains("-")) return raw
        return "${raw.substring(0, 8)}-${raw.substring(8, 12)}-" +
                "${raw.substring(12, 16)}-${raw.substring(16, 20)}-${raw.substring(20)}"
    }

    fun getAvatarUrl(uuid: String, size: Int = 128): String =
        ElySkinService.getAvatarUrl(uuid, size)
}
