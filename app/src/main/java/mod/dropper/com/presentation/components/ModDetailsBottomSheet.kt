package mod.dropper.com.presentation.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import mod.dropper.com.domain.model.DependencyType
import mod.dropper.com.domain.model.MinecraftInstance
import mod.dropper.com.domain.model.ModProject
import mod.dropper.com.domain.model.ResolvedModInstallPlan
import mod.dropper.com.presentation.ActiveDownloadState

private enum class ModDetailsSection(val label: String) {
    OVERVIEW("Overview"),
    DEPENDENCIES("Dependencies"),
    CHANGELOG("Changelog")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModDetailsBottomSheet(
    project: ModProject,
    activeInstance: MinecraftInstance?,
    isResolving: Boolean,
    installPlan: ResolvedModInstallPlan?,
    autoInstallDependencies: Boolean,
    errorMessage: String?,
    downloadState: ActiveDownloadState,
    onDismiss: () -> Unit,
    onSelectVersion: (versionId: String) -> Unit,
    onInstallClick: (ResolvedModInstallPlan) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val haptics = LocalDropperHaptics.current
    var versionMenuExpanded by remember { mutableStateOf(false) }
    var activeSection by remember { mutableStateOf(ModDetailsSection.OVERVIEW) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 18.dp, vertical = 4.dp)
        ) {
            // Pinned Website-Style Mod Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AsyncImage(
                    model = project.iconUrl,
                    contentDescription = project.title,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "by ${project.author} • ${formatNumber(project.downloads)} downloads",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                AssistChip(
                    onClick = {
                        haptics.click()
                        val slugOrId = project.slug.ifBlank { project.projectId }
                        val webUrl = "https://modrinth.com/mod/$slugOrId"
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Web",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isResolving) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Text(
                            text = "Loading project page & resolving dependencies for ${activeInstance?.gameVersion ?: ""} (${activeInstance?.modLoader?.displayName ?: ""})...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else if (installPlan != null) {
                val instanceName = activeInstance?.name ?: "Active Instance"
                val depCount = if (autoInstallDependencies) installPlan.requiredDependencies.size else 0

                // Prominent Install Button / Progress Bar
                if (downloadState.isDownloading) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Downloading (${downloadState.itemIndex}/${downloadState.itemCount}): ${downloadState.currentFileName}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { (downloadState.progressPercent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${downloadState.progressPercent}% • ${formatBytes(downloadState.bytesRead)} / ${formatBytes(downloadState.totalBytes)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            haptics.confirm()
                            onInstallClick(installPlan)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        val suffix = if (depCount > 0) " (+$depCount Required Dep)" else ""
                        Text(
                            text = "Install to $instanceName$suffix",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Version Selector Dropdown (if multiple compatible versions exist)
                if (installPlan.availableVersions.size > 1) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                haptics.tick()
                                versionMenuExpanded = true
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Release: ${installPlan.primaryVersion.versionNumber} (${installPlan.primaryVersion.name})",
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = versionMenuExpanded,
                            onDismissRequest = { versionMenuExpanded = false }
                        ) {
                            installPlan.availableVersions.forEach { ver ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = "${ver.versionNumber} — ${ver.name}",
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "MC: ${ver.gameVersions.take(3).joinToString(", ")} • ${ver.loaders.joinToString(", ")}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        haptics.tick()
                                        versionMenuExpanded = false
                                        onSelectVersion(ver.id)
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Website-Style Section Navigation Chips (Overview | Dependencies | Changelog)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ModDetailsSection.entries.forEach { section ->
                        val badgeLabel = when (section) {
                            ModDetailsSection.OVERVIEW -> section.label
                            ModDetailsSection.DEPENDENCIES -> "${section.label} (${installPlan.primaryVersion.dependencies.size})"
                            ModDetailsSection.CHANGELOG -> section.label
                        }
                        FilterChip(
                            selected = activeSection == section,
                            onClick = {
                                haptics.tick()
                                activeSection = section
                            },
                            label = {
                                Text(
                                    text = badgeLabel,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                // Scrollable Body Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (activeSection) {
                        ModDetailsSection.OVERVIEW -> {
                            ModrinthWebsiteContent(
                                rawMarkdownOrHtml = installPlan.primaryProject.descriptionBody.ifBlank { project.summary },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        ModDetailsSection.DEPENDENCIES -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(bottom = 24.dp)
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Target Artifact: ${installPlan.primaryFile.fileName}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Version ${installPlan.primaryVersion.versionNumber} • ${formatBytes(installPlan.primaryFile.size)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (!installPlan.primaryFile.sha512.isNullOrBlank()) {
                                            Text(
                                                text = "SHA-512: ${installPlan.primaryFile.sha512.take(28)}...",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                if (installPlan.primaryVersion.dependencies.isEmpty()) {
                                    Text(
                                        text = "No external companion mods required for this release.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                } else {
                                    installPlan.primaryVersion.dependencies.forEach { dep ->
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .padding(end = 8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (dep.dependencyType == DependencyType.REQUIRED) {
                                                            Icons.Default.CheckCircle
                                                        } else {
                                                            Icons.Default.Extension
                                                        },
                                                        contentDescription = null,
                                                        tint = if (dep.dependencyType == DependencyType.REQUIRED) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                        },
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column {
                                                        Text(
                                                            text = dep.resolvedTitle ?: dep.projectId ?: "Companion Mod",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = if (dep.dependencyType == DependencyType.REQUIRED) {
                                                                "Required companion mod (auto-installed)"
                                                            } else {
                                                                "Optional / companion integration"
                                                            },
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                AssistChip(
                                                    onClick = {},
                                                    label = {
                                                        Text(
                                                            text = dep.dependencyType.name,
                                                            style = MaterialTheme.typography.labelSmall,
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
                        }

                        ModDetailsSection.CHANGELOG -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = "${installPlan.primaryVersion.name} (${installPlan.primaryVersion.versionNumber})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                ModrinthWebsiteContent(
                                    rawMarkdownOrHtml = installPlan.primaryVersion.changelog,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatNumber(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format("%.2f MB", mb)
    } else {
        String.format("%.0f KB", kb)
    }
}
