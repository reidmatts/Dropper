package mod.dropper.com.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import mod.dropper.com.domain.model.InstalledMod
import mod.dropper.com.domain.model.ModCategory
import mod.dropper.com.domain.model.ModLoader
import mod.dropper.com.domain.model.ModProject
import mod.dropper.com.domain.model.ModSortIndex
import mod.dropper.com.presentation.components.InstanceSelectorTopBar
import mod.dropper.com.presentation.components.LocalDropperHaptics
import mod.dropper.com.presentation.components.LocalModsTab
import mod.dropper.com.presentation.components.ModDetailsBottomSheet
import mod.dropper.com.presentation.components.rememberDropperHaptics

@Composable
fun DropperScreen(
    viewModel: DropperViewModel,
    onRequestStoragePermission: () -> Unit,
    onLaunchSafPicker: (gameVersion: String, modLoader: ModLoader) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = rememberDropperHaptics(enabled = uiState.settings.enableHaptics)

    LaunchedEffect(uiState.statusSnackbarMessage) {
        val msg = uiState.statusSnackbarMessage
        if (msg != null) {
            haptics.confirm()
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbarMessage()
        }
    }

    CompositionLocalProvider(
        LocalDropperHaptics provides haptics,
        LocalHapticFeedback provides haptics
    ) {
        Scaffold(
            topBar = {
                InstanceSelectorTopBar(
                    settings = uiState.settings,
                    activeInstance = uiState.activeInstance,
                    allInstances = uiState.instances,
                    availableGameVersions = uiState.availableGameVersions,
                    isLoadingVersions = uiState.isLoadingVersions,
                    onRefreshVersions = { viewModel.loadAvailableGameVersions(forceRefresh = true) },
                    onSelectInstance = viewModel::selectInstance,
                    onRefreshInstances = viewModel::refreshPermissionsAndInstances,
                    onPickSafFolder = onLaunchSafPicker,
                    onAddCustomPath = viewModel::onAddCustomPathInstance,
                    onUpdateInstanceConfig = viewModel::updateActiveInstanceConfig,
                    onRemoveCustomInstance = viewModel::removeCustomInstance,
                    onUpdateSettings = viewModel::updateSettings
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = uiState.selectedTab == DropperTab.BROWSE,
                        onClick = {
                            haptics.tick()
                            viewModel.selectTab(DropperTab.BROWSE)
                        },
                        icon = { Icon(Icons.Default.Explore, contentDescription = null) },
                        label = { Text(DropperTab.BROWSE.title) }
                    )
                    NavigationBarItem(
                        selected = uiState.selectedTab == DropperTab.LOCAL_MODS,
                        onClick = {
                            haptics.tick()
                            viewModel.selectTab(DropperTab.LOCAL_MODS)
                        },
                        icon = { Icon(Icons.Default.FolderSpecial, contentDescription = null) },
                        label = {
                            val count = uiState.activeInstance?.installedModsCount ?: uiState.installedMods.size
                            Text("${DropperTab.LOCAL_MODS.title} ($count)")
                        }
                    )
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Permission Banner if MANAGE_EXTERNAL_STORAGE is not granted and not dismissed
                if (!uiState.hasAllFilesAccess && !uiState.permissionBannerDismissed) {
                    StoragePermissionBanner(
                        onGrantClick = {
                            haptics.click()
                            onRequestStoragePermission()
                        },
                        onSafFallbackClick = {
                            haptics.click()
                            val active = uiState.activeInstance
                            onLaunchSafPicker(
                                active?.gameVersion ?: "1.20.1",
                                active?.modLoader ?: ModLoader.FABRIC
                            )
                        },
                        onDismiss = {
                            haptics.tick()
                            viewModel.dismissPermissionBanner()
                        }
                    )
                }

                // Active download progress banner
                if (uiState.activeDownload.isDownloading && uiState.selectedProjectForSheet == null) {
                    LinearProgressIndicator(
                        progress = { (uiState.activeDownload.progressPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Installing ${uiState.activeDownload.currentFileName} (${uiState.activeDownload.progressPercent}%)",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                when (uiState.selectedTab) {
                    DropperTab.BROWSE -> {
                        ModBrowseContent(
                            searchQuery = uiState.searchQuery,
                            selectedCategories = uiState.selectedCategories,
                            sortIndex = uiState.sortIndex,
                            compactCards = uiState.settings.compactModCards,
                            installedMods = uiState.installedMods,
                            isLoading = uiState.isLoadingFeed,
                            mods = uiState.feedMods,
                            errorMessage = uiState.feedError,
                            onQueryChange = viewModel::onSearchQueryChange,
                            onSelectSort = {
                                haptics.tick()
                                viewModel.selectSortIndex(it)
                            },
                            onToggleCategory = {
                                haptics.tick()
                                viewModel.toggleCategoryFilter(it)
                            },
                            onClearFilters = {
                                haptics.heavy()
                                viewModel.clearAllFilters()
                            },
                            onRetry = {
                                haptics.click()
                                viewModel.executeModSearch()
                            },
                            onModClick = { project ->
                                haptics.click()
                                viewModel.openModDetails(project)
                            }
                        )
                    }

                    DropperTab.LOCAL_MODS -> {
                        LocalModsTab(
                            activeInstance = uiState.activeInstance,
                            installedMods = uiState.installedMods,
                            localSearchQuery = uiState.localSearchQuery,
                            localModFilter = uiState.localModFilter,
                            isLoading = uiState.isLoadingLocalMods,
                            onLocalSearchChange = viewModel::onLocalSearchQueryChange,
                            onSelectFilter = viewModel::selectLocalModFilter,
                            onEnableAll = { viewModel.setAllInstalledModsEnabled(true) },
                            onDisableAll = { viewModel.setAllInstalledModsEnabled(false) },
                            onToggleMod = viewModel::toggleInstalledMod,
                            onDeleteMod = viewModel::deleteInstalledMod
                        )
                    }
                }
            }

            val sheetProject = uiState.selectedProjectForSheet
            if (sheetProject != null) {
                ModDetailsBottomSheet(
                    project = sheetProject,
                    activeInstance = uiState.activeInstance,
                    isResolving = uiState.isResolvingDetails,
                    installPlan = uiState.resolvedInstallPlan,
                    autoInstallDependencies = uiState.settings.autoInstallDependencies,
                    errorMessage = uiState.detailsError,
                    downloadState = uiState.activeDownload,
                    onDismiss = viewModel::dismissModDetails,
                    onSelectVersion = { versionId ->
                        viewModel.openModDetails(sheetProject, preferredVersionId = versionId)
                    },
                    onInstallClick = viewModel::installModWithDependencies
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoragePermissionBanner(
    onGrantClick: () -> Unit,
    onSafFallbackClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "All Files Access Required for Public Launchers",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss Banner",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Grant MANAGE_EXTERNAL_STORAGE to automatically scan and install mods into /sdcard/PojavLauncher, /sdcard/Games/Mojo, and /sdcard/Amethyst, or select a folder via SAF.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(onClick = onGrantClick) {
                    Text("Grant Access")
                }
                TextButton(onClick = onSafFallbackClick) {
                    Text("Use SAF Folder Picker")
                }
            }
        }
    }
}

@Composable
private fun ModBrowseContent(
    searchQuery: String,
    selectedCategories: Set<ModCategory>,
    sortIndex: ModSortIndex,
    compactCards: Boolean,
    installedMods: List<InstalledMod>,
    isLoading: Boolean,
    mods: List<ModProject>,
    errorMessage: String?,
    onQueryChange: (String) -> Unit,
    onSelectSort: (ModSortIndex) -> Unit,
    onToggleCategory: (ModCategory) -> Unit,
    onClearFilters: () -> Unit,
    onRetry: () -> Unit,
    onModClick: (ModProject) -> Unit
) {
    val haptics = LocalDropperHaptics.current
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            placeholder = { Text("Search Modrinth mods (e.g. Sodium, Iris, FerriteCore)...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        haptics.tick()
                        onQueryChange("")
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )

        // Sort + Dynamic Category Filter Bar
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                Box {
                    AssistChip(
                        onClick = {
                            haptics.tick()
                            sortMenuExpanded = true
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = sortIndex.displayName,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    )

                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        ModSortIndex.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.displayName) },
                                onClick = {
                                    sortMenuExpanded = false
                                    onSelectSort(option)
                                }
                            )
                        }
                    }
                }
            }

            if (selectedCategories.isNotEmpty() || searchQuery.isNotEmpty()) {
                item {
                    AssistChip(
                        onClick = onClearFilters,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = "Reset",
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    )
                }
            }

            items(ModCategory.entries) { category ->
                FilterChip(
                    selected = category in selectedCategories,
                    onClick = { onToggleCategory(category) },
                    label = {
                        Text(
                            text = category.displayName,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        when {
            isLoading -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    CircularProgressIndicator()
                }
            }

            errorMessage != null -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onRetry) {
                            Text("Retry Search")
                        }
                    }
                }
            }

            mods.isEmpty() -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "No matching mods found for this version and loader.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compactCards) 6.dp else 10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(mods, key = { it.projectId }) { mod ->
                        val isInstalled = remember(mod.slug, mod.title, installedMods) {
                            val slugNorm = mod.slug.lowercase()
                            val titleNorm = mod.title.lowercase().replace(" ", "")
                            installedMods.any { installed ->
                                val fileNorm = installed.displayName.lowercase()
                                (slugNorm.length >= 3 && fileNorm.contains(slugNorm)) ||
                                    (titleNorm.length >= 4 && fileNorm.replace("-", "").contains(titleNorm))
                            }
                        }

                        ModFeedCard(
                            mod = mod,
                            compact = compactCards,
                            isInstalled = isInstalled,
                            onClick = { onModClick(mod) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModFeedCard(
    mod: ModProject,
    compact: Boolean,
    isInstalled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) 10.dp else 14.dp)
        ) {
            AsyncImage(
                model = mod.iconUrl,
                contentDescription = mod.title,
                modifier = Modifier
                    .size(if (compact) 44.dp else 56.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = mod.title,
                        style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (isInstalled) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Installed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = formatDownloads(mod.downloads),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "by ${mod.author}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(if (compact) 2.dp else 4.dp))

                Text(
                    text = mod.summary,
                    style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatDownloads(downloads: Long): String {
    return when {
        downloads >= 1_000_000 -> String.format("%.1fM dl", downloads / 1_000_000.0)
        downloads >= 1_000 -> String.format("%.1fK dl", downloads / 1_000.0)
        else -> "$downloads dl"
    }
}
