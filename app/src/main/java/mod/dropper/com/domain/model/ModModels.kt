package mod.dropper.com.domain.model

enum class ThemeMode(val displayName: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
    AMOLED("AMOLED Black")
}

enum class ModSortIndex(val displayName: String, val apiValue: String) {
    RELEVANCE("Relevance", "relevance"),
    DOWNLOADS("Downloads", "downloads"),
    UPDATED("Recently Updated", "updated"),
    NEWEST("Newest", "newest"),
    FOLLOWS("Most Followed", "follows")
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val compactModCards: Boolean = false,
    val autoInstallDependencies: Boolean = true,
    val verifySha512: Boolean = true,
    val enableHaptics: Boolean = true
)

enum class LocalModFilter(val displayName: String) {
    ALL("All"),
    ACTIVE("Active"),
    DISABLED("Disabled")
}

data class InstalledMod(
    val fileName: String,
    val displayName: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isEnabled: Boolean
)

enum class ModCategory(val displayName: String, val modrinthFacet: String) {
    PERFORMANCE("Performance", "optimization"),
    UTILITY("Utility", "utility"),
    QOL("QoL", "tweaks"),
    SHADERS("Shaders", "atmosphere"),
    WORLDGEN("World Gen", "worldgen"),
    MAGIC("Magic", "magic"),
    TECHNOLOGY("Tech", "technology")
}

data class ModProject(
    val projectId: String,
    val slug: String,
    val title: String,
    val summary: String,
    val descriptionBody: String = "",
    val author: String,
    val downloads: Long,
    val iconUrl: String?,
    val categories: List<String>,
    val versions: List<String>
)

enum class DependencyType {
    REQUIRED,
    OPTIONAL,
    INCOMPATIBLE,
    EMBEDDED
}

data class ModDependency(
    val projectId: String?,
    val versionId: String?,
    val fileName: String?,
    val dependencyType: DependencyType,
    val resolvedTitle: String? = null
)

data class ModFileAsset(
    val url: String,
    val fileName: String,
    val primary: Boolean,
    val size: Long,
    val sha512: String?
)

data class ModVersion(
    val id: String,
    val projectId: String,
    val name: String,
    val versionNumber: String,
    val changelog: String,
    val gameVersions: List<String>,
    val loaders: List<String>,
    val files: List<ModFileAsset>,
    val dependencies: List<ModDependency>
)

data class ResolvedModInstallPlan(
    val primaryProject: ModProject,
    val primaryVersion: ModVersion,
    val primaryFile: ModFileAsset,
    val availableVersions: List<ModVersion> = listOf(primaryVersion),
    val requiredDependencies: List<ResolvedDependencyItem>
)

data class ResolvedDependencyItem(
    val projectId: String,
    val projectTitle: String,
    val version: ModVersion,
    val file: ModFileAsset
)
