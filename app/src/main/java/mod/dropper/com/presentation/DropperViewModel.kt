package mod.dropper.com.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mod.dropper.com.core.storage.PermissionHelper
import mod.dropper.com.core.storage.SettingsPreferencesManager
import mod.dropper.com.data.modrinth.ModRepositoryImpl
import mod.dropper.com.data.worker.DownloadItemPayload
import mod.dropper.com.data.worker.ModDownloadWorker
import mod.dropper.com.domain.model.AppSettings
import mod.dropper.com.domain.model.LauncherType
import mod.dropper.com.domain.model.LocalModFilter
import mod.dropper.com.domain.model.MinecraftInstance
import mod.dropper.com.domain.model.ModCategory
import mod.dropper.com.domain.model.ModLoader
import mod.dropper.com.domain.model.ModProject
import mod.dropper.com.domain.model.ModSortIndex
import mod.dropper.com.domain.model.ResolvedModInstallPlan
import mod.dropper.com.domain.repository.InstanceRepository
import mod.dropper.com.domain.repository.ModRepository

class DropperViewModel(
    private val instanceRepository: InstanceRepository,
    private val modRepository: ModRepository,
    private val settingsManager: SettingsPreferencesManager,
    private val workManager: WorkManager,
    private val json: Json
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DropperUiState(
            settings = settingsManager.loadSettings(),
            sortIndex = settingsManager.loadSortIndex(),
            hasAllFilesAccess = PermissionHelper.hasAllFilesAccess()
        )
    )
    val uiState: StateFlow<DropperUiState> = _uiState.asStateFlow()

    private var searchDebounceJob: Job? = null
    private var downloadObserverJob: Job? = null

    init {
        refreshPermissionsAndInstances()
        loadAvailableGameVersions(forceRefresh = false)
    }

    fun updateSettings(newSettings: AppSettings) {
        settingsManager.saveSettings(newSettings)
        _uiState.update { it.copy(settings = newSettings) }
    }

    fun loadAvailableGameVersions(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingVersions = true) }
            modRepository.getAvailableGameVersions(forceRefresh = forceRefresh)
                .onSuccess { versions ->
                    val latest = versions.firstOrNull() ?: ModRepositoryImpl.DEFAULT_MINECRAFT_VERSIONS.first()
                    _uiState.update { state ->
                        val updatedInstances = state.instances.map { inst ->
                            if (inst.gameVersion == "1.20.1" || inst.gameVersion.isBlank()) {
                                inst.copy(gameVersion = latest)
                            } else {
                                inst
                            }
                        }
                        val currentActive = state.activeInstance
                        val updatedActive = if (currentActive != null && (currentActive.gameVersion == "1.20.1" || currentActive.gameVersion.isBlank())) {
                            currentActive.copy(gameVersion = latest)
                        } else {
                            currentActive
                        }
                        state.copy(
                            availableGameVersions = versions,
                            isLoadingVersions = false,
                            instances = updatedInstances,
                            activeInstance = updatedActive
                        )
                    }
                    executeModSearch()
                }
                .onFailure {
                    _uiState.update { it.copy(isLoadingVersions = false) }
                }
        }
    }

    fun dismissPermissionBanner() {
        _uiState.update { it.copy(permissionBannerDismissed = true) }
    }

    fun refreshPermissionsAndInstances() {
        loadAvailableGameVersions(forceRefresh = true)
        val granted = PermissionHelper.hasAllFilesAccess()
        _uiState.update { it.copy(hasAllFilesAccess = granted) }

        viewModelScope.launch {
            val allInstances = instanceRepository.discoverInstances()
            val latest = _uiState.value.availableGameVersions.firstOrNull() ?: ModRepositoryImpl.DEFAULT_MINECRAFT_VERSIONS.first()
            val resolvedInstances = allInstances.map { inst ->
                if (inst.gameVersion == "1.20.1" || inst.gameVersion.isBlank()) {
                    inst.copy(gameVersion = latest)
                } else {
                    inst
                }
            }

            val currentActiveId = _uiState.value.activeInstance?.id
            val nextActive = resolvedInstances.find { it.id == currentActiveId }
                ?: resolvedInstances.firstOrNull { it.launcherType == _uiState.value.selectedLauncher }
                ?: resolvedInstances.firstOrNull()

            val nextLauncher = nextActive?.launcherType ?: _uiState.value.selectedLauncher

            _uiState.update {
                it.copy(
                    instances = resolvedInstances,
                    selectedLauncher = nextLauncher,
                    activeInstance = nextActive
                )
            }

            if (nextActive != null) {
                refreshInstalledMods(nextActive.path)
                executeModSearch()
            }
        }
    }

    fun selectTab(tab: DropperTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        if (tab == DropperTab.LOCAL_MODS) {
            _uiState.value.activeInstance?.let { refreshInstalledMods(it.path) }
        }
    }

    fun selectLauncher(launcherType: LauncherType) {
        val latest = _uiState.value.availableGameVersions.firstOrNull() ?: ModRepositoryImpl.DEFAULT_MINECRAFT_VERSIONS.first()
        val matchingInstance = _uiState.value.instances.firstOrNull { it.launcherType == launcherType }?.let { inst ->
            if (inst.gameVersion == "1.20.1" || inst.gameVersion.isBlank()) {
                inst.copy(gameVersion = latest)
            } else {
                inst
            }
        }
        _uiState.update {
            it.copy(
                selectedLauncher = launcherType,
                activeInstance = matchingInstance ?: it.activeInstance
            )
        }
        if (matchingInstance != null) {
            refreshInstalledMods(matchingInstance.path)
            executeModSearch()
        }
    }

    fun selectInstance(instance: MinecraftInstance) {
        _uiState.update {
            it.copy(
                selectedLauncher = instance.launcherType,
                activeInstance = instance
            )
        }
        refreshInstalledMods(instance.path)
        executeModSearch()
    }

    fun updateActiveInstanceConfig(gameVersion: String, modLoader: ModLoader) {
        val active = _uiState.value.activeInstance ?: return
        viewModelScope.launch {
            instanceRepository.updateInstanceMetadata(active.id, gameVersion, modLoader)
            val updated = active.copy(gameVersion = gameVersion, modLoader = modLoader)
            _uiState.update { state ->
                state.copy(
                    activeInstance = updated,
                    instances = state.instances.map { if (it.id == updated.id) updated else it },
                    statusSnackbarMessage = "Target set to ${updated.gameVersion} (${updated.modLoader.displayName})"
                )
            }
            executeModSearch()
        }
    }

    fun removeCustomInstance(instanceId: String) {
        viewModelScope.launch {
            instanceRepository.removeCustomInstance(instanceId)
            val all = instanceRepository.discoverInstances()
            val nextActive = all.firstOrNull()
            _uiState.update {
                it.copy(
                    instances = all,
                    activeInstance = nextActive,
                    selectedLauncher = nextActive?.launcherType ?: it.selectedLauncher,
                    statusSnackbarMessage = "Removed profile"
                )
            }
            if (nextActive != null) {
                refreshInstalledMods(nextActive.path)
                executeModSearch()
            }
        }
    }

    fun onSafTreeSelected(uri: Uri, customName: String?, gameVersion: String, modLoader: ModLoader) {
        val latest = _uiState.value.availableGameVersions.firstOrNull() ?: ModRepositoryImpl.DEFAULT_MINECRAFT_VERSIONS.first()
        val effectiveVersion = if (gameVersion.isBlank() || gameVersion == "1.20.1") latest else gameVersion
        viewModelScope.launch {
            instanceRepository.addCustomSafInstance(
                treeUri = uri,
                customName = customName,
                gameVersion = effectiveVersion,
                modLoader = modLoader
            ).onSuccess { newInstance ->
                val all = instanceRepository.discoverInstances()
                val resolvedInstances = all.map { inst ->
                    if (inst.gameVersion == "1.20.1" || inst.gameVersion.isBlank()) inst.copy(gameVersion = latest) else inst
                }
                val resolvedActive = if (newInstance.gameVersion == "1.20.1" || newInstance.gameVersion.isBlank()) {
                    newInstance.copy(gameVersion = latest)
                } else newInstance
                _uiState.update {
                    it.copy(
                        instances = resolvedInstances,
                        selectedLauncher = resolvedActive.launcherType,
                        activeInstance = resolvedActive,
                        statusSnackbarMessage = "Added folder: ${resolvedActive.name}"
                    )
                }
                refreshInstalledMods(resolvedActive.path)
                executeModSearch()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(statusSnackbarMessage = "Failed to add folder: ${err.localizedMessage}")
                }
            }
        }
    }

    fun onAddCustomPathInstance(
        path: String,
        name: String,
        launcherType: LauncherType,
        gameVersion: String,
        modLoader: ModLoader
    ) {
        val latest = _uiState.value.availableGameVersions.firstOrNull() ?: ModRepositoryImpl.DEFAULT_MINECRAFT_VERSIONS.first()
        val effectiveVersion = if (gameVersion.isBlank() || gameVersion == "1.20.1") latest else gameVersion
        viewModelScope.launch {
            instanceRepository.addCustomPathInstance(
                path = path,
                name = name,
                launcherType = launcherType,
                gameVersion = effectiveVersion,
                modLoader = modLoader
            ).onSuccess { newInstance ->
                val all = instanceRepository.discoverInstances()
                val resolvedInstances = all.map { inst ->
                    if (inst.gameVersion == "1.20.1" || inst.gameVersion.isBlank()) inst.copy(gameVersion = latest) else inst
                }
                val resolvedActive = if (newInstance.gameVersion == "1.20.1" || newInstance.gameVersion.isBlank()) {
                    newInstance.copy(gameVersion = latest)
                } else newInstance
                _uiState.update {
                    it.copy(
                        instances = resolvedInstances,
                        selectedLauncher = resolvedActive.launcherType,
                        activeInstance = resolvedActive,
                        statusSnackbarMessage = "Added instance: ${resolvedActive.name}"
                    )
                }
                refreshInstalledMods(resolvedActive.path)
                executeModSearch()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(statusSnackbarMessage = "Could not add path: ${err.localizedMessage}")
                }
            }
        }
    }

    fun onSearchQueryChange(newQuery: String) {
        _uiState.update { it.copy(searchQuery = newQuery) }
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(350L)
            executeModSearch()
        }
    }

    fun selectSortIndex(sortIndex: ModSortIndex) {
        settingsManager.saveSortIndex(sortIndex)
        _uiState.update { it.copy(sortIndex = sortIndex) }
        executeModSearch()
    }

    fun toggleCategoryFilter(category: ModCategory) {
        _uiState.update { state ->
            val updated = if (category in state.selectedCategories) {
                state.selectedCategories - category
            } else {
                state.selectedCategories + category
            }
            state.copy(selectedCategories = updated)
        }
        executeModSearch()
    }

    fun clearAllFilters() {
        _uiState.update {
            it.copy(
                searchQuery = "",
                selectedCategories = emptySet()
            )
        }
        executeModSearch()
    }

    fun executeModSearch() {
        val state = _uiState.value
        val active = state.activeInstance
        val gameVersion = active?.gameVersion ?: "1.20.1"
        val modLoader = active?.modLoader ?: ModLoader.FABRIC

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFeed = true, feedError = null) }
            modRepository.searchMods(
                query = state.searchQuery.trim(),
                gameVersion = gameVersion,
                modLoader = modLoader,
                categories = state.selectedCategories,
                sortIndex = state.sortIndex
            ).onSuccess { projects ->
                _uiState.update {
                    it.copy(
                        isLoadingFeed = false,
                        feedMods = projects,
                        feedError = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingFeed = false,
                        feedError = err.localizedMessage ?: "Failed to query Modrinth API v2"
                    )
                }
            }
        }
    }

    fun openModDetails(project: ModProject, preferredVersionId: String? = null) {
        val activeInstance = _uiState.value.activeInstance ?: return
        _uiState.update {
            it.copy(
                selectedProjectForSheet = project,
                isResolvingDetails = true,
                resolvedInstallPlan = if (preferredVersionId == null) null else it.resolvedInstallPlan,
                detailsError = null
            )
        }

        viewModelScope.launch {
            modRepository.fetchFullProjectAndPlan(project, activeInstance, preferredVersionId)
                .onSuccess { plan ->
                    _uiState.update {
                        it.copy(
                            isResolvingDetails = false,
                            resolvedInstallPlan = plan,
                            detailsError = null
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isResolvingDetails = false,
                            detailsError = err.localizedMessage ?: "Failed to resolve versions and dependencies"
                        )
                    }
                }
        }
    }

    fun dismissModDetails() {
        _uiState.update {
            it.copy(
                selectedProjectForSheet = null,
                isResolvingDetails = false,
                resolvedInstallPlan = null,
                detailsError = null
            )
        }
    }

    fun installModWithDependencies(plan: ResolvedModInstallPlan) {
        val instance = _uiState.value.activeInstance ?: return
        val settings = _uiState.value.settings
        val payloads = mutableListOf<DownloadItemPayload>()

        // Add required dependencies first if autoInstallDependencies is enabled
        if (settings.autoInstallDependencies) {
            for (dep in plan.requiredDependencies) {
                payloads.add(
                    DownloadItemPayload(
                        title = dep.projectTitle,
                        url = dep.file.url,
                        fileName = dep.file.fileName,
                        expectedSha512 = if (settings.verifySha512) dep.file.sha512 else null,
                        expectedSize = dep.file.size
                    )
                )
            }
        }

        // Add primary mod
        payloads.add(
            DownloadItemPayload(
                title = plan.primaryProject.title,
                url = plan.primaryFile.url,
                fileName = plan.primaryFile.fileName,
                expectedSha512 = if (settings.verifySha512) plan.primaryFile.sha512 else null,
                expectedSize = plan.primaryFile.size
            )
        )

        val payloadJson = json.encodeToString(payloads)
        val workRequest = OneTimeWorkRequestBuilder<ModDownloadWorker>()
            .setInputData(
                workDataOf(
                    ModDownloadWorker.KEY_INSTANCE_PATH to instance.path,
                    ModDownloadWorker.KEY_PAYLOAD_JSON to payloadJson
                )
            )
            .build()

        val uniqueWorkName = "dropper_install_${plan.primaryProject.projectId}"
        workManager.enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        downloadObserverJob?.cancel()
        downloadObserverJob = viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(workRequest.id).collect { workInfo ->
                if (workInfo == null) return@collect
                when (workInfo.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING -> {
                        val progress = workInfo.progress
                        val fileName = progress.getString(ModDownloadWorker.PROGRESS_CURRENT_FILE)
                            ?: plan.primaryFile.fileName
                        val percent = progress.getInt(ModDownloadWorker.PROGRESS_PERCENT, 0)
                        val bytesRead = progress.getLong(ModDownloadWorker.PROGRESS_BYTES_READ, 0L)
                        val totalBytes = progress.getLong(ModDownloadWorker.PROGRESS_TOTAL_BYTES, plan.primaryFile.size)
                        val itemIndex = progress.getInt(ModDownloadWorker.PROGRESS_ITEM_INDEX, 1)
                        val itemCount = progress.getInt(ModDownloadWorker.PROGRESS_ITEM_COUNT, payloads.size)

                        _uiState.update {
                            it.copy(
                                activeDownload = ActiveDownloadState(
                                    isDownloading = true,
                                    currentFileName = fileName,
                                    progressPercent = percent.coerceAtLeast(0),
                                    bytesRead = bytesRead,
                                    totalBytes = totalBytes,
                                    itemIndex = itemIndex,
                                    itemCount = itemCount
                                )
                            )
                        }
                    }

                    WorkInfo.State.SUCCEEDED -> {
                        val files = workInfo.outputData
                            .getStringArray(ModDownloadWorker.OUTPUT_INSTALLED_FILES)
                            ?.toList()
                            .orEmpty()
                        _uiState.update {
                            it.copy(
                                activeDownload = ActiveDownloadState(isDownloading = false),
                                statusSnackbarMessage = "Installed ${files.size} mod(s) into ${instance.name}"
                            )
                        }
                        refreshInstalledMods(instance.path)
                    }

                    WorkInfo.State.FAILED -> {
                        val err = workInfo.outputData.getString(ModDownloadWorker.OUTPUT_ERROR_MESSAGE)
                            ?: "Download failed"
                        _uiState.update {
                            it.copy(
                                activeDownload = ActiveDownloadState(isDownloading = false),
                                statusSnackbarMessage = err
                            )
                        }
                    }

                    WorkInfo.State.CANCELLED -> {
                        _uiState.update {
                            it.copy(
                                activeDownload = ActiveDownloadState(isDownloading = false),
                                statusSnackbarMessage = "Download cancelled"
                            )
                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    fun onLocalSearchQueryChange(query: String) {
        _uiState.update { it.copy(localSearchQuery = query) }
    }

    fun selectLocalModFilter(filter: LocalModFilter) {
        _uiState.update { it.copy(localModFilter = filter) }
    }

    fun setAllInstalledModsEnabled(enable: Boolean) {
        val active = _uiState.value.activeInstance ?: return
        val currentMods = _uiState.value.installedMods
        val targets = currentMods.filter { it.isEnabled != enable }
        
        if (targets.isEmpty()) {
            val alreadyStatus = if (enable) "All mods are already enabled" else "All mods are already disabled"
            _uiState.update { it.copy(statusSnackbarMessage = alreadyStatus) }
            return
        }

        // Optimistically update UI so toggles switch state immediately
        val targetFileNames = targets.map { it.fileName }.toSet()
        _uiState.update { state ->
            val updatedMods = state.installedMods.map { mod ->
                if (targetFileNames.contains(mod.fileName)) {
                    mod.copy(isEnabled = enable)
                } else {
                    mod
                }
            }
            state.copy(installedMods = updatedMods)
        }

        viewModelScope.launch {
            var successCount = 0
            var failCount = 0
            for (mod in targets) {
                modRepository.toggleMod(active.path, mod.fileName)
                    .onSuccess { successCount++ }
                    .onFailure { failCount++ }
            }

            val actionLabel = if (enable) "Enabled" else "Disabled"
            val message = if (failCount == 0) {
                "$actionLabel $successCount mod(s)"
            } else {
                "$actionLabel $successCount mod(s) ($failCount failed)"
            }

            _uiState.update {
                it.copy(statusSnackbarMessage = message)
            }
            refreshInstalledMods(active.path)
        }
    }

    fun refreshInstalledMods(instancePath: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingLocalMods = true) }
            modRepository.getInstalledMods(instancePath)
                .onSuccess { mods ->
                    _uiState.update { state ->
                        val updatedInstances = state.instances.map { inst ->
                            if (inst.path == instancePath) inst.copy(installedModsCount = mods.size) else inst
                        }
                        val updatedActive = state.activeInstance?.let { act ->
                            if (act.path == instancePath) act.copy(installedModsCount = mods.size) else act
                        }
                        state.copy(
                            isLoadingLocalMods = false,
                            installedMods = mods,
                            instances = updatedInstances,
                            activeInstance = updatedActive
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoadingLocalMods = false, installedMods = emptyList()) }
                }
        }
    }

    fun toggleInstalledMod(fileName: String) {
        val active = _uiState.value.activeInstance ?: return
        viewModelScope.launch {
            modRepository.toggleMod(active.path, fileName)
                .onSuccess { updatedMod ->
                    val statusLabel = if (updatedMod.isEnabled) "Enabled" else "Disabled"
                    _uiState.update {
                        it.copy(statusSnackbarMessage = "$statusLabel ${updatedMod.displayName}")
                    }
                    refreshInstalledMods(active.path)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(statusSnackbarMessage = "Failed to toggle mod: ${err.localizedMessage}")
                    }
                }
        }
    }

    fun deleteInstalledMod(fileName: String) {
        val active = _uiState.value.activeInstance ?: return
        viewModelScope.launch {
            modRepository.deleteMod(active.path, fileName)
                .onSuccess {
                    _uiState.update {
                        it.copy(statusSnackbarMessage = "Deleted $fileName")
                    }
                    refreshInstalledMods(active.path)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(statusSnackbarMessage = "Failed to delete $fileName: ${err.localizedMessage}")
                    }
                }
        }
    }

    fun clearSnackbarMessage() {
        _uiState.update { it.copy(statusSnackbarMessage = null) }
    }

    companion object {
        fun provideFactory(
            instanceRepository: InstanceRepository,
            modRepository: ModRepository,
            settingsManager: SettingsPreferencesManager,
            workManager: WorkManager,
            json: Json
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DropperViewModel(
                    instanceRepository = instanceRepository,
                    modRepository = modRepository,
                    settingsManager = settingsManager,
                    workManager = workManager,
                    json = json
                ) as T
            }
        }
    }
}
