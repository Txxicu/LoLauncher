package com.lolauncher.service

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Microsoft OAuth через Device Code Flow (как в официальном лаунчере).
 * Не открывает подозрительную страницу oauth20_authorize.
 */
class MicrosoftAuthService {

    companion object {
        const val CLIENT_ID = "00000000402b5328"
        private const val DEVICE_CODE_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"
        private const val TOKEN_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"
        const val MICROSOFT_LINK = "https://www.microsoft.com/link"
    }

    @Serializable
    data class DeviceCodeResponse(
        @SerialName("device_code") val deviceCode: String,
        @SerialName("user_code") val userCode: String,
        @SerialName("verification_uri") val verificationUri: String = MICROSOFT_LINK,
        @SerialName("expires_in") val expiresIn: Int = 900,
        val interval: Int = 5,
        val message: String = ""
    )

    @Serializable
    data class TokenResponse(
        @SerialName("access_token") val accessToken: String = "",
        @SerialName("refresh_token") val refreshToken: String = "",
        val error: String? = null,
        @SerialName("error_description") val errorDescription: String? = null
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Запрашивает device code для входа через microsoft.com/link
     */
    fun requestDeviceCode(): DeviceCodeResponse {
        val body = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("scope", "XboxLive.signin offline_access")
            .build()

        val request = Request.Builder()
            .url(DEVICE_CODE_URL)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Ошибка запроса кода: HTTP ${response.code}")
            }
            val text = response.body?.string() ?: throw Exception("Пустой ответ")
            return json.decodeFromString(DeviceCodeResponse.serializer(), text)
        }
    }

    /**
     * Ожидает подтверждения пользователем (polling).
     */
    fun pollForToken(deviceCode: String, intervalSec: Int = 5): TokenResponse {
        val body = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .add("client_id", CLIENT_ID)
            .add("device_code", deviceCode)
            .build()

        val request = Request.Builder().url(TOKEN_URL).post(body).build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: throw Exception("Пустой ответ")
            val token = json.decodeFromString(TokenResponse.serializer(), text)
            if (token.error == "authorization_pending") {
                throw AuthPendingException()
            }
            if (token.error != null) {
                throw Exception(token.errorDescription ?: token.error)
            }
            if (token.accessToken.isBlank()) {
                throw Exception("Токен не получен")
            }
            return token
        }
    }

    class AuthPendingException : Exception("Ожидание подтверждения")
}
