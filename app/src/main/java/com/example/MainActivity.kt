package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.AppDestination
import com.example.model.FileEntry
import com.example.model.PreviewTarget
import com.example.ui.components.BackgroundJobsSheet
import com.example.ui.components.CompressArchiveDialog
import com.example.ui.components.CreateFolderDialog
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.ExtractArchiveSheet
import com.example.ui.components.FilterAndLayoutSheet
import com.example.ui.components.PropertiesDialog
import com.example.ui.components.RenameDialog
import com.example.ui.screens.BrowserScreen
import com.example.ui.screens.OverviewScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.theme.MonolithTheme
import com.example.ui.viewers.ImageZoomViewerOverlay
import com.example.ui.viewers.InlineAudioPlayerBar
import com.example.ui.viewers.Media3VideoPlayerOverlay
import com.example.ui.viewers.PdfPreviewViewerOverlay
import com.example.ui.viewers.TextDocumentViewerOverlay
import com.example.viewmodel.MonolithViewModel

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MonolithViewModel = viewModel()
            MonolithApp(
                activity = this,
                viewModel = viewModel
            )
        }
    }
}

@Composable
fun MonolithApp(
    activity: FragmentActivity,
    viewModel: MonolithViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val destination by viewModel.currentDestination.collectAsStateWithLifecycle()
    val volumes by viewModel.volumes.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val safMounts by viewModel.safMounts.collectAsStateWithLifecycle()
    val recentFiles by viewModel.recentFiles.collectAsStateWithLifecycle()
    val hasAllFilesAccess by viewModel.hasAllFilesAccess.collectAsStateWithLifecycle()
    val browserState by viewModel.browserState.collectAsStateWithLifecycle()
    val displayedEntries by viewModel.displayedEntries.collectAsStateWithLifecycle()
    val vaultUiState by viewModel.vaultUiState.collectAsStateWithLifecycle()
    val vaultItems by viewModel.vaultItems.collectAsStateWithLifecycle()
    val backgroundJobs by viewModel.backgroundJobs.collectAsStateWithLifecycle()
    val previewTarget by viewModel.previewTarget.collectAsStateWithLifecycle()
    val audioPlayerEntry by viewModel.audioPlayerEntry.collectAsStateWithLifecycle()
    val archiveInspection by viewModel.archiveInspection.collectAsStateWithLifecycle()

    // Modal Dialog & Sheet State
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileEntry?>(null) }
    var deleteTargets by remember { mutableStateOf<List<FileEntry>?>(null) }
    var compressTargets by remember { mutableStateOf<List<FileEntry>?>(null) }
    var propertiesTarget by remember { mutableStateOf<FileEntry?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showJobsSheet by remember { mutableStateOf(false) }

    // Refresh permissions & check vault auto-lock whenever activity resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAllFilesStatus()
                viewModel.checkVaultAutoLock()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // System Launchers: SAF Directory Mount, Zero-Permission Photo Picker, SAF Vault Document Picker, Notifications
    val safTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.onSafTreeSelected(uri)
    }

    val workspacePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importMediaToWorkspace(uris)
    }

    val vaultPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.encryptPickedUrisToVault(uris)
    }

    val vaultDocPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.encryptPickedUrisToVault(uris)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    fun ensureNotificationPermissionForJobs() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun openAllFilesAccessSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = runCatching {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }.getOrElse {
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            }
            runCatching { context.startActivity(intent) }
                .onFailure {
                    runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                        .onFailure { viewModel.emitMessage("All-Files settings unavailable on this device") }
                }
        } else {
            viewModel.emitMessage("Use Mount Folder (SAF) on Android 10 and below")
        }
    }

    fun shareEntries(entries: List<FileEntry>) {
        val files = entries.filter { !it.isDirectory }
        if (files.isEmpty()) {
            viewModel.emitMessage("Select files to share")
            return
        }
        runCatching {
            val uris = ArrayList(files.map { viewModel.storageRepository.getShareUri(it) })
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = files.first().mimeType
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                }
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, "Share via"))
        }.onFailure { err ->
            viewModel.emitMessage(err.message ?: "Unable to share selected file(s)")
        }
    }

    fun launchBiometricPrompt() {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        if (biometricManager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            viewModel.emitMessage("Biometrics not enrolled — use your Vault PIN")
            return
        }
        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.onBiometricUnlockSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        viewModel.emitMessage(errString.toString())
                    }
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Privacy Vault")
            .setSubtitle("Verify identity to access AES-256 encrypted files")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(promptInfo)
    }

    // Back navigation across top-level tabs returns to Overview
    BackHandler(enabled = previewTarget == null && destination != AppDestination.OVERVIEW) {
        viewModel.navigateTo(AppDestination.OVERVIEW)
    }

    val runningJobsCount = remember(backgroundJobs) { backgroundJobs.count { it.isRunning } }
    val isCurrentDirBookmarked = remember(bookmarks, browserState.currentPathOrUri) {
        bookmarks.any { it.pathOrUri == browserState.currentPathOrUri }
    }

    MonolithTheme(themeMode = prefs.themeMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavigationRail = maxWidth >= 600.dp

                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    bottomBar = {
                        if (!useNavigationRail) {
                            Column {
                                audioPlayerEntry?.let { (entry, uriStr) ->
                                    InlineAudioPlayerBar(
                                        entry = entry,
                                        uriString = uriStr,
                                        onDismiss = viewModel::dismissAudioPlayer
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 0.dp
                                ) {
                                    AppDestination.entries.forEach { dest ->
                                        val selected = destination == dest
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { viewModel.navigateTo(dest) },
                                            icon = {
                                                Icon(
                                                    imageVector = destinationIcon(dest, selected),
                                                    contentDescription = dest.label
                                                )
                                            },
                                            label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                                indicatorColor = MaterialTheme.colorScheme.primary,
                                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            modifier = Modifier.testTag("nav_${dest.name.lowercase()}")
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useNavigationRail) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .statusBarsPadding()
                            ) {
                                AppDestination.entries.forEach { dest ->
                                    val selected = destination == dest
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { viewModel.navigateTo(dest) },
                                        icon = {
                                            Icon(
                                                imageVector = destinationIcon(dest, selected),
                                                contentDescription = dest.label
                                            )
                                        },
                                        label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) },
                                        colors = NavigationRailItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                            indicatorColor = MaterialTheme.colorScheme.primary,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.testTag("rail_${dest.name.lowercase()}")
                                    )
                                }
                            }
                            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }

                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            Box(modifier = Modifier.weight(1f)) {
                                when (destination) {
                                    AppDestination.OVERVIEW -> OverviewScreen(
                                        volumes = volumes,
                                        categories = categories,
                                        bookmarks = bookmarks,
                                        recentFiles = recentFiles,
                                        hasAllFilesAccess = hasAllFilesAccess,
                                        runningJobsCount = runningJobsCount,
                                        onVolumeClick = viewModel::openVolume,
                                        onCategoryClick = viewModel::openCategory,
                                        onBookmarkClick = viewModel::openBookmark,
                                        onRecentClick = viewModel::openRecent,
                                        onMountSafFolder = { safTreeLauncher.launch(null) },
                                        onImportPhotoPicker = {
                                            workspacePhotoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                            )
                                        },
                                        onOpenAllFilesSettings = ::openAllFilesAccessSettings,
                                        onOpenJobsSheet = { showJobsSheet = true },
                                        onRefresh = viewModel::refreshOverview
                                    )

                                    AppDestination.BROWSE -> BrowserScreen(
                                        browserState = browserState,
                                        entries = displayedEntries,
                                        volumes = volumes,
                                        prefs = prefs,
                                        isBookmarked = isCurrentDirBookmarked,
                                        onNavigateUp = viewModel::navigateUpDirectory,
                                        onVolumeSelect = viewModel::openVolume,
                                        onBreadcrumbClick = viewModel::navigateToBreadcrumb,
                                        onSearchChange = viewModel::updateSearchQuery,
                                        onViewModeChange = viewModel::setViewMode,
                                        onGridColumnsChange = viewModel::setGridColumns,
                                        onSortChange = viewModel::setSort,
                                        onOpenFilterSheet = { showFilterSheet = true },
                                        onToggleBookmark = viewModel::toggleBookmarkCurrentDirectory,
                                        onCreateFolderClick = { showCreateFolderDialog = true },
                                        onEntryClick = viewModel::openFileEntry,
                                        onEntryLongClick = viewModel::toggleSelection,
                                        onInspectorSelect = viewModel::selectForInspector,
                                        onSelectAll = viewModel::selectAllVisible,
                                        onClearSelection = viewModel::clearSelection,
                                        onStageClipboard = viewModel::stageClipboard,
                                        onPasteHere = {
                                            ensureNotificationPermissionForJobs()
                                            viewModel.pasteClipboardHere()
                                        },
                                        onClearClipboard = viewModel::clearClipboard,
                                        onRenameRequest = { renameTarget = it },
                                        onDeleteRequest = { deleteTargets = it },
                                        onCompressRequest = { compressTargets = it },
                                        onVaultEncryptRequest = { viewModel.encryptEntriesToVault(it, deleteOriginal = false) },
                                        onShareRequest = ::shareEntries,
                                        onPropertiesRequest = { propertiesTarget = it }
                                    )

                                    AppDestination.VAULT -> VaultScreen(
                                        vaultUiState = vaultUiState,
                                        hasConfiguredPin = prefs.hasVaultPin,
                                        biometricEnabled = prefs.biometricEnabled,
                                        autoLockSeconds = prefs.vaultAutoLockSeconds,
                                        vaultItems = vaultItems,
                                        onSetupPin = viewModel::setupVaultPin,
                                        onUnlockWithPin = viewModel::unlockVaultWithPin,
                                        onTriggerBiometric = ::launchBiometricPrompt,
                                        onLockNow = viewModel::lockVault,
                                        onImportPhotosToVault = {
                                            vaultPhotoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                            )
                                        },
                                        onImportDocumentToVault = {
                                            vaultDocPickerLauncher.launch(arrayOf("*/*"))
                                        },
                                        onPreviewVaultItem = viewModel::previewVaultItem,
                                        onExportVaultItem = viewModel::exportVaultItem,
                                        onDeleteVaultItem = viewModel::deleteVaultItem
                                    )

                                    AppDestination.SETTINGS -> SettingsScreen(
                                        prefs = prefs,
                                        hasAllFilesAccess = hasAllFilesAccess,
                                        safMounts = safMounts,
                                        onThemeModeChange = viewModel::setThemeMode,
                                        onViewModeChange = viewModel::setViewMode,
                                        onGridColumnsChange = viewModel::setGridColumns,
                                        onSortChange = viewModel::setSort,
                                        onShowHiddenChange = viewModel::setShowHiddenFiles,
                                        onBiometricEnabledChange = viewModel::setBiometricEnabled,
                                        onVaultTimeoutChange = viewModel::setVaultAutoLockSeconds,
                                        onUpdateVaultPin = viewModel::setupVaultPin,
                                        onMountSafFolder = { safTreeLauncher.launch(null) },
                                        onRemoveSafMount = viewModel::removeSafMount,
                                        onOpenAllFilesSettings = ::openAllFilesAccessSettings,
                                        onOpenJobsSheet = { showJobsSheet = true },
                                        onClearRecentFiles = viewModel::clearRecentFiles
                                    )
                                }
                            }

                            if (useNavigationRail) {
                                audioPlayerEntry?.let { (entry, uriStr) ->
                                    InlineAudioPlayerBar(
                                        entry = entry,
                                        uriString = uriStr,
                                        onDismiss = viewModel::dismissAudioPlayer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Dialogs & Bottom Sheets
            if (showCreateFolderDialog) {
                CreateFolderDialog(
                    onDismiss = { showCreateFolderDialog = false },
                    onCreate = { name ->
                        showCreateFolderDialog = false
                        viewModel.createFolder(name)
                    }
                )
            }

            renameTarget?.let { entry ->
                RenameDialog(
                    entry = entry,
                    onDismiss = { renameTarget = null },
                    onRename = { newName ->
                        renameTarget = null
                        viewModel.renameEntry(entry, newName)
                    }
                )
            }

            deleteTargets?.let { entries ->
                DeleteConfirmDialog(
                    entries = entries,
                    onDismiss = { deleteTargets = null },
                    onConfirm = {
                        deleteTargets = null
                        viewModel.deleteEntries(entries)
                    }
                )
            }

            propertiesTarget?.let { entry ->
                PropertiesDialog(
                    entry = entry,
                    onDismiss = { propertiesTarget = null }
                )
            }

            compressTargets?.let { entries ->
                CompressArchiveDialog(
                    entries = entries,
                    onDismiss = { compressTargets = null },
                    onCompress = { name, level, password ->
                        compressTargets = null
                        ensureNotificationPermissionForJobs()
                        viewModel.compressEntries(entries, name, level, password)
                    }
                )
            }

            archiveInspection?.let { state ->
                ExtractArchiveSheet(
                    state = state,
                    onDismiss = viewModel::dismissArchiveInspection,
                    onUnlockRetry = { pwd -> viewModel.inspectArchive(state.entry, pwd) },
                    onExtract = { pwd ->
                        ensureNotificationPermissionForJobs()
                        viewModel.extractInspectedArchive(pwd)
                    }
                )
            }

            if (showFilterSheet) {
                FilterAndLayoutSheet(
                    viewMode = prefs.viewMode,
                    gridColumns = prefs.gridColumns,
                    sortField = prefs.sortField,
                    sortAscending = prefs.sortAscending,
                    minSizeMb = browserState.minSizeFilterMb,
                    showHidden = prefs.showHiddenFiles,
                    onViewModeChange = viewModel::setViewMode,
                    onGridColumnsChange = viewModel::setGridColumns,
                    onSortChange = viewModel::setSort,
                    onMinSizeMbChange = viewModel::updateMinSizeFilterMb,
                    onShowHiddenChange = viewModel::setShowHiddenFiles,
                    onDismiss = { showFilterSheet = false }
                )
            }

            if (showJobsSheet) {
                BackgroundJobsSheet(
                    jobs = backgroundJobs,
                    onCancelJob = viewModel::cancelJob,
                    onPruneFinished = viewModel::pruneJobs,
                    onDismiss = { showJobsSheet = false }
                )
            }

            // Fullscreen Built-In Viewers Overlay
            when (val target = previewTarget) {
                is PreviewTarget.Video -> Media3VideoPlayerOverlay(
                    entry = target.entry,
                    uriString = target.uriString,
                    onClose = viewModel::closePreview
                )
                is PreviewTarget.Image -> ImageZoomViewerOverlay(
                    entry = target.entry,
                    uriString = target.uriString,
                    onClose = viewModel::closePreview
                )
                is PreviewTarget.Pdf -> PdfPreviewViewerOverlay(
                    entry = target.entry,
                    filePath = target.filePath,
                    onClose = viewModel::closePreview
                )
                is PreviewTarget.Text -> TextDocumentViewerOverlay(
                    entry = target.entry,
                    content = target.content,
                    truncated = target.truncated,
                    onClose = viewModel::closePreview
                )
                null -> Unit
            }
        }
    }
}

private fun destinationIcon(destination: AppDestination, selected: Boolean) = when (destination) {
    AppDestination.OVERVIEW -> if (selected) Icons.Filled.Dashboard else Icons.Outlined.Dashboard
    AppDestination.BROWSE -> if (selected) Icons.Filled.Folder else Icons.Outlined.Folder
    AppDestination.VAULT -> if (selected) Icons.Filled.Lock else Icons.Outlined.Lock
    AppDestination.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
}
