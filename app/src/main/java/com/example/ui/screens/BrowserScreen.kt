package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.UserPreferences
import com.example.model.ClipboardMode
import com.example.model.FileEntry
import com.example.model.Formatters
import com.example.model.SortField
import com.example.model.StorageVolumeInfo
import com.example.model.ViewMode
import com.example.ui.components.FileThumbnail
import com.example.ui.components.MonolithDropdownSelector
import com.example.ui.components.MonolithEmptyState
import com.example.viewmodel.BrowserState
import kotlin.math.roundToInt

@Composable
fun BrowserScreen(
    browserState: BrowserState,
    entries: List<FileEntry>,
    volumes: List<StorageVolumeInfo>,
    prefs: UserPreferences,
    isBookmarked: Boolean,
    onNavigateUp: () -> Boolean,
    onVolumeSelect: (StorageVolumeInfo) -> Unit,
    onBreadcrumbClick: (Int) -> Unit,
    onSearchChange: (String) -> Unit,
    onViewModeChange: (ViewMode) -> Unit,
    onGridColumnsChange: (Int) -> Unit,
    onSortChange: (SortField, Boolean) -> Unit,
    onOpenFilterSheet: () -> Unit,
    onToggleBookmark: () -> Unit,
    onCreateFolderClick: () -> Unit,
    onEntryClick: (FileEntry) -> Unit,
    onEntryLongClick: (FileEntry) -> Unit,
    onInspectorSelect: (FileEntry?) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onStageClipboard: (List<FileEntry>, ClipboardMode) -> Unit,
    onPasteHere: () -> Unit,
    onClearClipboard: () -> Unit,
    onRenameRequest: (FileEntry) -> Unit,
    onDeleteRequest: (List<FileEntry>) -> Unit,
    onCompressRequest: (List<FileEntry>) -> Unit,
    onVaultEncryptRequest: (List<FileEntry>) -> Unit,
    onShareRequest: (List<FileEntry>) -> Unit,
    onPropertiesRequest: (FileEntry) -> Unit
) {
    BackHandler(enabled = browserState.selectedIds.isNotEmpty() || browserState.breadcrumbs.size > 1 || browserState.activeCategory != null) {
        onNavigateUp()
    }

    var searchExpanded by remember { mutableStateOf(false) }
    val selectedEntries = remember(entries, browserState.selectedIds) {
        entries.filter { it.id in browserState.selectedIds }
    }
    val selectableVolumes = remember(volumes) { volumes.filter { it.isAvailable } }
    val activeVolumeLabel = remember(volumes, browserState.activeVolumeId, browserState.activeCategory) {
        browserState.activeCategory?.label
            ?: volumes.firstOrNull { it.id == browserState.activeVolumeId }?.label
            ?: "Workspace"
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val showSplitInspector = maxWidth >= 840.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Toolbar: Volume Dropdown + Sort Dropdown + View Toggle + Sliders/Filter + Bookmark + New Folder
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        MonolithDropdownSelector(
                            label = "Volume",
                            selectedText = activeVolumeLabel,
                            options = selectableVolumes,
                            optionLabel = { it.label },
                            isSelected = { it.id == browserState.activeVolumeId && browserState.activeCategory == null },
                            onSelect = onVolumeSelect,
                            leadingIcon = Icons.Outlined.Storage,
                            modifier = Modifier.weight(1f),
                            testTag = "browser_volume_dropdown"
                        )

                        MonolithDropdownSelector(
                            label = "Sort",
                            selectedText = "${prefs.sortField.label} ${if (prefs.sortAscending) "↑" else "↓"}",
                            options = SortField.entries,
                            optionLabel = { it.label },
                            isSelected = { it == prefs.sortField },
                            onSelect = { field ->
                                val nextAsc = if (field == prefs.sortField) !prefs.sortAscending else true
                                onSortChange(field, nextAsc)
                            },
                            leadingIcon = Icons.AutoMirrored.Outlined.Sort,
                            testTag = "browser_sort_dropdown"
                        )

                        IconButton(
                            onClick = { searchExpanded = !searchExpanded },
                            modifier = Modifier.testTag("browser_search_toggle")
                        ) {
                            Icon(Icons.Outlined.Search, contentDescription = "Search directory")
                        }

                        IconButton(
                            onClick = {
                                onViewModeChange(if (prefs.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST)
                            },
                            modifier = Modifier.testTag("browser_view_toggle")
                        ) {
                            Icon(
                                imageVector = if (prefs.viewMode == ViewMode.LIST) Icons.Outlined.GridView else Icons.AutoMirrored.Outlined.ViewList,
                                contentDescription = "Toggle list or grid view"
                            )
                        }

                        IconButton(
                            onClick = onOpenFilterSheet,
                            modifier = Modifier.testTag("browser_filter_sliders_button")
                        ) {
                            Icon(Icons.Outlined.Tune, contentDescription = "Layout and filter sliders")
                        }
                    }

                    // Search Bar (expandable)
                    AnimatedVisibility(visible = searchExpanded || browserState.searchQuery.isNotEmpty()) {
                        OutlinedTextField(
                            value = browserState.searchQuery,
                            onValueChange = onSearchChange,
                            placeholder = { Text("Filter by filename…") },
                            singleLine = true,
                            trailingIcon = {
                                if (browserState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchChange("") }) {
                                        Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .testTag("browser_search_input")
                        )
                    }

                    // Inline Grid Density Slider when Grid View is active
                    AnimatedVisibility(visible = prefs.viewMode == ViewMode.GRID) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "GRID ${prefs.gridColumns} COLS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = prefs.gridColumns.toFloat(),
                                onValueChange = { onGridColumnsChange(it.roundToInt()) },
                                valueRange = 2f..6f,
                                steps = 3,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("inline_grid_density_slider")
                            )
                        }
                    }

                    // Breadcrumb Path Strip + Pin + New Folder
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            browserState.breadcrumbs.forEachIndexed { index, crumb ->
                                if (index > 0) {
                                    Icon(
                                        Icons.Outlined.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (index == browserState.breadcrumbs.lastIndex) {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    onClick = { onBreadcrumbClick(index) },
                                    modifier = Modifier.testTag("breadcrumb_$index")
                                ) {
                                    Text(
                                        text = crumb.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (index == browserState.breadcrumbs.lastIndex) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        if (browserState.activeCategory == null) {
                            IconButton(
                                onClick = onToggleBookmark,
                                modifier = Modifier.testTag("bookmark_folder_button")
                            ) {
                                Icon(
                                    imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = if (isBookmarked) "Remove bookmark" else "Bookmark folder"
                                )
                            }
                            IconButton(
                                onClick = onCreateFolderClick,
                                modifier = Modifier.testTag("new_folder_button")
                            ) {
                                Icon(Icons.Outlined.CreateNewFolder, contentDescription = "Create new folder")
                            }
                        }
                    }

                    if (browserState.isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            // Clipboard Paste Banner
            AnimatedVisibility(visible = browserState.clipboard != null) {
                val clip = browserState.clipboard
                if (clip != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "${clip.mode.label}: ${clip.items.size} item(s)",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = onPasteHere,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("paste_here_button")
                            ) {
                                Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Paste Here")
                            }
                            IconButton(onClick = onClearClipboard) {
                                Icon(Icons.Outlined.Close, contentDescription = "Cancel clipboard")
                            }
                        }
                    }
                }
            }

            // Main Content Area (Single pane on Compact/Medium, Split List-Detail on Expanded)
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Box(modifier = Modifier.weight(1.35f).fillMaxHeight()) {
                    if (!browserState.isLoading && entries.isEmpty()) {
                        MonolithEmptyState(
                            icon = Icons.Outlined.FolderOff,
                            title = "No Items Found",
                            subtitle = if (browserState.minSizeFilterMb > 0f || browserState.searchQuery.isNotBlank()) {
                                "No files match your active filter criteria."
                            } else {
                                "This directory is empty or needs storage access."
                            }
                        )
                    } else if (prefs.viewMode == ViewMode.LIST) {
                        LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(entries, key = { it.id }) { entry ->
                                FileListRow(
                                    entry = entry,
                                    isSelected = entry.id in browserState.selectedIds,
                                    isInspected = showSplitInspector && browserState.inspectorEntry?.id == entry.id,
                                    onClick = {
                                        when {
                                            browserState.selectedIds.isNotEmpty() -> onEntryLongClick(entry)
                                            showSplitInspector && !entry.isDirectory -> onInspectorSelect(entry)
                                            else -> onEntryClick(entry)
                                        }
                                    },
                                    onDoubleActionOpen = { onEntryClick(entry) },
                                    onLongClick = { onEntryLongClick(entry) },
                                    onRename = { onRenameRequest(entry) },
                                    onCopy = { onStageClipboard(listOf(entry), ClipboardMode.COPY) },
                                    onMove = { onStageClipboard(listOf(entry), ClipboardMode.MOVE) },
                                    onCompress = { onCompressRequest(listOf(entry)) },
                                    onVaultEncrypt = { onVaultEncryptRequest(listOf(entry)) },
                                    onShare = { onShareRequest(listOf(entry)) },
                                    onProperties = { onPropertiesRequest(entry) },
                                    onDelete = { onDeleteRequest(listOf(entry)) }
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(prefs.gridColumns.coerceIn(2, 6)),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(entries, key = { it.id }) { entry ->
                                FileGridCard(
                                    entry = entry,
                                    isSelected = entry.id in browserState.selectedIds,
                                    onClick = {
                                        when {
                                            browserState.selectedIds.isNotEmpty() -> onEntryLongClick(entry)
                                            showSplitInspector && !entry.isDirectory -> onInspectorSelect(entry)
                                            else -> onEntryClick(entry)
                                        }
                                    },
                                    onLongClick = { onEntryLongClick(entry) },
                                    onOpen = { onEntryClick(entry) },
                                    onRename = { onRenameRequest(entry) },
                                    onCopy = { onStageClipboard(listOf(entry), ClipboardMode.COPY) },
                                    onMove = { onStageClipboard(listOf(entry), ClipboardMode.MOVE) },
                                    onCompress = { onCompressRequest(listOf(entry)) },
                                    onVaultEncrypt = { onVaultEncryptRequest(listOf(entry)) },
                                    onShare = { onShareRequest(listOf(entry)) },
                                    onProperties = { onPropertiesRequest(entry) },
                                    onDelete = { onDeleteRequest(listOf(entry)) }
                                )
                            }
                        }
                    }
                }

                // Expanded Tablet/Desktop Right Inspector Pane
                if (showSplitInspector) {
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DesktopInspectorPane(
                        entry = browserState.inspectorEntry,
                        onOpenPreview = { onEntryClick(it) },
                        onRename = { onRenameRequest(it) },
                        onCopy = { onStageClipboard(listOf(it), ClipboardMode.COPY) },
                        onCompress = { onCompressRequest(listOf(it)) },
                        onVaultEncrypt = { onVaultEncryptRequest(listOf(it)) },
                        onShare = { onShareRequest(listOf(it)) },
                        onDelete = { onDeleteRequest(listOf(it)) },
                        modifier = Modifier
                            .weight(0.75f)
                            .fillMaxHeight()
                    )
                }
            }

            // Multi-Select Contextual Batch Action Bar
            AnimatedVisibility(visible = selectedEntries.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear selection")
                        }
                        Text(
                            text = "${selectedEntries.size}",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        IconButton(onClick = onSelectAll) {
                            Icon(Icons.Outlined.SelectAll, contentDescription = "Select all")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { onStageClipboard(selectedEntries, ClipboardMode.COPY) },
                            modifier = Modifier.testTag("batch_copy_button")
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy selected")
                        }
                        IconButton(
                            onClick = { onStageClipboard(selectedEntries, ClipboardMode.MOVE) },
                            modifier = Modifier.testTag("batch_move_button")
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = "Move selected")
                        }
                        IconButton(
                            onClick = { onCompressRequest(selectedEntries) },
                            modifier = Modifier.testTag("batch_compress_button")
                        ) {
                            Icon(Icons.Outlined.FolderZip, contentDescription = "Compress selected")
                        }
                        IconButton(
                            onClick = { onVaultEncryptRequest(selectedEntries) },
                            modifier = Modifier.testTag("batch_vault_button")
                        ) {
                            Icon(Icons.Outlined.Lock, contentDescription = "Encrypt to vault")
                        }
                        IconButton(
                            onClick = { onShareRequest(selectedEntries) },
                            modifier = Modifier.testTag("batch_share_button")
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share selected")
                        }
                        IconButton(
                            onClick = { onDeleteRequest(selectedEntries) },
                            modifier = Modifier.testTag("batch_delete_button")
                        ) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete selected")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListRow(
    entry: FileEntry,
    isSelected: Boolean,
    isInspected: Boolean,
    onClick: () -> Unit,
    onDoubleActionOpen: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onCompress: () -> Unit,
    onVaultEncrypt: () -> Unit,
    onShare: () -> Unit,
    onProperties: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            isInspected -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (isSelected || isInspected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("file_row_${entry.name}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Box {
                FileThumbnail(entry = entry, size = 42.dp)
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.TopEnd)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(9.dp))
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (entry.isDirectory) {
                        "${entry.childCount} items · ${Formatters.formatDate(entry.lastModifiedMs)}"
                    } else {
                        "${Formatters.formatBytes(entry.sizeBytes)} · ${Formatters.formatDate(entry.lastModifiedMs)}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("file_menu_${entry.name}")
                ) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "File actions")
                }
                FileItemActionDropdown(
                    entry = entry,
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    onOpen = onDoubleActionOpen,
                    onRename = onRename,
                    onCopy = onCopy,
                    onMove = onMove,
                    onCompress = onCompress,
                    onVaultEncrypt = onVaultEncrypt,
                    onShare = onShare,
                    onProperties = onProperties,
                    onDelete = onDelete
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridCard(
    entry: FileEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onCompress: () -> Unit,
    onVaultEncrypt: () -> Unit,
    onShare: () -> Unit,
    onProperties: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(10.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                FileThumbnail(
                    entry = entry,
                    size = 64.dp,
                    modifier = Modifier.align(Alignment.Center)
                )
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More actions", modifier = Modifier.size(18.dp))
                    }
                    FileItemActionDropdown(
                        entry = entry,
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        onOpen = onOpen,
                        onRename = onRename,
                        onCopy = onCopy,
                        onMove = onMove,
                        onCompress = onCompress,
                        onVaultEncrypt = onVaultEncrypt,
                        onShare = onShare,
                        onProperties = onProperties,
                        onDelete = onDelete
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = entry.name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (entry.isDirectory) "${entry.childCount} items" else Formatters.formatBytes(entry.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FileItemActionDropdown(
    entry: FileEntry,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onCompress: () -> Unit,
    onVaultEncrypt: () -> Unit,
    onShare: () -> Unit,
    onProperties: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        DropdownMenuItem(
            text = { Text(if (entry.isDirectory) "Open Folder" else "Preview / Open") },
            onClick = { onDismiss(); onOpen() }
        )
        DropdownMenuItem(
            text = { Text("Rename") },
            leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null) },
            onClick = { onDismiss(); onRename() }
        )
        DropdownMenuItem(
            text = { Text("Copy") },
            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
            onClick = { onDismiss(); onCopy() }
        )
        DropdownMenuItem(
            text = { Text("Move") },
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = null) },
            onClick = { onDismiss(); onMove() }
        )
        DropdownMenuItem(
            text = { Text("Compress (.zip)") },
            leadingIcon = { Icon(Icons.Outlined.FolderZip, contentDescription = null) },
            onClick = { onDismiss(); onCompress() }
        )
        if (!entry.isDirectory) {
            DropdownMenuItem(
                text = { Text("Encrypt to Vault") },
                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                onClick = { onDismiss(); onVaultEncrypt() }
            )
            DropdownMenuItem(
                text = { Text("Share") },
                leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                onClick = { onDismiss(); onShare() }
            )
        }
        DropdownMenuItem(
            text = { Text("Properties") },
            leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
            onClick = { onDismiss(); onProperties() }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        DropdownMenuItem(
            text = { Text("Delete") },
            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
            onClick = { onDismiss(); onDelete() }
        )
    }
}

@Composable
private fun DesktopInspectorPane(
    entry: FileEntry?,
    onOpenPreview: (FileEntry) -> Unit,
    onRename: (FileEntry) -> Unit,
    onCopy: (FileEntry) -> Unit,
    onCompress: (FileEntry) -> Unit,
    onVaultEncrypt: (FileEntry) -> Unit,
    onShare: (FileEntry) -> Unit,
    onDelete: (FileEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        if (entry == null) {
            MonolithEmptyState(
                icon = Icons.Outlined.Info,
                title = "Inspector",
                subtitle = "Select a file on the left to inspect metadata and quick actions."
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                FileThumbnail(
                    entry = entry,
                    size = 112.dp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                InspectorMeta("MIME TYPE", entry.mimeType)
                InspectorMeta("SIZE", Formatters.formatBytes(entry.sizeBytes))
                InspectorMeta("MODIFIED", Formatters.formatDate(entry.lastModifiedMs))
                InspectorMeta("SOURCE", entry.sourceType.name)
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = { onOpenPreview(entry) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Open Built-In Viewer") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { onRename(entry) }, modifier = Modifier.weight(1f)) { Text("Rename") }
                    OutlinedButton(onClick = { onCopy(entry) }, modifier = Modifier.weight(1f)) { Text("Copy") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { onCompress(entry) }, modifier = Modifier.weight(1f)) { Text("Zip") }
                    OutlinedButton(onClick = { onVaultEncrypt(entry) }, modifier = Modifier.weight(1f)) { Text("Vault") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { onShare(entry) }, modifier = Modifier.weight(1f)) { Text("Share") }
                    OutlinedButton(onClick = { onDelete(entry) }, modifier = Modifier.weight(1f)) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
private fun InspectorMeta(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
