package com.lolauncher.service

import com.lolauncher.data.api.ElyByApi
import com.lolauncher.data.api.MojangApi
import com.lolauncher.data.models.SkinTextures

/**
 * Загрузка скинов и плащей: приоритет Ely.by, затем Mojang.
 */
class SkinTextureService {

    fun loadTextures(username: String, uuid: String? = null, preferEly: Boolean = true): SkinTextures {
        if (username.isBlank()) return SkinTextures()

        if (preferEly) {
            loadFromEly(username)?.let { return it }
        }

        loadFromMojang(username, uuid)?.let { return it }

        if (!preferEly) {
            loadFromEly(username)?.let { return it }
        }

        return SkinTextures()
    }

    private fun loadFromEly(username: String): SkinTextures? {
        val skin = ElyByApi.downloadSkin(username)
        val cloak = ElyByApi.downloadCloak(username)
        val preview = ElyByApi.downloadPreview(username)
        if (skin == null && cloak == null && preview == null) return null
        return SkinTextures(
            skinBytes = skin,
            cloakBytes = cloak,
            previewBytes = preview ?: skin,
            source = "ely.by"
        )
    }

    private fun loadFromMojang(username: String, uuid: String?): SkinTextures? {
        val profile = uuid?.let {
            com.lolauncher.data.models.MojangProfile(
                id = it.replace("-", ""),
                name = username
            )
        } ?: MojangApi.fetchPlayerProfile(username) ?: return null

        val formattedUuid = formatUuid(profile.id)
        val skin = ElySkinService.downloadAvatar(formattedUuid, username)
        if (skin == null) return null
        return SkinTextures(
            skinBytes = skin,
            previewBytes = skin,
            source = "mojang"
        )
    }

    private fun formatUuid(raw: String): String {
        if (raw.contains("-")) return raw
        if (raw.length < 32) return raw
        return "${raw.substring(0, 8)}-${raw.substring(8, 12)}-" +
            "${raw.substring(12, 16)}-${raw.substring(16, 20)}-${raw.substring(20)}"
    }
}
