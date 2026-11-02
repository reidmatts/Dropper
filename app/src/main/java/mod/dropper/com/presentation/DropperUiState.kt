package mod.dropper.com.presentation

import mod.dropper.com.domain.model.AppSettings
import mod.dropper.com.domain.model.InstalledMod
import mod.dropper.com.domain.model.LauncherType
import mod.dropper.com.domain.model.LocalModFilter
import mod.dropper.com.domain.model.MinecraftInstance
import mod.dropper.com.domain.model.ModCategory
import mod.dropper.com.domain.model.ModProject
import mod.dropper.com.domain.model.ModSortIndex
import mod.dropper.com.domain.model.ResolvedModInstallPlan

enum class DropperTab(val title: String) {
    BROWSE("Modrinth Feed"),
    LOCAL_MODS("Installed Mods")
}

data class ActiveDownloadState(
    val isDownloading: Boolean = false,
    val currentFileName: String = "",
    val progressPercent: Int = 0,
    val bytesRead: Long = 0L,
    val totalBytes: Long = 0L,
    val itemIndex: Int = 1,
    val itemCount: Int = 1
)

data class DropperUiState(
    val settings: AppSettings = AppSettings(),
    val hasAllFilesAccess: Boolean = false,
    val permissionBannerDismissed: Boolean = false,
    val selectedTab: DropperTab = DropperTab.BROWSE,
    val instances: List<MinecraftInstance> = emptyList(),
    val selectedLauncher: LauncherType = LauncherType.POJAV,
    val activeInstance: MinecraftInstance? = null,
    val searchQuery: String = "",
    val selectedCategories: Set<ModCategory> = emptySet(),
    val sortIndex: ModSortIndex = ModSortIndex.RELEVANCE,
    val isLoadingFeed: Boolean = false,
    val feedMods: List<ModProject> = emptyList(),
    val feedError: String? = null,
    val selectedProjectForSheet: ModProject? = null,
    val isResolvingDetails: Boolean = false,
    val resolvedInstallPlan: ResolvedModInstallPlan? = null,
    val detailsError: String? = null,
    val installedMods: List<InstalledMod> = emptyList(),
    val localSearchQuery: String = "",
    val localModFilter: LocalModFilter = LocalModFilter.ALL,
    val isLoadingLocalMods: Boolean = false,
    val activeDownload: ActiveDownloadState = ActiveDownloadState(),
    val availableGameVersions: List<String> = emptyList(),
    val isLoadingVersions: Boolean = false,
    val statusSnackbarMessage: String? = null
)
