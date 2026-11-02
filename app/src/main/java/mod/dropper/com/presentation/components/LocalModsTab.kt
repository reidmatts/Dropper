package mod.dropper.com.presentation.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mod.dropper.com.domain.model.InstalledMod
import mod.dropper.com.domain.model.LocalModFilter
import mod.dropper.com.domain.model.MinecraftInstance

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocalModsTab(
    activeInstance: MinecraftInstance?,
    installedMods: List<InstalledMod>,
    localSearchQuery: String,
    localModFilter: LocalModFilter,
    isLoading: Boolean,
    onLocalSearchChange: (String) -> Unit,
    onSelectFilter: (LocalModFilter) -> Unit,
    onEnableAll: () -> Unit,
    onDisableAll: () -> Unit,
    onToggleMod: (fileName: String) -> Unit,
    onDeleteMod: (fileName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalDropperHaptics.current
    var modPendingDelete by remember { mutableStateOf<InstalledMod?>(null) }

    val displayedMods = remember(installedMods, localSearchQuery, localModFilter) {
        installedMods.filter { mod ->
            val matchesQuery = localSearchQuery.isBlank() ||
                mod.displayName.contains(localSearchQuery.trim(), ignoreCase = true) ||
                mod.fileName.contains(localSearchQuery.trim(), ignoreCase = true)
            val matchesFilter = when (localModFilter) {
                LocalModFilter.ALL -> true
                LocalModFilter.ACTIVE -> mod.isEnabled
                LocalModFilter.DISABLED -> !mod.isEnabled
            }
            matchesQuery && matchesFilter
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (activeInstance != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${activeInstance.name} (${installedMods.count { it.isEnabled }} active / ${installedMods.size} total)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "${activeInstance.path}/mods",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (installedMods.isNotEmpty()) {
                        val activeCount = installedMods.count { it.isEnabled }
                        val disabledCount = installedMods.size - activeCount

                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            AssistChip(
                                onClick = {
                                    haptics.confirm()
                                    onEnableAll()
                                },
                                enabled = disabledCount > 0,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.height(16.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = if (disabledCount > 0) "Enable All ($disabledCount)" else "All Enabled",
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            )
                            AssistChip(
                                onClick = {
                                    haptics.heavy()
                                    onDisableAll()
                                },
                                enabled = activeCount > 0,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.PowerSettingsNew,
                                        contentDescription = null,
                                        modifier = Modifier.height(16.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = if (activeCount > 0) "Disable All ($activeCount)" else "All Disabled",
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        if (installedMods.isNotEmpty()) {
            OutlinedTextField(
                value = localSearchQuery,
                onValueChange = onLocalSearchChange,
                placeholder = { Text("Filter installed mods...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (localSearchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            haptics.tick()
                            onLocalSearchChange("")
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear filter")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                LocalModFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = localModFilter == filter,
                        onClick = {
                            haptics.tick()
                            onSelectFilter(filter)
                        },
                        label = {
                            Text(
                                text = filter.displayName,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    )
                }
            }
        }

        when {
            isLoading -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    CircularProgressIndicator()
                }
            }

            installedMods.isEmpty() -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No mods installed in this instance yet.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Browse the Modrinth Feed tab to download and install compatible .jar mods directly into this instance's mods/ folder.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            displayedMods.isEmpty() -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                ) {
                    Text(
                        text = "No installed mods match the current filter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayedMods, key = { it.fileName }) { mod ->
                        InstalledModRow(
                            mod = mod,
                            onToggle = {
                                haptics.tick()
                                onToggleMod(mod.fileName)
                            },
                            onDelete = {
                                haptics.heavy()
                                modPendingDelete = mod
                            }
                        )
                    }
                }
            }
        }
    }

    val targetToDelete = modPendingDelete
    if (targetToDelete != null) {
        AlertDialog(
            onDismissRequest = { modPendingDelete = null },
            title = { Text("Delete Mod?") },
            text = {
                Text("Are you sure you want to permanently delete ${targetToDelete.fileName} from this instance?")
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        haptics.heavy()
                        val file = targetToDelete.fileName
                        modPendingDelete = null
                        onDeleteMod(file)
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    haptics.click()
                    modPendingDelete = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun InstalledModRow(
    mod: InstalledMod,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (mod.isEnabled) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            }
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mod.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val sizeKb = (mod.sizeBytes / 1024).coerceAtLeast(1)
                val stateLabel = if (mod.isEnabled) "Active (.jar)" else "Disabled (.jar.disabled)"
                Text(
                    text = "$stateLabel • ${sizeKb} KB",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (mod.isEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Switch(
                checked = mod.isEnabled,
                onCheckedChange = { onToggle() }
            )

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete ${mod.displayName}",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
