package mod.dropper.com.data.modrinth

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mod.dropper.com.core.storage.ModStorageManager
import mod.dropper.com.domain.model.DependencyType
import mod.dropper.com.domain.model.InstalledMod
import mod.dropper.com.domain.model.MinecraftInstance
import mod.dropper.com.domain.model.ModCategory
import mod.dropper.com.domain.model.ModDependency
import mod.dropper.com.domain.model.ModFileAsset
import mod.dropper.com.domain.model.ModLoader
import mod.dropper.com.domain.model.ModProject
import mod.dropper.com.domain.model.ModSortIndex
import mod.dropper.com.domain.model.ModVersion
import mod.dropper.com.domain.model.ResolvedDependencyItem
import mod.dropper.com.domain.model.ResolvedModInstallPlan
import mod.dropper.com.domain.repository.ModRepository

class ModRepositoryImpl(
    private val context: Context,
    private val apiService: ModrinthApiService,
    private val storageManager: ModStorageManager,
    private val json: Json
) : ModRepository {

    private val prefs = context.getSharedPreferences("dropper_mc_versions_cache", Context.MODE_PRIVATE)
    private var memoryCachedVersions: List<String>? = null

    companion object {
        private const val KEY_VERSIONS_JSON = "cached_versions_json"
        val DEFAULT_MINECRAFT_VERSIONS = listOf(
            "26.3", "26.2", "26.1.2", "26.1.1", "26.1",
            "1.21.11", "1.21.10", "1.21.9", "1.21.8", "1.21.7", "1.21.6", "1.21.5",
            "1.21.4", "1.21.3", "1.21.2", "1.21.1", "1.21",
            "1.20.6", "1.20.5", "1.20.4", "1.20.3", "1.20.2", "1.20.1", "1.20",
            "1.19.4", "1.19.3", "1.19.2", "1.19.1", "1.19",
            "1.18.2", "1.18.1", "1.18",
            "1.17.1", "1.17",
            "1.16.5", "1.16.4", "1.16.3", "1.16.2", "1.16.1", "1.16",
            "1.15.2", "1.15.1", "1.15",
            "1.14.4", "1.14.3", "1.14.2", "1.14.1", "1.14",
            "1.13.2", "1.13.1", "1.13",
            "1.12.2", "1.12.1", "1.12",
            "1.11.2", "1.11.1", "1.11",
            "1.10.2", "1.10.1", "1.10",
            "1.9.4", "1.9.3", "1.9.2", "1.9.1", "1.9",
            "1.8.9", "1.8.8", "1.8.7", "1.8.6", "1.8.5", "1.8.4", "1.8.3", "1.8.2", "1.8.1", "1.8",
            "1.7.10", "1.7.9", "1.7.8", "1.7.7", "1.7.6", "1.7.5", "1.7.4", "1.7.2",
            "1.6.4", "1.6.2", "1.6.1",
            "1.5.2", "1.5.1",
            "1.4.7", "1.4.6", "1.4.5", "1.4.4", "1.4.2",
            "1.3.2", "1.3.1",
            "1.2.5", "1.2.4", "1.2.3", "1.2.2", "1.2.1",
            "1.1", "1.0.0"
        )
    }

    override suspend fun searchMods(
        query: String,
        gameVersion: String,
        modLoader: ModLoader,
        categories: Set<ModCategory>,
        sortIndex: ModSortIndex
    ): Result<List<ModProject>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.searchMods(
                query = query,
                gameVersion = gameVersion,
                modLoader = modLoader,
                selectedCategories = categories,
                sortIndex = sortIndex
            )
            response.hits.map { hit ->
                ModProject(
                    projectId = hit.projectId,
                    slug = hit.slug,
                    title = hit.title,
                    summary = hit.description,
                    author = hit.author,
                    downloads = hit.downloads,
                    iconUrl = hit.iconUrl,
                    categories = hit.categories,
                    versions = hit.versions
                )
            }
        }
    }

    override suspend fun fetchFullProjectAndPlan(
        project: ModProject,
        instance: MinecraftInstance,
        preferredVersionId: String?
    ): Result<ResolvedModInstallPlan> = withContext(Dispatchers.IO) {
        runCatching {
            coroutineScope {
                val detailsDeferred = async {
                    runCatching { apiService.getProjectDetails(project.projectId) }.getOrNull()
                }
                val versionsDeferred = async {
                    val exact = apiService.getProjectVersions(
                        idOrSlug = project.projectId,
                        gameVersion = instance.gameVersion,
                        modLoader = instance.modLoader
                    )
                    exact.ifEmpty {
                        apiService.getProjectVersions(
                            idOrSlug = project.projectId,
                            gameVersion = null,
                            modLoader = instance.modLoader
                        ).ifEmpty {
                            apiService.getProjectVersions(
                                idOrSlug = project.projectId,
                                gameVersion = null,
                                modLoader = null
                            )
                        }
                    }
                }

                val details = detailsDeferred.await()
                val versionDtos = versionsDeferred.await()

                if (versionDtos.isEmpty()) {
                    throw IllegalStateException("No compatible versions found for ${project.title}")
                }

                val chosenVersionDto = versionDtos.find { it.id == preferredVersionId }
                    ?: versionDtos.first()
                val resolvedDependencies = mutableListOf<ResolvedDependencyItem>()
                val mappedDependencies = mutableListOf<ModDependency>()

                // Resolve required dependencies concurrently
                val depJobs = chosenVersionDto.dependencies.map { depDto ->
                    async {
                        val depType = when (depDto.dependencyType.lowercase()) {
                            "required" -> DependencyType.REQUIRED
                            "optional" -> DependencyType.OPTIONAL
                            "incompatible" -> DependencyType.INCOMPATIBLE
                            "embedded" -> DependencyType.EMBEDDED
                            else -> DependencyType.OPTIONAL
                        }

                        var resolvedTitle: String? = depDto.fileName
                        var resolvedItem: ResolvedDependencyItem? = null

                        if (depType == DependencyType.REQUIRED) {
                            runCatching {
                                val targetProjectId = depDto.projectId
                                    ?: depDto.versionId?.let { vId -> apiService.getVersionById(vId).projectId }

                                if (targetProjectId != null) {
                                    val depProject = apiService.getProjectDetails(targetProjectId)
                                    resolvedTitle = depProject.title

                                    val depVersionDto = if (!depDto.versionId.isNullOrBlank()) {
                                        runCatching { apiService.getVersionById(depDto.versionId) }.getOrNull()
                                    } else {
                                        null
                                    } ?: apiService.getProjectVersions(
                                        idOrSlug = targetProjectId,
                                        gameVersion = instance.gameVersion,
                                        modLoader = instance.modLoader
                                    ).firstOrNull() ?: apiService.getProjectVersions(
                                        idOrSlug = targetProjectId,
                                        gameVersion = null,
                                        modLoader = instance.modLoader
                                    ).firstOrNull()

                                    if (depVersionDto != null) {
                                        val mappedDepVersion = depVersionDto.toDomain()
                                        val primaryDepFile = mappedDepVersion.files.firstOrNull { it.primary }
                                            ?: mappedDepVersion.files.firstOrNull()
                                        if (primaryDepFile != null) {
                                            resolvedItem = ResolvedDependencyItem(
                                                projectId = targetProjectId,
                                                projectTitle = depProject.title,
                                                version = mappedDepVersion,
                                                file = primaryDepFile
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (!depDto.projectId.isNullOrBlank()) {
                            runCatching {
                                resolvedTitle = apiService.getProjectDetails(depDto.projectId).title
                            }
                        }

                        val depModel = ModDependency(
                            projectId = depDto.projectId,
                            versionId = depDto.versionId,
                            fileName = depDto.fileName,
                            dependencyType = depType,
                            resolvedTitle = resolvedTitle ?: depDto.projectId ?: "Companion Mod"
                        )
                        depModel to resolvedItem
                    }
                }

                for ((depModel, resolvedItem) in depJobs.awaitAll()) {
                    mappedDependencies.add(depModel)
                    if (resolvedItem != null) {
                        resolvedDependencies.add(resolvedItem)
                    }
                }

                val primaryVersion = chosenVersionDto.toDomain(overrideDependencies = mappedDependencies)
                val allMappedVersions = versionDtos.take(15).map { dto ->
                    if (dto.id == chosenVersionDto.id) primaryVersion else dto.toDomain()
                }

                val primaryFile = primaryVersion.files.firstOrNull { it.primary }
                    ?: primaryVersion.files.firstOrNull()
                    ?: throw IllegalStateException("No downloadable .jar asset found for ${project.title}")

                val enrichedProject = project.copy(
                    descriptionBody = details?.body?.ifBlank { project.summary } ?: project.summary
                )

                ResolvedModInstallPlan(
                    primaryProject = enrichedProject,
                    primaryVersion = primaryVersion,
                    primaryFile = primaryFile,
                    availableVersions = allMappedVersions,
                    requiredDependencies = resolvedDependencies.distinctBy { it.projectId }
                )
            }
        }
    }

    override suspend fun getInstalledMods(instancePath: String): Result<List<InstalledMod>> {
        return storageManager.listInstalledMods(instancePath)
    }

    override suspend fun toggleMod(instancePath: String, fileName: String): Result<InstalledMod> {
        return storageManager.toggleModState(instancePath, fileName)
    }

    override suspend fun deleteMod(instancePath: String, fileName: String): Result<Unit> {
        return storageManager.deleteMod(instancePath, fileName)
    }

    override suspend fun getAvailableGameVersions(forceRefresh: Boolean): Result<List<String>> = withContext(Dispatchers.IO) {
        // Return memory cache if available and not forced
        if (!forceRefresh && !memoryCachedVersions.isNullOrEmpty()) {
            return@withContext Result.success(memoryCachedVersions!!)
        }

        // Return persistent cache if not forced
        if (!forceRefresh) {
            val cachedJson = prefs.getString(KEY_VERSIONS_JSON, null)
            if (!cachedJson.isNullOrBlank()) {
                val decoded = runCatching { json.decodeFromString<List<String>>(cachedJson) }.getOrNull()
                if (!decoded.isNullOrEmpty()) {
                    memoryCachedVersions = decoded
                    return@withContext Result.success(decoded)
                }
            }
        }

        // Attempt network fetch from Modrinth API v2 tag endpoint
        val networkResult = runCatching {
            val tags = apiService.getGameVersions()
            val filtered = tags
                .filter { it.versionType.equals("release", ignoreCase = true) && it.version.isNotBlank() }
                .let { list ->
                    if (list.any { it.date.isNotBlank() }) {
                        list.sortedByDescending { it.date }
                    } else {
                        list
                    }
                }
                .map { it.version.trim() }
                .distinct()

            if (filtered.isNotEmpty()) {
                memoryCachedVersions = filtered
                prefs.edit()
                    .putString(KEY_VERSIONS_JSON, json.encodeToString(filtered))
                    .apply()
                filtered
            } else {
                DEFAULT_MINECRAFT_VERSIONS
            }
        }

        if (networkResult.isSuccess) {
            return@withContext networkResult
        }

        // If network failed, fall back to cached json, memory, or DEFAULT_MINECRAFT_VERSIONS
        val cachedJson = prefs.getString(KEY_VERSIONS_JSON, null)
        val fallback = if (!cachedJson.isNullOrBlank()) {
            runCatching { json.decodeFromString<List<String>>(cachedJson) }.getOrNull()
        } else null

        val finalVersions = fallback ?: memoryCachedVersions ?: DEFAULT_MINECRAFT_VERSIONS
        memoryCachedVersions = finalVersions
        Result.success(finalVersions)
    }

    private fun ModrinthVersionDto.toDomain(
        overrideDependencies: List<ModDependency>? = null
    ): ModVersion {
        return ModVersion(
            id = id,
            projectId = projectId,
            name = name,
            versionNumber = versionNumber,
            changelog = changelog?.takeIf { it.isNotBlank() } ?: "No changelog provided for this release.",
            gameVersions = gameVersions,
            loaders = loaders,
            files = files.map { fileDto ->
                ModFileAsset(
                    url = fileDto.url,
                    fileName = fileDto.filename,
                    primary = fileDto.primary,
                    size = fileDto.size,
                    sha512 = fileDto.hashes.sha512
                )
            },
            dependencies = overrideDependencies ?: dependencies.map { dep ->
                ModDependency(
                    projectId = dep.projectId,
                    versionId = dep.versionId,
                    fileName = dep.fileName,
                    dependencyType = when (dep.dependencyType.lowercase()) {
                        "required" -> DependencyType.REQUIRED
                        "optional" -> DependencyType.OPTIONAL
                        "incompatible" -> DependencyType.INCOMPATIBLE
                        "embedded" -> DependencyType.EMBEDDED
                        else -> DependencyType.OPTIONAL
                    }
                )
            }
        )
    }
}
