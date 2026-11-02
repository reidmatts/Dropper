package mod.dropper.com.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mod.dropper.com.R
import mod.dropper.com.data.modrinth.ModRepositoryImpl
import mod.dropper.com.domain.model.AppSettings
import mod.dropper.com.domain.model.LauncherType
import mod.dropper.com.domain.model.MinecraftInstance
import mod.dropper.com.domain.model.ModLoader
import mod.dropper.com.domain.model.ThemeMode

val PRESET_MC_VERSIONS = listOf(
    "1.21.4", "1.21.1", "1.20.4", "1.20.1",
    "1.19.4", "1.19.2", "1.18.2", "1.16.5"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InstanceSelectorTopBar(
    settings: AppSettings,
    activeInstance: MinecraftInstance?,
    allInstances: List<MinecraftInstance>,
    onSelectInstance: (MinecraftInstance) -> Unit,
    onRefreshInstances: () -> Unit,
    onPickSafFolder: (gameVersion: String, modLoader: ModLoader) -> Unit,
    onAddCustomPath: (path: String, name: String, launcher: LauncherType, gameVersion: String, modLoader: ModLoader) -> Unit,
    onUpdateInstanceConfig: (gameVersion: String, modLoader: ModLoader) -> Unit,
    onRemoveCustomInstance: ((String) -> Unit)? = null,
    onUpdateSettings: (AppSettings) -> Unit,
    availableGameVersions: List<String> = emptyList(),
    isLoadingVersions: Boolean = false,
    onRefreshVersions: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptics = LocalDropperHaptics.current
    var instanceMenuExpanded by remember { mutableStateOf(false) }
    var versionMenuExpanded by remember { mutableStateOf(false) }
    var loaderMenuExpanded by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var showAllVersionsPicker by remember { mutableStateOf(false) }
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val effectiveVersions = remember(availableGameVersions) {
        if (availableGameVersions.isNotEmpty()) availableGameVersions else ModRepositoryImpl.DEFAULT_MINECRAFT_VERSIONS
    }
    val latestVersion = effectiveVersions.firstOrNull() ?: ModRepositoryImpl.DEFAULT_MINECRAFT_VERSIONS.first()
    val quickPresetVersions = remember(effectiveVersions) {
        val topRecent = effectiveVersions.take(8)
        val notable = listOf("1.20.1", "1.16.5", "1.12.2")
        (topRecent + notable).distinct().filter { it in effectiveVersions }
    }

    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // Row 1: Header (Branding & Global Controls)
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
                        painter = painterResource(id = R.drawable.ic_dropper_logo),
                        contentDescription = "Dropper Logo",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Dropper",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = activeInstance?.let {
                                "${it.installedModsCount} mods installed"
                            } ?: "No active instance",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 3 Clean, well-spaced action buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            haptics.click()
                            onRefreshInstances()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Rescan Launchers",
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            haptics.tick()
                            val nextTheme = when (settings.themeMode) {
                                ThemeMode.SYSTEM -> ThemeMode.DARK
                                ThemeMode.DARK -> ThemeMode.AMOLED
                                ThemeMode.AMOLED -> ThemeMode.LIGHT
                                ThemeMode.LIGHT -> ThemeMode.SYSTEM
                            }
                            onUpdateSettings(settings.copy(themeMode = nextTheme))
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        val isDarkLike = settings.themeMode == ThemeMode.DARK || settings.themeMode == ThemeMode.AMOLED
                        Icon(
                            imageVector = if (isDarkLike) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = "Cycle Theme (${settings.themeMode.displayName})",
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            haptics.click()
                            showSettingsDialog = true
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings & Preferences",
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: Merged Single Instance Profile Selector
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedCard(
                    onClick = {
                        haptics.click()
                        instanceMenuExpanded = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (activeInstance?.isSafUri == true) {
                                Icons.Default.FolderOpen
                            } else {
                                Icons.Default.SportsEsports
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeInstance?.name ?: "Select Game Instance",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = activeInstance?.path ?: "Tap to select launcher profile",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select Instance",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                DropdownMenu(
                    expanded = instanceMenuExpanded,
                    onDismissRequest = { instanceMenuExpanded = false }
                ) {
                    allInstances.forEach { inst ->
                        DropdownMenuItem(
                            leadingIcon = {
                                if (inst.id == activeInstance?.id) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Active",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            text = {
                                Column {
                                    Text(
                                        text = inst.name,
                                        fontWeight = if (inst.id == activeInstance?.id) FontWeight.Bold else FontWeight.Medium
                                    )
                                    Text(
                                        text = "${inst.gameVersion} (${inst.modLoader.displayName}) • ${inst.installedModsCount} mods",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                haptics.tick()
                                instanceMenuExpanded = false
                                onSelectInstance(inst)
                            }
                        )
                    }

                    HorizontalDivider()

                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        text = { Text("Add Custom / SAF Folder...", fontWeight = FontWeight.SemiBold) },
                        onClick = {
                            haptics.click()
                            instanceMenuExpanded = false
                            showAddFolderDialog = true
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 3: Revamped, Direct & Accessible Profile Target Bar
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Target:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // 1-Tap Version Quick Selector
                        Box {
                            AssistChip(
                                onClick = {
                                    haptics.tick()
                                    versionMenuExpanded = true
                                },
                                label = {
                                    Text(
                                        text = activeInstance?.gameVersion ?: "1.20.1",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )

                            DropdownMenu(
                                expanded = versionMenuExpanded,
                                onDismissRequest = { versionMenuExpanded = false }
                            ) {
                                quickPresetVersions.forEach { presetVer ->
                                    val isCurrent = activeInstance?.gameVersion == presetVer
                                    val isLatest = presetVer == latestVersion
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = presetVer,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                                )
                                                if (isLatest) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                                        modifier = Modifier.padding(start = 8.dp)
                                                    ) {
                                                        Text(
                                                            text = "LATEST",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        leadingIcon = if (isCurrent) {
                                            {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = "Active",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        } else null,
                                        onClick = {
                                            haptics.tick()
                                            versionMenuExpanded = false
                                            if (activeInstance != null) {
                                                onUpdateInstanceConfig(presetVer, activeInstance.modLoader)
                                            }
                                        }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.List,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    text = {
                                        Text(
                                            "All Versions (${effectiveVersions.size})...",
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    onClick = {
                                        haptics.click()
                                        versionMenuExpanded = false
                                        showAllVersionsPicker = true
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(Icons.Default.Edit, contentDescription = null)
                                    },
                                    text = { Text("Configure Target...") },
                                    onClick = {
                                        haptics.click()
                                        versionMenuExpanded = false
                                        showConfigDialog = true
                                    }
                                )
                            }
                        }

                        // 1-Tap Loader Quick Selector
                        Box {
                            AssistChip(
                                onClick = {
                                    haptics.tick()
                                    loaderMenuExpanded = true
                                },
                                label = {
                                    Text(
                                        text = activeInstance?.modLoader?.displayName ?: "Fabric",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )

                            DropdownMenu(
                                expanded = loaderMenuExpanded,
                                onDismissRequest = { loaderMenuExpanded = false }
                            ) {
                                ModLoader.entries.forEach { loader ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = loader.displayName,
                                                fontWeight = if (activeInstance?.modLoader == loader) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            haptics.tick()
                                            loaderMenuExpanded = false
                                            if (activeInstance != null) {
                                                onUpdateInstanceConfig(activeInstance.gameVersion, loader)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Direct Edit / Configure Button
                    IconButton(
                        onClick = {
                            haptics.click()
                            showConfigDialog = true
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Configure Profile Target",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    // Searchable All Versions Picker Dialog (opened directly from version chip or from config dialog)
    if (showAllVersionsPicker && activeInstance != null) {
        SearchableVersionPickerDialog(
            currentVersion = activeInstance.gameVersion,
            availableVersions = effectiveVersions,
            isLoading = isLoadingVersions,
            onRefresh = { onRefreshVersions?.invoke() },
            onSelectVersion = { chosenVer ->
                showAllVersionsPicker = false
                onUpdateInstanceConfig(chosenVer, activeInstance.modLoader)
            },
            onDismiss = { showAllVersionsPicker = false }
        )
    }

    // Revamped, Clean & Direct Configure Profile Target Dialog
    if (showConfigDialog && activeInstance != null) {
        RevampedConfigureTargetDialog(
            instance = activeInstance,
            availableVersions = effectiveVersions,
            isLoadingVersions = isLoadingVersions,
            onRefreshVersions = onRefreshVersions,
            onDismiss = { showConfigDialog = false },
            onSave = { ver, loader ->
                showConfigDialog = false
                onUpdateInstanceConfig(ver, loader)
            },
            onDeleteInstance = if (onRemoveCustomInstance != null && (activeInstance.id.startsWith("CUSTOM_") || activeInstance.id.startsWith("SAF_"))) {
                {
                    showConfigDialog = false
                    onRemoveCustomInstance(activeInstance.id)
                }
            } else null
        )
    }

    if (showAddFolderDialog) {
        AddInstanceDialog(
            availableVersions = effectiveVersions,
            onDismiss = { showAddFolderDialog = false },
            onPickSaf = { ver, loader ->
                showAddFolderDialog = false
                onPickSafFolder(ver, loader)
            },
            onSaveDirectPath = { path, name, launcher, ver, loader ->
                showAddFolderDialog = false
                onAddCustomPath(path, name, launcher, ver, loader)
            }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            settings = settings,
            onDismiss = { showSettingsDialog = false },
            onUpdateSettings = onUpdateSettings
        )
    }
}

/**
 * Full searchable dialog displaying all official Minecraft versions fetched from Modrinth.
 * Prioritizes the latest release at the top with a distinct badge.
 */
@Composable
fun SearchableVersionPickerDialog(
    currentVersion: String,
    availableVersions: List<String>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onSelectVersion: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalDropperHaptics.current
    var searchQuery by remember { mutableStateOf("") }
    val latestVersion = availableVersions.firstOrNull()

    val filteredVersions = remember(searchQuery, availableVersions) {
        if (searchQuery.isBlank()) availableVersions
        else availableVersions.filter { it.contains(searchQuery.trim(), ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Minecraft Versions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isLoading) "Checking Modrinth for latest updates..." else "${availableVersions.size} releases • Newest first",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = {
                        haptics.click()
                        onRefresh()
                    },
                    enabled = !isLoading,
                    modifier = Modifier.size(36.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Check for New Updates",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search version (e.g. 1.21, 1.16)...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = {
                                haptics.tick()
                                searchQuery = ""
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (filteredVersions.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Text(
                            text = "No versions match '$searchQuery'",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(filteredVersions, key = { it }) { ver ->
                            val isSelected = ver.equals(currentVersion.trim(), ignoreCase = true)
                            val isLatest = ver == latestVersion
                            Surface(
                                onClick = {
                                    haptics.confirm()
                                    onSelectVersion(ver)
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = ver,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected || isLatest) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isLatest) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.tertiaryContainer,
                                            modifier = Modifier.padding(end = 6.dp)
                                        ) {
                                            Text(
                                                text = "LATEST",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                haptics.click()
                onDismiss()
            }) {
                Text("Close")
            }
        }
    )
}

/**
 * Revamped, direct, and simple Configure Profile Target dialog.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RevampedConfigureTargetDialog(
    instance: MinecraftInstance,
    availableVersions: List<String>,
    isLoadingVersions: Boolean,
    onRefreshVersions: (() -> Unit)?,
    onDismiss: () -> Unit,
    onSave: (String, ModLoader) -> Unit,
    onDeleteInstance: (() -> Unit)?
) {
    val haptics = LocalDropperHaptics.current
    var versionText by remember(instance) { mutableStateOf(instance.gameVersion) }
    var selectedLoader by remember(instance) { mutableStateOf(instance.modLoader) }
    var showAllVersionsInsideDialog by remember { mutableStateOf(false) }

    val latestVersion = availableVersions.firstOrNull() ?: ModRepositoryImpl.DEFAULT_MINECRAFT_VERSIONS.first()
    val quickPresets = remember(availableVersions) {
        val topRecent = availableVersions.take(8)
        val notable = listOf("1.20.1", "1.16.5", "1.12.2")
        (topRecent + notable).distinct().filter { it in availableVersions }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Configure Profile Target",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = instance.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Storage Path Display
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Target Directory",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${instance.path}/mods",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Mod Loader Selector (One-Tap Chips)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Mod Loader",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ModLoader.entries.forEach { loader ->
                            FilterChip(
                                selected = selectedLoader == loader,
                                onClick = {
                                    haptics.tick()
                                    selectedLoader = loader
                                },
                                label = {
                                    Text(
                                        text = loader.displayName,
                                        fontWeight = if (selectedLoader == loader) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            )
                        }
                    }
                }

                // Minecraft Version Section
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Minecraft Version",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (isLoadingVersions) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                Text(
                                    text = "Updating...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else if (onRefreshVersions != null) {
                            TextButton(
                                onClick = {
                                    haptics.click()
                                    onRefreshVersions()
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Check Updates", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = versionText,
                        onValueChange = { versionText = it },
                        label = { Text("Game Version") },
                        placeholder = { Text("e.g. 1.20.1") },
                        trailingIcon = {
                            if (versionText.isNotBlank()) {
                                IconButton(onClick = {
                                    haptics.tick()
                                    versionText = ""
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Quick Presets:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = {
                                haptics.click()
                                showAllVersionsInsideDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("All (${availableVersions.size})...", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        quickPresets.forEach { preset ->
                            val isLatest = preset == latestVersion
                            val isSelected = versionText.trim() == preset
                            AssistChip(
                                onClick = {
                                    haptics.tick()
                                    versionText = preset
                                },
                                label = {
                                    Text(
                                        text = if (isLatest) "$preset (Latest)" else preset,
                                        fontWeight = if (isSelected || isLatest) FontWeight.Bold else FontWeight.Normal,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary) }
                                } else null
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            haptics.click()
                            showAllVersionsInsideDialog = true
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Search & Pick from All ${availableVersions.size} Releases")
                    }

                    Text(
                        text = "Auto-fetches newly released updates from Modrinth.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Optional Delete Action for Custom Profiles
                if (onDeleteInstance != null) {
                    HorizontalDivider()
                    OutlinedButton(
                        onClick = {
                            haptics.heavy()
                            onDeleteInstance()
                        },
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Remove This Custom Profile")
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    haptics.confirm()
                    onSave(versionText.trim().ifBlank { latestVersion }, selectedLoader)
                }
            ) {
                Text("Apply Target")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                haptics.click()
                onDismiss()
            }) {
                Text("Cancel")
            }
        }
    )

    if (showAllVersionsInsideDialog) {
        SearchableVersionPickerDialog(
            currentVersion = versionText,
            availableVersions = availableVersions,
            isLoading = isLoadingVersions,
            onRefresh = { onRefreshVersions?.invoke() },
            onSelectVersion = { chosen ->
                versionText = chosen
                showAllVersionsInsideDialog = false
            },
            onDismiss = { showAllVersionsInsideDialog = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddInstanceDialog(
    availableVersions: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onPickSaf: (String, ModLoader) -> Unit,
    onSaveDirectPath: (String, String, LauncherType, String, ModLoader) -> Unit
) {
    val haptics = LocalDropperHaptics.current
    var nameText by remember { mutableStateOf("Custom Profile") }
    var pathText by remember { mutableStateOf("/sdcard/PojavLauncher/.minecraft") }
    var versionText by remember { mutableStateOf(availableVersions.firstOrNull() ?: "1.20.1") }
    var selectedLoader by remember { mutableStateOf(ModLoader.FABRIC) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Instance Directory") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("Profile Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = versionText,
                    onValueChange = { versionText = it },
                    label = { Text("Minecraft Version (e.g. 1.20.1, 1.21.1)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Mod Loader",
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ModLoader.entries.forEach { loader ->
                        FilterChip(
                            selected = selectedLoader == loader,
                            onClick = {
                                haptics.tick()
                                selectedLoader = loader
                            },
                            label = {
                                Text(
                                    text = loader.displayName,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        )
                    }
                }
                OutlinedTextField(
                    value = pathText,
                    onValueChange = { pathText = it },
                    label = { Text("Direct Folder Path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val defaultLatest = availableVersions.firstOrNull() ?: ModRepositoryImpl.DEFAULT_MINECRAFT_VERSIONS.first()
                OutlinedButton(
                    onClick = {
                        haptics.click()
                        onPickSaf(versionText.trim().ifBlank { defaultLatest }, selectedLoader)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pick Folder via SAF Tree")
                }
            }
        },
        confirmButton = {
            val defaultLatest = availableVersions.firstOrNull() ?: ModRepositoryImpl.DEFAULT_MINECRAFT_VERSIONS.first()
            FilledTonalButton(
                onClick = {
                    haptics.confirm()
                    onSaveDirectPath(
                        pathText.trim(),
                        nameText.trim(),
                        LauncherType.CUSTOM,
                        versionText.trim().ifBlank { defaultLatest },
                        selectedLoader
                    )
                }
            ) {
                Text("Add Path")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                haptics.click()
                onDismiss()
            }) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsDialog(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onUpdateSettings: (AppSettings) -> Unit
) {
    val haptics = LocalDropperHaptics.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings & QoL Preferences") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Appearance & Theme",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = {
                                haptics.tick()
                                onUpdateSettings(settings.copy(themeMode = mode))
                            },
                            label = {
                                Text(
                                    text = mode.displayName,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        )
                    }
                }

                SettingSwitchRow(
                    title = "Dynamic Material You Colors",
                    subtitle = "Use Android 12+ wallpaper palette",
                    checked = settings.useDynamicColor,
                    onCheckedChange = {
                        haptics.tick()
                        onUpdateSettings(settings.copy(useDynamicColor = it))
                    }
                )

                SettingSwitchRow(
                    title = "Compact Mod Feed Cards",
                    subtitle = "Fit more mods on screen while browsing",
                    checked = settings.compactModCards,
                    onCheckedChange = {
                        haptics.tick()
                        onUpdateSettings(settings.copy(compactModCards = it))
                    }
                )

                SettingSwitchRow(
                    title = "Haptic Feedback & Vibration Simulation",
                    subtitle = "Tactile response on buttons and toggles; simulates haptics via micro-pulse vibration if native haptics are unavailable",
                    checked = settings.enableHaptics,
                    onCheckedChange = {
                        onUpdateSettings(settings.copy(enableHaptics = it))
                        if (it) haptics.tick()
                    }
                )

                HorizontalDivider()

                Text(
                    text = "Downloads & Dependency Management",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                SettingSwitchRow(
                    title = "Auto-Install Required Dependencies",
                    subtitle = "Automatically download companion mods (e.g. Fabric API, Cloth Config)",
                    checked = settings.autoInstallDependencies,
                    onCheckedChange = {
                        haptics.tick()
                        onUpdateSettings(settings.copy(autoInstallDependencies = it))
                    }
                )

                SettingSwitchRow(
                    title = "Verify SHA-512 Checksums",
                    subtitle = "Validate downloaded .jar integrity against Modrinth hashes",
                    checked = settings.verifySha512,
                    onCheckedChange = {
                        haptics.tick()
                        onUpdateSettings(settings.copy(verifySha512 = it))
                    }
                )
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = {
                haptics.click()
                onDismiss()
            }) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
