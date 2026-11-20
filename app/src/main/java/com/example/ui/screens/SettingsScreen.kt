package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.local.SafMountEntity
import com.example.data.local.UserPreferences
import com.example.model.Formatters
import com.example.model.SortField
import com.example.model.ThemeMode
import com.example.model.ViewMode
import com.example.ui.components.MonolithDropdownSelector
import com.example.ui.components.MonolithLabeledSlider
import kotlin.math.roundToInt

private val TIMEOUT_STEPS = listOf(0, 30, 60, 300, 900)

@Composable
fun SettingsScreen(
    prefs: UserPreferences,
    hasAllFilesAccess: Boolean,
    safMounts: List<SafMountEntity>,
    onThemeModeChange: (ThemeMode) -> Unit,
    onViewModeChange: (ViewMode) -> Unit,
    onGridColumnsChange: (Int) -> Unit,
    onSortChange: (SortField, Boolean) -> Unit,
    onShowHiddenChange: (Boolean) -> Unit,
    onBiometricEnabledChange: (Boolean) -> Unit,
    onVaultTimeoutChange: (Int) -> Unit,
    onUpdateVaultPin: (String) -> Unit,
    onMountSafFolder: () -> Unit,
    onRemoveSafMount: (String) -> Unit,
    onOpenAllFilesSettings: () -> Unit,
    onOpenJobsSheet: () -> Unit,
    onClearRecentFiles: () -> Unit
) {
    var showChangePinDialog by remember { mutableStateOf(false) }

    val timeoutIndex = remember(prefs.vaultAutoLockSeconds) {
        TIMEOUT_STEPS.indexOfFirst { it >= prefs.vaultAutoLockSeconds }.takeIf { it >= 0 } ?: 2
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                Text(
                    text = "CONFIGURATION",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineLarge
                )
            }
        }

        // Appearance & Monochrome Theme
        item {
            SettingsSectionCard(title = "Appearance") {
                MonolithDropdownSelector(
                    label = "Monochrome Theme",
                    selectedText = prefs.themeMode.label,
                    options = ThemeMode.entries,
                    optionLabel = { it.label },
                    isSelected = { it == prefs.themeMode },
                    onSelect = onThemeModeChange,
                    leadingIcon = Icons.Outlined.DarkMode,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "settings_theme_dropdown"
                )
            }
        }

        // Browser Defaults (Dropdowns + Grid Slider)
        item {
            SettingsSectionCard(title = "Browser Defaults") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MonolithDropdownSelector(
                        label = "Default View",
                        selectedText = prefs.viewMode.label,
                        options = ViewMode.entries,
                        optionLabel = { it.label },
                        isSelected = { it == prefs.viewMode },
                        onSelect = onViewModeChange,
                        modifier = Modifier.weight(1f),
                        testTag = "settings_view_dropdown"
                    )
                    MonolithDropdownSelector(
                        label = "Default Sort",
                        selectedText = "${prefs.sortField.label} ${if (prefs.sortAscending) "↑" else "↓"}",
                        options = SortField.entries,
                        optionLabel = { it.label },
                        isSelected = { it == prefs.sortField },
                        onSelect = { field ->
                            val nextAsc = if (field == prefs.sortField) !prefs.sortAscending else true
                            onSortChange(field, nextAsc)
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "settings_sort_dropdown"
                    )
                }

                MonolithLabeledSlider(
                    label = "Default Grid Column Density",
                    valueText = "${prefs.gridColumns} cols",
                    value = prefs.gridColumns.toFloat(),
                    onValueChange = { onGridColumnsChange(it.roundToInt()) },
                    valueRange = 2f..6f,
                    steps = 3,
                    minLabel = "2 (Spacious)",
                    maxLabel = "6 (Dense)",
                    testTag = "settings_grid_slider"
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show Hidden Files", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Include dot-prefixed files and folders in listings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = prefs.showHiddenFiles,
                        onCheckedChange = onShowHiddenChange
                    )
                }
            }
        }

        // Hybrid Storage Access (SAF + Optional All-Files)
        item {
            SettingsSectionCard(title = "Hybrid Storage Access") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Optional All-Files Access", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (hasAllFilesAccess) "Granted for /storage/emulated/0" else "Off — using SAF & MediaStore only",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenAllFilesSettings,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("settings_all_files_button")
                    ) {
                        Icon(Icons.Outlined.Security, contentDescription = null)
                        Text(if (hasAllFilesAccess) " Manage" else " Enable")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "SAF Mounted Folders (${safMounts.size})",
                        style = MaterialTheme.typography.titleSmall
                    )
                    OutlinedButton(
                        onClick = onMountSafFolder,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Outlined.CreateNewFolder, contentDescription = null)
                        Text(" Mount")
                    }
                }

                safMounts.forEach { mount ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mount.displayName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                mount.treeUri,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        IconButton(onClick = { onRemoveSafMount(mount.treeUri) }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Unmount SAF folder")
                        }
                    }
                }
            }
        }

        // Privacy Vault Settings
        item {
            SettingsSectionCard(title = "Privacy Vault (AES-256-GCM)") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Biometric Unlock", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Allow fingerprint or face recognition alongside PIN",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = prefs.biometricEnabled,
                        onCheckedChange = onBiometricEnabledChange
                    )
                }

                MonolithLabeledSlider(
                    label = "Auto-Lock Timeout",
                    valueText = Formatters.formatTimeoutSeconds(prefs.vaultAutoLockSeconds),
                    value = timeoutIndex.toFloat(),
                    onValueChange = { idx ->
                        val step = TIMEOUT_STEPS[idx.roundToInt().coerceIn(0, TIMEOUT_STEPS.lastIndex)]
                        onVaultTimeoutChange(step)
                    },
                    valueRange = 0f..(TIMEOUT_STEPS.lastIndex).toFloat(),
                    steps = TIMEOUT_STEPS.size - 2,
                    minLabel = "Immediate",
                    maxLabel = "15m",
                    testTag = "vault_timeout_slider"
                )

                OutlinedButton(
                    onClick = { showChangePinDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (prefs.hasVaultPin) "Change Vault PIN" else "Set Vault PIN")
                }
            }
        }

        // Operations & Maintenance
        item {
            SettingsSectionCard(title = "Maintenance") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onOpenJobsSheet,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Sync, contentDescription = null)
                        Text(" Background Jobs")
                    }
                    OutlinedButton(
                        onClick = onClearRecentFiles,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear Recents")
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    if (showChangePinDialog) {
        var newPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showChangePinDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Update Vault PIN", style = MaterialTheme.typography.titleLarge) },
            text = {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { input -> newPin = input.filter { it.isDigit() }.take(6) },
                    label = { Text("New 4–6 digit PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateVaultPin(newPin)
                        showChangePinDialog = false
                    },
                    enabled = newPin.length >= 4
                ) { Text("Save PIN") }
            },
            dismissButton = {
                TextButton(onClick = { showChangePinDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
    }
}
