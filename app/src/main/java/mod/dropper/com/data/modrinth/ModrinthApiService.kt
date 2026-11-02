package mod.dropper.com.data.modrinth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.encodeURLPathPart
import mod.dropper.com.domain.model.ModCategory
import mod.dropper.com.domain.model.ModLoader
import mod.dropper.com.domain.model.ModSortIndex

class ModrinthApiService(
    private val httpClient: HttpClient
) {
    companion object {
        const val BASE_URL = "https://api.modrinth.com/v2"
        private val SAFE_VERSION_REGEX = Regex("""[^a-zA-Z0-9._+-]""")
    }

    private fun sanitizePathSegment(raw: String): String {
        val trimmed = raw.trim()
        require(
            trimmed.isNotEmpty() &&
                !trimmed.contains("..") &&
                !trimmed.contains('/') &&
                !trimmed.contains('\\')
        ) {
            "Invalid Modrinth identifier: $raw"
        }
        return trimmed.encodeURLPathPart()
    }

    private fun sanitizeVersionString(raw: String): String {
        return raw.trim().replace(SAFE_VERSION_REGEX, "")
    }

    suspend fun searchMods(
        query: String,
        gameVersion: String,
        modLoader: ModLoader,
        selectedCategories: Set<ModCategory>,
        sortIndex: ModSortIndex = ModSortIndex.RELEVANCE,
        limit: Int = 30,
        offset: Int = 0
    ): ModrinthSearchResponseDto {
        val facetsList = mutableListOf<String>()
        val safeVersion = sanitizeVersionString(gameVersion)
        if (safeVersion.isNotBlank()) {
            facetsList.add("""["versions:$safeVersion"]""")
        }
        facetsList.add("""["categories:${modLoader.modrinthSlug}"]""")
        facetsList.add("""["project_type:mod"]""")
        for (cat in selectedCategories) {
            facetsList.add("""["categories:${cat.modrinthFacet}"]""")
        }
        val facetsJson = "[${facetsList.joinToString(",")}]"

        return httpClient.get("$BASE_URL/search") {
            parameter("query", query)
            parameter("facets", facetsJson)
            parameter("index", sortIndex.apiValue)
            parameter("limit", limit)
            parameter("offset", offset)
        }.body()
    }

    suspend fun getProjectDetails(idOrSlug: String): ModrinthProjectDetailsDto {
        val safeId = sanitizePathSegment(idOrSlug)
        return httpClient.get("$BASE_URL/project/$safeId").body()
    }

    suspend fun getProjectVersions(
        idOrSlug: String,
        gameVersion: String?,
        modLoader: ModLoader?
    ): List<ModrinthVersionDto> {
        val safeId = sanitizePathSegment(idOrSlug)
        val safeVersion = gameVersion?.let { sanitizeVersionString(it) }
        return httpClient.get("$BASE_URL/project/$safeId/version") {
            if (modLoader != null) {
                parameter("loaders", """["${modLoader.modrinthSlug}"]""")
            }
            if (!safeVersion.isNullOrBlank()) {
                parameter("game_versions", """["$safeVersion"]""")
            }
        }.body()
    }

    suspend fun getVersionById(versionId: String): ModrinthVersionDto {
        val safeVersionId = sanitizePathSegment(versionId)
        return httpClient.get("$BASE_URL/version/$safeVersionId").body()
    }

    suspend fun getGameVersions(): List<ModrinthGameVersionTagDto> {
        return httpClient.get("$BASE_URL/tag/game_version").body()
    }
}
