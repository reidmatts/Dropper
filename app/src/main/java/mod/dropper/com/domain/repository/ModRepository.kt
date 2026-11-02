package mod.dropper.com.domain.repository

import mod.dropper.com.domain.model.InstalledMod
import mod.dropper.com.domain.model.MinecraftInstance
import mod.dropper.com.domain.model.ModCategory
import mod.dropper.com.domain.model.ModLoader
import mod.dropper.com.domain.model.ModProject
import mod.dropper.com.domain.model.ModSortIndex
import mod.dropper.com.domain.model.ResolvedModInstallPlan

interface ModRepository {
    suspend fun searchMods(
        query: String,
        gameVersion: String,
        modLoader: ModLoader,
        categories: Set<ModCategory>,
        sortIndex: ModSortIndex = ModSortIndex.RELEVANCE
    ): Result<List<ModProject>>

    suspend fun fetchFullProjectAndPlan(
        project: ModProject,
        instance: MinecraftInstance,
        preferredVersionId: String? = null
    ): Result<ResolvedModInstallPlan>

    suspend fun getInstalledMods(instancePath: String): Result<List<InstalledMod>>

    suspend fun toggleMod(instancePath: String, fileName: String): Result<InstalledMod>

    suspend fun deleteMod(instancePath: String, fileName: String): Result<Unit>

    suspend fun getAvailableGameVersions(forceRefresh: Boolean = false): Result<List<String>>
}
