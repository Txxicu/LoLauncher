package com.lolauncher.service

import com.lolauncher.data.models.PlayerProfile
import com.lolauncher.util.SecureTokenStorage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.awt.Desktop
import java.net.InetSocketAddress
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit
import com.sun.net.httpserver.HttpServer

/**
 * Microsoft OAuth Authorization Code Flow + Xbox Live + Minecraft Services.
 */
class MicrosoftOAuthService {

    companion object {
        const val CLIENT_ID = "a332a5f6-c4dc-45b6-9fe3-d881490252b2"
        const val REDIRECT_URI = "http://localhost:46521"
        const val REDIRECT_PORT = 46521
        const val SCOPE = "XboxLive.signin offline_access"

        fun buildAuthorizeUrl(state: String = UUID.randomUUID().toString()): String =
            "https://login.live.com/oauth20_authorize.srf" +
                "?client_id=$CLIENT_ID" +
                "&response_type=code" +
                "&redirect_uri=${java.net.URLEncoder.encode(REDIRECT_URI, "UTF-8")}" +
                "&scope=${java.net.URLEncoder.encode(SCOPE, "UTF-8")}" +
                "&state=$state" +
                "&prompt=select_account"
    }

    @Serializable
    data class OAuthTokens(
        @SerialName("access_token") val accessToken: String = "",
        @SerialName("refresh_token") val refreshToken: String = "",
        @SerialName("expires_in") val expiresIn: Int = 0,
        val error: String? = null,
        @SerialName("error_description") val errorDescription: String? = null
    )

    @Serializable
    data class MinecraftProfileResponse(
        val id: String = "",
        val name: String = ""
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Полный цикл: браузер → callback → токены → Xbox → Minecraft Profile.
     */
    fun authenticateInteractive(): Pair<PlayerProfile, OAuthTokens> {
        val state = UUID.randomUUID().toString()
        val code = waitForAuthCode(state)
        val tokens = exchangeCode(code)
        val profile = loginMinecraft(tokens.accessToken)
        return profile to tokens
    }

    fun refreshAccessToken(refreshToken: String): OAuthTokens {
        val body = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("redirect_uri", REDIRECT_URI)
            .add("scope", SCOPE)
            .build()
        return postTokens("https://login.live.com/oauth20_token.srf", body)
    }

    fun resolveProfile(accountId: String, tokens: SecureTokenStorage.DecryptedTokens): PlayerProfile {
        var accessToken = tokens.accessToken
        if (tokens.expiresAt > 0 && System.currentTimeMillis() > tokens.expiresAt - 60_000) {
            val refreshed = refreshAccessToken(tokens.refreshToken)
            accessToken = refreshed.accessToken
            val expiresAt = System.currentTimeMillis() + refreshed.expiresIn * 1000L
            SecureTokenStorage.saveTokens(
                accountId,
                refreshed.accessToken,
                refreshed.refreshToken.ifBlank { tokens.refreshToken },
                expiresAt
            )
        }
        return loginMinecraft(accessToken)
    }

    private fun waitForAuthCode(expectedState: String): String {
        var resultCode: String? = null
        var resultError: String? = null
        val latch = java.util.concurrent.CountDownLatch(1)

        val server = HttpServer.create(InetSocketAddress(REDIRECT_PORT), 0)
        server.createContext("/") { exchange ->
            val query = exchange.requestURI.query ?: ""
            val params = query.split("&").associate {
                val p = it.split("=", limit = 2)
                p[0] to (p.getOrNull(1) ?: "")
            }
            val code = params["code"]
            val state = params["state"]
            val error = params["error"]

            if (error != null) {
                resultError = error
            } else if (code != null && (state == null || state == expectedState)) {
                resultCode = code
            } else {
                resultError = "Неверный state"
            }

            val response = "<html><body><h2>Авторизация завершена. Можно закрыть вкладку.</h2></body></html>"
            exchange.sendResponseHeaders(200, response.length.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
            latch.countDown()
        }
        server.executor = null
        server.start()

        try {
            Desktop.getDesktop().browse(URI(buildAuthorizeUrl(expectedState)))
        } catch (_: Exception) {
        }

        val ok = latch.await(5, TimeUnit.MINUTES)
        server.stop(0)

        if (!ok) throw Exception("Время ожидания авторизации истекло")
        resultCode?.let { return it }
        throw Exception(resultError ?: "Код авторизации не получен")
    }

    private fun exchangeCode(code: String): OAuthTokens {
        val body = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("code", code)
            .add("grant_type", "authorization_code")
            .add("redirect_uri", REDIRECT_URI)
            .add("scope", SCOPE)
            .build()
        return postTokens("https://login.live.com/oauth20_token.srf", body)
    }

    private fun postTokens(url: String, body: okhttp3.RequestBody): OAuthTokens {
        val request = Request.Builder().url(url).post(body).build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: throw Exception("Пустой ответ токена")
            val tokens = json.decodeFromString<OAuthTokens>(text)
            if (tokens.error != null) {
                throw Exception(tokens.errorDescription ?: tokens.error)
            }
            if (tokens.accessToken.isBlank()) throw Exception("Access token не получен")
            return tokens
        }
    }

    private fun loginMinecraft(msAccessToken: String): PlayerProfile {
        val xbl = authenticateXbox(msAccessToken)
        val xsts = authorizeXsts(xbl)
        val mcToken = loginWithXbox(xsts)
        val mcProfile = fetchMinecraftProfile(mcToken)
        return PlayerProfile(
            username = mcProfile.name,
            uuid = formatUuid(mcProfile.id),
            accessToken = mcToken,
            isOffline = false,
            accountType = com.lolauncher.data.models.AccountType.MICROSOFT
        )
    }

    private fun authenticateXbox(msToken: String): String {
        val payload = """
            {"Properties":{"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com","RpsTicket":"d=$msToken"},"RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT"}
        """.trimIndent()
        val request = Request.Builder()
            .url("https://user.auth.xboxlive.com/user/authenticate")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        return extractToken(request)
    }

    private fun authorizeXsts(xblToken: String): Pair<String, String> {
        val payload = """
            {"Properties":{"SandboxId":"RETAIL","UserTokens":["$xblToken"]},"RelyingParty":"rp://api.minecraftservices.com/","TokenType":"JWT"}
        """.trimIndent()
        val request = Request.Builder()
            .url("https://xsts.auth.xboxlive.com/xsts/authorize")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: throw Exception("Пустой XSTS ответ")
            val root = json.parseToJsonElement(text).jsonObject
            val token = root["Token"]?.jsonPrimitive?.content ?: throw Exception("XSTS token не получен")
            val uhs = root["DisplayClaims"]?.jsonObject
                ?.get("xui")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("uhs")?.jsonPrimitive?.content
                ?: throw Exception("uhs не получен")
            return token to uhs
        }
    }

    private fun loginWithXbox(xsts: Pair<String, String>): String {
        val (token, uhs) = xsts
        val payload = """{"identityToken":"XBL3.0 x=$uhs;$token"}"""
        val request = Request.Builder()
            .url("https://api.minecraftservices.com/authentication/login_with_xbox")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        return extractToken(request)
    }

    private fun fetchMinecraftProfile(mcToken: String): MinecraftProfileResponse {
        val request = Request.Builder()
            .url("https://api.minecraftservices.com/minecraft/profile")
            .get()
            .header("Authorization", "Bearer $mcToken")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Minecraft profile HTTP ${response.code}")
            val text = response.body?.string() ?: throw Exception("Пустой профиль")
            return json.decodeFromString<MinecraftProfileResponse>(text)
        }
    }

    private fun extractToken(request: Request): String {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: throw Exception("Пустой ответ Xbox")
            val root = json.parseToJsonElement(text).jsonObject
            return root["Token"]?.jsonPrimitive?.content ?: throw Exception("Token не найден в ответе")
        }
    }

    private fun formatUuid(raw: String): String {
        if (raw.contains("-")) return raw
        if (raw.length < 32) return raw
        return "${raw.substring(0, 8)}-${raw.substring(8, 12)}-" +
            "${raw.substring(12, 16)}-${raw.substring(16, 20)}-${raw.substring(20)}"
    }
}
