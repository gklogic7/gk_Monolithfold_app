package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.BackgroundJobInfo
import com.example.model.FileEntry
import com.example.model.Formatters
import com.example.model.SortField
import com.example.model.ViewMode
import com.example.viewmodel.ArchiveInspectionState
import kotlin.math.roundToInt

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("New Folder", style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Folder name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("new_folder_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onCreate(name.trim()) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("confirm_create_folder_button")
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RenameDialog(
    entry: FileEntry,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var name by remember(entry.id) { mutableStateOf(entry.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Rename", style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("New name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank() && name != entry.name) onRename(name.trim()) },
                enabled = name.isNotBlank() && name != entry.name,
                modifier = Modifier.testTag("confirm_rename_button")
            ) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DeleteConfirmDialog(
    entries: List<FileEntry>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val label = if (entries.size == 1) "\"${entries.first().name}\"" else "${entries.size} selected items"
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Delete permanently?", style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                text = "This will permanently remove $label from storage. This action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.testTag("confirm_delete_button")
            ) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PropertiesDialog(
    entry: FileEntry,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PropertyRow("Type", if (entry.isDirectory) "Directory" else entry.mimeType)
                PropertyRow("Size", if (entry.isDirectory) "${entry.childCount} items" else Formatters.formatBytes(entry.sizeBytes))
                PropertyRow("Modified", Formatters.formatDate(entry.lastModifiedMs))
                PropertyRow("Source", entry.sourceType.name)
                PropertyRow("Location", entry.pathOrUri)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Column {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun CompressArchiveDialog(
    entries: List<FileEntry>,
    onDismiss: () -> Unit,
    onCompress: (name: String, level: Int, password: String?) -> Unit
) {
    val defaultName = remember(entries) {
        if (entries.size == 1) "${entries.first().name.substringBeforeLast('.')}.zip" else "archive_${System.currentTimeMillis() / 1000}.zip"
    }
    var archiveName by remember { mutableStateOf(defaultName) }
    var compressionLevel by remember { mutableFloatStateOf(5f) }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Create ZIP Archive", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Packing ${entries.size} item(s) via WorkManager",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = archiveName,
                    onValueChange = { archiveName = it },
                    label = { Text("Archive filename") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                MonolithLabeledSlider(
                    label = "Compression Level",
                    valueText = when (compressionLevel.roundToInt()) {
                        0 -> "0 · Store"
                        in 1..3 -> "${compressionLevel.roundToInt()} · Fast"
                        in 4..6 -> "${compressionLevel.roundToInt()} · Normal"
                        in 7..8 -> "${compressionLevel.roundToInt()} · High"
                        else -> "9 · Ultra"
                    },
                    value = compressionLevel,
                    onValueChange = { compressionLevel = it },
                    valueRange = 0f..9f,
                    steps = 8,
                    minLabel = "Store (0)",
                    maxLabel = "Ultra (9)",
                    testTag = "compression_level_slider"
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Optional AES-256 password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCompress(archiveName.trim(), compressionLevel.roundToInt(), password.ifBlank { null }) },
                enabled = archiveName.isNotBlank(),
                modifier = Modifier.testTag("confirm_compress_button")
            ) { Text("Compress") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractArchiveSheet(
    state: ArchiveInspectionState,
    onDismiss: () -> Unit,
    onUnlockRetry: (String) -> Unit,
    onExtract: (String?) -> Unit
) {
    var password by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Outlined.FolderZip, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${state.contents.size} entries · ${Formatters.formatBytes(state.entry.sizeBytes)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.contents.take(100)) { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (item.isDirectory) Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (!item.isDirectory) {
                                Text(
                                    text = Formatters.formatBytes(item.sizeBytes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password (if encrypted)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.errorMessage != null) {
                    OutlinedButton(
                        onClick = { onUnlockRetry(password) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Unlock List") }
                }
                Button(
                    onClick = { onExtract(password.ifBlank { null }) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("extract_archive_button")
                ) { Text("Extract Here") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterAndLayoutSheet(
    viewMode: ViewMode,
    gridColumns: Int,
    sortField: SortField,
    sortAscending: Boolean,
    minSizeMb: Float,
    showHidden: Boolean,
    onViewModeChange: (ViewMode) -> Unit,
    onGridColumnsChange: (Int) -> Unit,
    onSortChange: (SortField, Boolean) -> Unit,
    onMinSizeMbChange: (Float) -> Unit,
    onShowHiddenChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("Layout, Sort & Filters", style = MaterialTheme.typography.headlineSmall)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MonolithDropdownSelector(
                    label = "View Mode",
                    selectedText = viewMode.label,
                    options = ViewMode.entries,
                    optionLabel = { it.label },
                    isSelected = { it == viewMode },
                    onSelect = onViewModeChange,
                    modifier = Modifier.weight(1f),
                    testTag = "sheet_view_mode_dropdown"
                )
                MonolithDropdownSelector(
                    label = "Sort By",
                    selectedText = "${sortField.label} ${if (sortAscending) "↑" else "↓"}",
                    options = SortField.entries,
                    optionLabel = { it.label },
                    isSelected = { it == sortField },
                    onSelect = { selected ->
                        val nextAsc = if (selected == sortField) !sortAscending else true
                        onSortChange(selected, nextAsc)
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "sheet_sort_dropdown"
                )
            }

            MonolithLabeledSlider(
                label = "Grid Column Density",
                valueText = "$gridColumns cols",
                value = gridColumns.toFloat(),
                onValueChange = { onGridColumnsChange(it.roundToInt()) },
                valueRange = 2f..6f,
                steps = 3,
                minLabel = "2 (Large)",
                maxLabel = "6 (Compact)",
                testTag = "grid_density_slider"
            )

            MonolithLabeledSlider(
                label = "Minimum File Size Filter",
                valueText = if (minSizeMb <= 0.5f) "All sizes" else "≥ ${minSizeMb.roundToInt()} MB",
                value = minSizeMb,
                onValueChange = onMinSizeMbChange,
                valueRange = 0f..200f,
                minLabel = "0 MB (All)",
                maxLabel = "200 MB+",
                testTag = "min_size_filter_slider"
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Show Hidden Files", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Display dot-prefixed config files & folders",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = showHidden,
                    onCheckedChange = onShowHiddenChange,
                    modifier = Modifier.testTag("show_hidden_switch")
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundJobsSheet(
    jobs: List<BackgroundJobInfo>,
    onCancelJob: (String) -> Unit,
    onPruneFinished: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Background Jobs", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onPruneFinished) { Text("Clear Finished") }
            }

            if (jobs.isEmpty()) {
                Text(
                    text = "No recent copy, move, or archive jobs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(jobs, key = { it.id }) { job ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(job.title, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "${job.status} · ${job.detail}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (job.isRunning) {
                                        IconButton(onClick = { onCancelJob(job.id) }) {
                                            Icon(Icons.Outlined.Close, contentDescription = "Cancel job")
                                        }
                                    } else {
                                        Text(
                                            text = "${job.progressPercent}%",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                                if (job.isRunning) {
                                    LinearProgressIndicator(
                                        progress = { (job.progressPercent / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
