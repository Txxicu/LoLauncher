package com.lolauncher.service

import com.lolauncher.data.models.PlayerProfile

/**
 * Сервис авторизации игрока.
 *
 * Поддерживает:
 * - Офлайн-режим (ввод ника без Microsoft-аккаунта)
 * - Заготовка для Microsoft OAuth (полная реализация требует Azure App Registration)
 *
 * Для полноценной Microsoft-авторизации необходимо зарегистрировать приложение
 * на https://portal.azure.com и указать CLIENT_ID.
 */
class AuthService(private val skinService: SkinService) {

    companion object {
        /** ID приложения Minecraft Launcher (публичный, используется многими лаунчерами) */
        const val MICROSOFT_CLIENT_ID = "00000000402b5328"
        const val MICROSOFT_AUTH_URL =
            "https://login.live.com/oauth20_authorize.srf"
        const val MICROSOFT_TOKEN_URL =
            "https://login.live.com/oauth20_token.srf"
        const val XBOX_AUTH_URL =
            "https://user.auth.xboxlive.com/user/authenticate"
        const val XSTS_AUTH_URL =
            "https://xsts.auth.xboxlive.com/xsts/authorize"
        const val MINECRAFT_AUTH_URL =
            "https://api.minecraftservices.com/authentication/login_with_xbox"
        const val MINECRAFT_PROFILE_URL =
            "https://api.minecraftservices.com/minecraft/profile"
    }

    private var currentProfile: PlayerProfile? = null

    /**
     * Авторизация в офлайн-режиме по нику.
     */
    fun loginOffline(username: String): PlayerProfile {
        val profile = skinService.loadProfile(username.trim())
        currentProfile = profile
        return profile
    }

    /**
     * Возвращает текущий профиль или null.
     */
    fun getCurrentProfile(): PlayerProfile? = currentProfile

    /**
     * Выход из аккаунта.
     */
    fun logout() {
        currentProfile = null
    }

    /**
     * Проверяет, авторизован ли пользователь.
     */
    fun isLoggedIn(): Boolean = currentProfile != null

    /**
     * Заготовка для Microsoft OAuth авторизации.
     * Открывает браузер для входа через Microsoft Account.
     *
     * Полная реализация включает:
     * 1. OAuth2 Device Code Flow или Authorization Code Flow
     * 2. Xbox Live аутентификацию
     * 3. XSTS токен
     * 4. Minecraft Services login
     * 5. Получение профиля и access token
     *
     * @return URL для открытия в браузере
     */
    fun getMicrosoftLoginUrl(): String {
        val redirectUri = "https://login.live.com/oauth20_desktop.srf"
        val scope = "XboxLive.signin%20offline_access"
        return "$MICROSOFT_AUTH_URL?client_id=$MICROSOFT_CLIENT_ID" +
                "&response_type=code&redirect_uri=$redirectUri&scope=$scope"
    }

    /**
     * Обновляет скин текущего профиля.
     */
    fun refreshProfile(): PlayerProfile? {
        val profile = currentProfile ?: return null
        val updated = skinService.loadProfile(profile.username)
        currentProfile = updated
        return updated
    }
}
