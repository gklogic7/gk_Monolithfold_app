package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EnhancedEncryption
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import com.example.data.local.VaultItemEntity
import com.example.model.Formatters
import com.example.ui.components.MonolithEmptyState
import com.example.viewmodel.VaultUiState

@Composable
fun VaultScreen(
    vaultUiState: VaultUiState,
    hasConfiguredPin: Boolean,
    biometricEnabled: Boolean,
    autoLockSeconds: Int,
    vaultItems: List<VaultItemEntity>,
    onSetupPin: (String) -> Unit,
    onUnlockWithPin: (String) -> Unit,
    onTriggerBiometric: () -> Unit,
    onLockNow: () -> Unit,
    onImportPhotosToVault: () -> Unit,
    onImportDocumentToVault: () -> Unit,
    onPreviewVaultItem: (VaultItemEntity) -> Unit,
    onExportVaultItem: (VaultItemEntity, Boolean) -> Unit,
    onDeleteVaultItem: (VaultItemEntity) -> Unit
) {
    if (!vaultUiState.isUnlocked) {
        VaultPinLockGate(
            isSetupMode = !hasConfiguredPin,
            biometricAvailable = hasConfiguredPin && biometricEnabled,
            pinError = vaultUiState.pinError,
            onSubmitPin = { pin ->
                if (hasConfiguredPin) onUnlockWithPin(pin) else onSetupPin(pin)
            },
            onTriggerBiometric = onTriggerBiometric
        )
    } else {
        UnlockedVaultContent(
            vaultItems = vaultItems,
            isBusy = vaultUiState.isBusy,
            autoLockSeconds = autoLockSeconds,
            onLockNow = onLockNow,
            onImportPhotosToVault = onImportPhotosToVault,
            onImportDocumentToVault = onImportDocumentToVault,
            onPreviewVaultItem = onPreviewVaultItem,
            onExportVaultItem = onExportVaultItem,
            onDeleteVaultItem = onDeleteVaultItem
        )
    }
}

@Composable
private fun VaultPinLockGate(
    isSetupMode: Boolean,
    biometricAvailable: Boolean,
    pinError: String?,
    onSubmitPin: (String) -> Unit,
    onTriggerBiometric: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var firstSetupPin by remember { mutableStateOf<String?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }

    val displayError = localError ?: pinError
    val subtitle = when {
        isSetupMode && firstSetupPin == null -> "Create a 4–6 digit PIN to initialize your AES-256-GCM Vault"
        isSetupMode && firstSetupPin != null -> "Confirm your Vault PIN"
        else -> "Enter PIN or use biometrics to decrypt Vault container"
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.widthIn(max = 360.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.EnhancedEncryption, contentDescription = "Vault Lock", modifier = Modifier.size(28.dp))
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isSetupMode) "Initialize Privacy Vault" else "Privacy Vault Locked",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Monochrome PIN Dots
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(6) { index ->
                    val filled = index < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
            }

            if (displayError != null) {
                Text(
                    text = displayError,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Minimal Monochrome Numeric Keypad
            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9")
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { digit ->
                            KeypadButton(
                                label = digit,
                                onClick = {
                                    localError = null
                                    if (enteredPin.length < 6) enteredPin += digit
                                },
                                testTag = "vault_pin_$digit"
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (biometricAvailable) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            onClick = onTriggerBiometric,
                            modifier = Modifier
                                .size(68.dp)
                                .testTag("vault_biometric_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Fingerprint, contentDescription = "Unlock with biometrics")
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.size(68.dp))
                    }

                    KeypadButton(
                        label = "0",
                        onClick = {
                            localError = null
                            if (enteredPin.length < 6) enteredPin += "0"
                        },
                        testTag = "vault_pin_0"
                    )

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        onClick = {
                            if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                        },
                        modifier = Modifier
                            .size(68.dp)
                            .testTag("vault_pin_backspace")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = "Delete digit")
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (enteredPin.length < 4) {
                        localError = "Enter at least 4 digits"
                        return@Button
                    }
                    if (isSetupMode) {
                        if (firstSetupPin == null) {
                            firstSetupPin = enteredPin
                            enteredPin = ""
                        } else if (firstSetupPin == enteredPin) {
                            onSubmitPin(enteredPin)
                            enteredPin = ""
                            firstSetupPin = null
                        } else {
                            localError = "PINs did not match — try again"
                            firstSetupPin = null
                            enteredPin = ""
                        }
                    } else {
                        onSubmitPin(enteredPin)
                        enteredPin = ""
                    }
                },
                enabled = enteredPin.length >= 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vault_pin_submit_button")
            ) {
                Text(
                    when {
                        isSetupMode && firstSetupPin == null -> "Continue"
                        isSetupMode -> "Confirm & Unlock"
                        else -> "Unlock Vault"
                    }
                )
            }
        }
    }
}

@Composable
private fun KeypadButton(
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = onClick,
        modifier = Modifier
            .size(68.dp)
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = label, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun UnlockedVaultContent(
    vaultItems: List<VaultItemEntity>,
    isBusy: Boolean,
    autoLockSeconds: Int,
    onLockNow: () -> Unit,
    onImportPhotosToVault: () -> Unit,
    onImportDocumentToVault: () -> Unit,
    onPreviewVaultItem: (VaultItemEntity) -> Unit,
    onExportVaultItem: (VaultItemEntity, Boolean) -> Unit,
    onDeleteVaultItem: (VaultItemEntity) -> Unit
) {
    var importMenuExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AES-256-GCM · AUTO-LOCK ${Formatters.formatTimeoutSeconds(autoLockSeconds).uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Privacy Vault (${vaultItems.size})",
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }

                    Box {
                        OutlinedButton(
                            onClick = { importMenuExpanded = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("vault_import_dropdown")
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(" Encrypt", style = MaterialTheme.typography.labelLarge)
                        }
                        DropdownMenu(
                            expanded = importMenuExpanded,
                            onDismissRequest = { importMenuExpanded = false },
                            containerColor = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Import Photos / Videos") },
                                leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null) },
                                onClick = {
                                    importMenuExpanded = false
                                    onImportPhotosToVault()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Import Any File (SAF)") },
                                leadingIcon = { Icon(Icons.Outlined.UploadFile, contentDescription = null) },
                                onClick = {
                                    importMenuExpanded = false
                                    onImportDocumentToVault()
                                }
                            )
                        }
                    }

                    IconButton(onClick = onLockNow, modifier = Modifier.testTag("vault_lock_now_button")) {
                        Icon(Icons.Outlined.Lock, contentDescription = "Lock vault now")
                    }
                }
                if (isBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        if (vaultItems.isEmpty()) {
            MonolithEmptyState(
                icon = Icons.Outlined.LockOpen,
                title = "Vault Is Empty",
                subtitle = "Encrypt files from the Browser or tap Encrypt above to store them with AES-256-GCM."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(vaultItems, key = { it.id }) { item ->
                    VaultItemRow(
                        item = item,
                        onPreview = { onPreviewVaultItem(item) },
                        onExportKeep = { onExportVaultItem(item, false) },
                        onExportRemove = { onExportVaultItem(item, true) },
                        onDelete = { onDeleteVaultItem(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultItemRow(
    item: VaultItemEntity,
    onPreview: () -> Unit,
    onExportKeep: () -> Unit,
    onExportRemove: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onPreview)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.EnhancedEncryption, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.originalName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${Formatters.formatBytes(item.originalSizeBytes)} · ${Formatters.formatDate(item.addedAtMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Vault item actions")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    DropdownMenuItem(
                        text = { Text("Preview (Ephemeral)") },
                        leadingIcon = { Icon(Icons.Outlined.Visibility, contentDescription = null) },
                        onClick = { menuExpanded = false; onPreview() }
                    )
                    DropdownMenuItem(
                        text = { Text("Restore & Remove from Vault") },
                        leadingIcon = { Icon(Icons.Outlined.Restore, contentDescription = null) },
                        onClick = { menuExpanded = false; onExportRemove() }
                    )
                    DropdownMenuItem(
                        text = { Text("Export Decrypted Copy") },
                        leadingIcon = { Icon(Icons.Outlined.Restore, contentDescription = null) },
                        onClick = { menuExpanded = false; onExportKeep() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Permanently") },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}
