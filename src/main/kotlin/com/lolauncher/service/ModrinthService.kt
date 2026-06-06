package com.lolauncher.service

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Клиент Modrinth API v2 для поиска модов, шейдеров и ресурс-паков.
 */
object ModrinthService {

    const val SHADERS_DISCOVER_URL = "https://modrinth.com/discover/shaders"
    const val MODS_DISCOVER_URL = "https://modrinth.com/discover/mods"
    const val RESOURCEPACKS_DISCOVER_URL = "https://modrinth.com/discover/resourcepacks"

    private const val API_BASE = "https://api.modrinth.com/v2"

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    @Serializable
    data class ModrinthProject(
        val id: String = "",
        val slug: String = "",
        val title: String = "",
        val description: String = "",
        @SerialName("project_type") val projectType: String = "",
        @SerialName("icon_url") val iconUrl: String? = null,
        val downloads: Long = 0
    )

    @Serializable
    private data class SearchResponse(
        val hits: List<ModrinthProject> = emptyList()
    )

    fun fetchTrendingShaders(limit: Int = 12): List<ModrinthProject> =
        searchProjects("categories:shaders", limit)

    fun fetchTrendingMods(limit: Int = 12): List<ModrinthProject> =
        searchProjects("project_type:mod", limit)

    fun fetchTrendingResourcePacks(limit: Int = 12): List<ModrinthProject> =
        searchProjects("project_type:resourcepack", limit)

    fun projectPageUrl(slug: String): String = "https://modrinth.com/project/$slug"

    private fun searchProjects(facet: String, limit: Int): List<ModrinthProject> {
        val url = "$API_BASE/search?query=&limit=$limit&index=relevance&facets=%5B%22$facet%22%5D"
        return try {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "LoLauncher/1.0 (modrinth-api)")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                json.decodeFromString<SearchResponse>(body).hits
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
