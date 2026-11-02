package mod.dropper.com.data.modrinth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModrinthSearchResponseDto(
    @SerialName("hits") val hits: List<ModrinthProjectHitDto> = emptyList(),
    @SerialName("offset") val offset: Int = 0,
    @SerialName("limit") val limit: Int = 20,
    @SerialName("total_hits") val totalHits: Int = 0
)

@Serializable
data class ModrinthProjectHitDto(
    @SerialName("project_id") val projectId: String = "",
    @SerialName("slug") val slug: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("description") val description: String = "",
    @SerialName("author") val author: String = "Unknown",
    @SerialName("downloads") val downloads: Long = 0L,
    @SerialName("icon_url") val iconUrl: String? = null,
    @SerialName("categories") val categories: List<String> = emptyList(),
    @SerialName("versions") val versions: List<String> = emptyList()
)

@Serializable
data class ModrinthProjectDetailsDto(
    @SerialName("id") val id: String = "",
    @SerialName("slug") val slug: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("description") val description: String = "",
    @SerialName("body") val body: String = "",
    @SerialName("downloads") val downloads: Long = 0L,
    @SerialName("icon_url") val iconUrl: String? = null,
    @SerialName("categories") val categories: List<String> = emptyList(),
    @SerialName("game_versions") val gameVersions: List<String> = emptyList()
)

@Serializable
data class ModrinthVersionDto(
    @SerialName("id") val id: String = "",
    @SerialName("project_id") val projectId: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("version_number") val versionNumber: String = "",
    @SerialName("changelog") val changelog: String? = null,
    @SerialName("game_versions") val gameVersions: List<String> = emptyList(),
    @SerialName("loaders") val loaders: List<String> = emptyList(),
    @SerialName("files") val files: List<ModrinthFileDto> = emptyList(),
    @SerialName("dependencies") val dependencies: List<ModrinthDependencyDto> = emptyList()
)

@Serializable
data class ModrinthFileDto(
    @SerialName("hashes") val hashes: ModrinthHashesDto = ModrinthHashesDto(),
    @SerialName("url") val url: String = "",
    @SerialName("filename") val filename: String = "",
    @SerialName("primary") val primary: Boolean = false,
    @SerialName("size") val size: Long = 0L
)

@Serializable
data class ModrinthHashesDto(
    @SerialName("sha512") val sha512: String? = null,
    @SerialName("sha1") val sha1: String? = null
)

@Serializable
data class ModrinthDependencyDto(
    @SerialName("version_id") val versionId: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("dependency_type") val dependencyType: String = "required"
)

@Serializable
data class ModrinthGameVersionTagDto(
    @SerialName("version") val version: String = "",
    @SerialName("version_type") val versionType: String = "release",
    @SerialName("date") val date: String = "",
    @SerialName("major") val major: Boolean = false
)
