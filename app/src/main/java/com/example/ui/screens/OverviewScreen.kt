package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.BookmarkEntity
import com.example.data.local.RecentFileEntity
import com.example.model.CategorySummary
import com.example.model.FileCategory
import com.example.model.Formatters
import com.example.model.StorageVolumeInfo
import com.example.model.StorageVolumeKind
import com.example.ui.components.MonochromeStorageBar

@Composable
fun OverviewScreen(
    volumes: List<StorageVolumeInfo>,
    categories: List<CategorySummary>,
    bookmarks: List<BookmarkEntity>,
    recentFiles: List<RecentFileEntity>,
    hasAllFilesAccess: Boolean,
    runningJobsCount: Int,
    onVolumeClick: (StorageVolumeInfo) -> Unit,
    onCategoryClick: (FileCategory) -> Unit,
    onBookmarkClick: (BookmarkEntity) -> Unit,
    onRecentClick: (RecentFileEntity) -> Unit,
    onMountSafFolder: () -> Unit,
    onImportPhotoPicker: () -> Unit,
    onOpenAllFilesSettings: () -> Unit,
    onOpenJobsSheet: () -> Unit,
    onRefresh: () -> Unit
) {
    var quickMenuExpanded by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 840.dp
        val isMedium = maxWidth >= 600.dp

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Minimal Monochrome Header + Quick Actions Dropdown
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MONOLITH FILES",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Storage Overview",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (runningJobsCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary,
                            onClick = onOpenJobsSheet,
                            modifier = Modifier.testTag("overview_jobs_badge")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Sync,
                                    contentDescription = "Active background jobs",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "$runningJobsCount",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    IconButton(onClick = onRefresh, modifier = Modifier.testTag("overview_refresh_button")) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh storage stats")
                    }

                    Box {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            onClick = { quickMenuExpanded = true },
                            modifier = Modifier.testTag("overview_quick_actions_dropdown")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = "Quick actions", modifier = Modifier.size(18.dp))
                                Text("Actions", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        DropdownMenu(
                            expanded = quickMenuExpanded,
                            onDismissRequest = { quickMenuExpanded = false },
                            containerColor = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Mount Folder (SAF)") },
                                leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null) },
                                onClick = {
                                    quickMenuExpanded = false
                                    onMountSafFolder()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Import Photos / Videos") },
                                leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null) },
                                onClick = {
                                    quickMenuExpanded = false
                                    onImportPhotoPicker()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (hasAllFilesAccess) "All-Files Access: Enabled" else "Enable All-Files Access") },
                                leadingIcon = { Icon(Icons.Outlined.Security, contentDescription = null) },
                                onClick = {
                                    quickMenuExpanded = false
                                    onOpenAllFilesSettings()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Background Jobs") },
                                leadingIcon = { Icon(Icons.Outlined.Sync, contentDescription = null) },
                                onClick = {
                                    quickMenuExpanded = false
                                    onOpenJobsSheet()
                                }
                            )
                        }
                    }
                }
            }

            // Storage Volumes (Internal, SD Card, USB OTG, App Workspace, SAF Mounts)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "VOLUMES",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val volumeCols = when {
                        isWide -> 3
                        isMedium -> 2
                        else -> 1
                    }
                    val rows = volumes.chunked(volumeCols)
                    rows.forEach { rowVolumes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowVolumes.forEach { volume ->
                                StorageVolumeCard(
                                    volume = volume,
                                    onClick = {
                                        if (!volume.isAvailable && (volume.kind == StorageVolumeKind.SD_CARD || volume.kind == StorageVolumeKind.USB_OTG)) {
                                            onMountSafFolder()
                                        } else {
                                            onVolumeClick(volume)
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(volumeCols - rowVolumes.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Hybrid Access Status Strip
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Hybrid Storage Access",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = if (hasAllFilesAccess) {
                                        "SAF + MediaStore + All-Files Access active"
                                    } else {
                                        "SAF & MediaStore active · Optional All-Files Access off"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = onMountSafFolder,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("mount_saf_button")
                            ) {
                                Text("Mount SAF Folder", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = onOpenAllFilesSettings,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("all_files_toggle_button")
                            ) {
                                Text(
                                    if (hasAllFilesAccess) "All-Files On" else "All-Files Access",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Categories Grid (Videos, Images, Audio, Documents, APKs, Downloads)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "CATEGORIES",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val catCols = when {
                        isWide -> 3
                        isMedium -> 3
                        else -> 2
                    }
                    val displayCategories = if (categories.isEmpty()) {
                        FileCategory.entries.map { CategorySummary(it, 0, 0L) }
                    } else {
                        categories
                    }
                    displayCategories.chunked(catCols).forEach { rowCats ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowCats.forEach { summary ->
                                CategoryTile(
                                    summary = summary,
                                    onClick = { onCategoryClick(summary.category) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(catCols - rowCats.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Pinned Bookmarks
            if (bookmarks.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "PINNED FOLDERS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(bookmarks, key = { it.pathOrUri }) { bm ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    onClick = { onBookmarkClick(bm) }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.BookmarkBorder,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(bm.label, style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Recent Files
            if (recentFiles.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "RECENT",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column {
                                recentFiles.take(6).forEachIndexed { index, recent ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onRecentClick(recent) }
                                            .padding(horizontal = 14.dp, vertical = 12.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.History,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = recent.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${Formatters.formatBytes(recent.sizeBytes)} · ${Formatters.formatDate(recent.openedAtMs)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (index < recentFiles.take(6).lastIndex) {
                                        androidx.compose.material3.HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun StorageVolumeCard(
    volume: StorageVolumeInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon: ImageVector = when (volume.kind) {
        StorageVolumeKind.INTERNAL -> Icons.Outlined.Smartphone
        StorageVolumeKind.SD_CARD -> Icons.Outlined.SdStorage
        StorageVolumeKind.USB_OTG -> Icons.Outlined.Usb
        StorageVolumeKind.SAF_MOUNT -> Icons.Outlined.FolderOpen
        StorageVolumeKind.APP_WORKSPACE -> Icons.Outlined.CreateNewFolder
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("volume_card_${volume.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = volume.label, modifier = Modifier.size(18.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = volume.label,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            !volume.isAvailable -> "Not mounted · Tap to connect via SAF"
                            volume.totalBytes > 0L -> "${(volume.usedFraction * 100).toInt()}% capacity · ${Formatters.formatBytes(volume.totalBytes)}"
                            else -> "SAF Document Tree"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (volume.isAvailable) {
                MonochromeStorageBar(
                    usedFraction = volume.usedFraction,
                    usedBytes = volume.usedBytes,
                    totalBytes = volume.totalBytes
                )
            }
        }
    }
}

@Composable
private fun CategoryTile(
    summary: CategorySummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when (summary.category) {
        FileCategory.VIDEOS -> Icons.Outlined.VideoLibrary
        FileCategory.IMAGES -> Icons.Outlined.Image
        FileCategory.AUDIO -> Icons.Outlined.AudioFile
        FileCategory.DOCUMENTS -> Icons.Outlined.Description
        FileCategory.APKS -> Icons.Outlined.Android
        FileCategory.DOWNLOADS -> Icons.Outlined.Download
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("category_tile_${summary.category.name.lowercase()}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(icon, contentDescription = summary.category.label, modifier = Modifier.size(20.dp))
                Text(
                    text = "${summary.itemCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Column {
                Text(summary.category.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = Formatters.formatBytes(summary.totalBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
