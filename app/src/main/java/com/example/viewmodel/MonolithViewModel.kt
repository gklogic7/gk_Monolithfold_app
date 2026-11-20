package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.archive.ArchiveEngine
import com.example.data.archive.FileJobScheduler
import com.example.data.local.BookmarkEntity
import com.example.data.local.MonolithDatabase
import com.example.data.local.PreferencesRepository
import com.example.data.local.RecentFileEntity
import com.example.data.local.SafMountEntity
import com.example.data.local.UserPreferences
import com.example.data.local.VaultItemEntity
import com.example.data.storage.StorageRepository
import com.example.data.vault.VaultRepository
import com.example.data.vault.toVirtualFileEntry
import com.example.model.AppDestination
import com.example.model.ArchiveEntryInfo
import com.example.model.BackgroundJobInfo
import com.example.model.BreadcrumbItem
import com.example.model.CategorySummary
import com.example.model.ClipboardMode
import com.example.model.ClipboardState
import com.example.model.FileCategory
import com.example.model.FileEntry
import com.example.model.FileSourceType
import com.example.model.PreviewTarget
import com.example.model.SortField
import com.example.model.StorageVolumeInfo
import com.example.model.StorageVolumeKind
import com.example.model.ThemeMode
import com.example.model.ViewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class BrowserState(
    val currentPathOrUri: String = "",
    val currentSourceType: FileSourceType = FileSourceType.FILE,
    val activeCategory: FileCategory? = null,
    val activeVolumeId: String = "workspace",
    val breadcrumbs: List<BreadcrumbItem> = emptyList(),
    val rawEntries: List<FileEntry> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val minSizeFilterMb: Float = 0f,
    val selectedIds: Set<String> = emptySet(),
    val inspectorEntry: FileEntry? = null,
    val clipboard: ClipboardState? = null
)

data class ArchiveInspectionState(
    val entry: FileEntry,
    val localFile: File,
    val contents: List<ArchiveEntryInfo> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

data class VaultUiState(
    val isUnlocked: Boolean = false,
    val lastActivityMs: Long = 0L,
    val pinError: String? = null,
    val isBusy: Boolean = false
)

class MonolithViewModel(application: Application) : AndroidViewModel(application) {

    private val db = MonolithDatabase.getInstance(application)
    val preferencesRepository = PreferencesRepository(application)
    val storageRepository = StorageRepository(application, db.safMountDao())
    val vaultRepository = VaultRepository(application, db.vaultItemDao(), storageRepository)
    private val jobScheduler = FileJobScheduler(application)

    private val _currentDestination = MutableStateFlow(AppDestination.OVERVIEW)
    val currentDestination: StateFlow<AppDestination> = _currentDestination.asStateFlow()

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val bookmarks: StateFlow<List<BookmarkEntity>> = db.bookmarkDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val safMounts: StateFlow<List<SafMountEntity>> = db.safMountDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentFiles: StateFlow<List<RecentFileEntity>> = db.recentFileDao().observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vaultItems: StateFlow<List<VaultItemEntity>> = vaultRepository.vaultItemsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val backgroundJobs: StateFlow<List<BackgroundJobInfo>> = jobScheduler.observeJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _volumes = MutableStateFlow<List<StorageVolumeInfo>>(emptyList())
    val volumes: StateFlow<List<StorageVolumeInfo>> = _volumes.asStateFlow()

    private val _categories = MutableStateFlow<List<CategorySummary>>(emptyList())
    val categories: StateFlow<List<CategorySummary>> = _categories.asStateFlow()

    private val _hasAllFilesAccess = MutableStateFlow(storageRepository.hasAllFilesAccess())
    val hasAllFilesAccess: StateFlow<Boolean> = _hasAllFilesAccess.asStateFlow()

    private val _browserState = MutableStateFlow(BrowserState())
    val browserState: StateFlow<BrowserState> = _browserState.asStateFlow()

    val displayedEntries: StateFlow<List<FileEntry>> = combine(
        _browserState,
        preferences
    ) { state, prefs ->
        val query = state.searchQuery.trim()
        val minBytes = (state.minSizeFilterMb * 1024f * 1024f).toLong()
        state.rawEntries
            .filter { entry ->
                val matchesQuery = query.isEmpty() || entry.name.contains(query, ignoreCase = true)
                val matchesSize = entry.isDirectory || minBytes <= 0L || entry.sizeBytes >= minBytes
                matchesQuery && matchesSize
            }
            .sortedWith(buildComparator(prefs.sortField, prefs.sortAscending))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _vaultUiState = MutableStateFlow(VaultUiState())
    val vaultUiState: StateFlow<VaultUiState> = _vaultUiState.asStateFlow()

    private val _previewTarget = MutableStateFlow<PreviewTarget?>(null)
    val previewTarget: StateFlow<PreviewTarget?> = _previewTarget.asStateFlow()

    private val _audioPlayerEntry = MutableStateFlow<Pair<FileEntry, String>?>(null)
    val audioPlayerEntry: StateFlow<Pair<FileEntry, String>?> = _audioPlayerEntry.asStateFlow()

    private val _archiveInspection = MutableStateFlow<ArchiveInspectionState?>(null)
    val archiveInspection: StateFlow<ArchiveInspectionState?> = _archiveInspection.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessages = _snackbarMessages.asSharedFlow()

    init {
        viewModelScope.launch {
            storageRepository.ensureWorkspaceSeeded()
            refreshOverview()
            openDefaultBrowserRoot()
        }
        viewModelScope.launch {
            // Auto-refresh active directory whenever a WorkManager job completes
            var lastRunningCount = 0
            backgroundJobs.collect { jobs ->
                val runningCount = jobs.count { it.isRunning }
                if (lastRunningCount > 0 && runningCount < lastRunningCount) {
                    refreshCurrentDirectory()
                    refreshOverview()
                }
                lastRunningCount = runningCount
            }
        }
    }

    fun navigateTo(destination: AppDestination) {
        if (destination == AppDestination.VAULT) {
            checkVaultAutoLock()
        }
        _currentDestination.value = destination
    }

    fun refreshAllFilesStatus() {
        val granted = storageRepository.hasAllFilesAccess()
        _hasAllFilesAccess.value = granted
        refreshOverview()
        refreshCurrentDirectory()
    }

    fun refreshOverview() {
        viewModelScope.launch {
            _hasAllFilesAccess.value = storageRepository.hasAllFilesAccess()
            _volumes.value = storageRepository.getStorageVolumes()
            _categories.value = storageRepository.getCategorySummaries()
        }
    }

    private fun openDefaultBrowserRoot() {
        val rootPath = if (storageRepository.hasAllFilesAccess()) {
            android.os.Environment.getExternalStorageDirectory().absolutePath
        } else {
            storageRepository.workspaceDir.absolutePath
        }
        val volumeId = if (storageRepository.hasAllFilesAccess()) "internal" else "workspace"
        val label = if (storageRepository.hasAllFilesAccess()) "Internal" else "Workspace"
        openDirectory(
            pathOrUri = rootPath,
            sourceType = FileSourceType.FILE,
            volumeId = volumeId,
            breadcrumbs = listOf(BreadcrumbItem(label, rootPath, FileSourceType.FILE))
        )
    }

    fun openVolume(volume: StorageVolumeInfo) {
        if (!volume.isAvailable || volume.rootPathOrUri.isBlank()) {
            emitMessage("${volume.label} is not mounted — use Mount Folder (SAF) to connect")
            return
        }
        if (volume.kind == StorageVolumeKind.INTERNAL && !storageRepository.hasAllFilesAccess()) {
            emitMessage("Internal Storage needs All-Files Access or SAF folder mount — showing accessible entries")
        }
        openDirectory(
            pathOrUri = volume.rootPathOrUri,
            sourceType = volume.sourceType,
            volumeId = volume.id,
            breadcrumbs = listOf(BreadcrumbItem(volume.label, volume.rootPathOrUri, volume.sourceType))
        )
        _currentDestination.value = AppDestination.BROWSE
    }

    fun openCategory(category: FileCategory) {
        _currentDestination.value = AppDestination.BROWSE
        _browserState.update {
            it.copy(
                activeCategory = category,
                breadcrumbs = listOf(BreadcrumbItem(category.label, "category://${category.name}", FileSourceType.MEDIASTORE)),
                selectedIds = emptySet(),
                inspectorEntry = null,
                isLoading = true
            )
        }
        viewModelScope.launch {
            val items = storageRepository.listCategoryFiles(category)
            _browserState.update {
                it.copy(rawEntries = items, isLoading = false)
            }
        }
    }

    fun openBookmark(bookmark: BookmarkEntity) {
        val source = FileSourceType.entries.firstOrNull { it.name == bookmark.sourceType } ?: FileSourceType.FILE
        openDirectory(
            pathOrUri = bookmark.pathOrUri,
            sourceType = source,
            volumeId = if (source == FileSourceType.SAF) "saf_${bookmark.pathOrUri.hashCode()}" else "bookmark",
            breadcrumbs = listOf(BreadcrumbItem(bookmark.label, bookmark.pathOrUri, source))
        )
        _currentDestination.value = AppDestination.BROWSE
    }

    fun openDirectory(
        pathOrUri: String,
        sourceType: FileSourceType,
        volumeId: String = _browserState.value.activeVolumeId,
        breadcrumbs: List<BreadcrumbItem>
    ) {
        _browserState.update {
            it.copy(
                currentPathOrUri = pathOrUri,
                currentSourceType = sourceType,
                activeCategory = null,
                activeVolumeId = volumeId,
                breadcrumbs = breadcrumbs,
                selectedIds = emptySet(),
                inspectorEntry = null,
                isLoading = true
            )
        }
        viewModelScope.launch {
            val entries = storageRepository.listDirectory(
                pathOrUri = pathOrUri,
                sourceType = sourceType,
                showHidden = preferences.value.showHiddenFiles
            )
            _browserState.update {
                it.copy(rawEntries = entries, isLoading = false)
            }
        }
    }

    fun enterDirectory(entry: FileEntry) {
        if (!entry.isDirectory) return
        val nextCrumbs = _browserState.value.breadcrumbs + BreadcrumbItem(
            label = entry.name,
            pathOrUri = entry.pathOrUri,
            sourceType = entry.sourceType
        )
        openDirectory(
            pathOrUri = entry.pathOrUri,
            sourceType = entry.sourceType,
            breadcrumbs = nextCrumbs
        )
    }

    fun navigateToBreadcrumb(index: Int) {
        val crumbs = _browserState.value.breadcrumbs
        if (index !in crumbs.indices) return
        val target = crumbs[index]
        if (target.sourceType == FileSourceType.MEDIASTORE) return
        openDirectory(
            pathOrUri = target.pathOrUri,
            sourceType = target.sourceType,
            breadcrumbs = crumbs.take(index + 1)
        )
    }

    fun navigateUpDirectory(): Boolean {
        val state = _browserState.value
        if (state.selectedIds.isNotEmpty()) {
            clearSelection()
            return true
        }
        if (state.activeCategory != null) {
            openDefaultBrowserRoot()
            return true
        }
        if (state.breadcrumbs.size > 1) {
            navigateToBreadcrumb(state.breadcrumbs.size - 2)
            return true
        }
        return false
    }

    fun refreshCurrentDirectory() {
        val state = _browserState.value
        val category = state.activeCategory
        if (category != null) {
            openCategory(category)
        } else if (state.currentPathOrUri.isNotBlank()) {
            openDirectory(
                pathOrUri = state.currentPathOrUri,
                sourceType = state.currentSourceType,
                volumeId = state.activeVolumeId,
                breadcrumbs = state.breadcrumbs
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _browserState.update { it.copy(searchQuery = query) }
    }

    fun updateMinSizeFilterMb(mb: Float) {
        _browserState.update { it.copy(minSizeFilterMb = mb.coerceIn(0f, 500f)) }
    }

    fun toggleSelection(entry: FileEntry) {
        _browserState.update { state ->
            val next = if (entry.id in state.selectedIds) {
                state.selectedIds - entry.id
            } else {
                state.selectedIds + entry.id
            }
            state.copy(selectedIds = next)
        }
    }

    fun selectAllVisible() {
        val ids = displayedEntries.value.map { it.id }.toSet()
        _browserState.update { it.copy(selectedIds = ids) }
    }

    fun clearSelection() {
        _browserState.update { it.copy(selectedIds = emptySet()) }
    }

    fun selectForInspector(entry: FileEntry?) {
        _browserState.update { it.copy(inspectorEntry = entry) }
    }

    fun stageClipboard(entries: List<FileEntry>, mode: ClipboardMode) {
        if (entries.isEmpty()) return
        _browserState.update { it.copy(clipboard = ClipboardState(entries, mode), selectedIds = emptySet()) }
        emitMessage("${entries.size} item(s) ready to ${mode.label.lowercase()}")
    }

    fun clearClipboard() {
        _browserState.update { it.copy(clipboard = null) }
    }

    fun pasteClipboardHere() {
        val state = _browserState.value
        val clip = state.clipboard ?: return
        if (state.activeCategory != null || state.currentSourceType != FileSourceType.FILE) {
            emitMessage("Navigate into a writable local directory to paste")
            return
        }
        val filePaths = clip.items.filter { it.sourceType == FileSourceType.FILE }.map { it.pathOrUri }
        if (filePaths.isEmpty()) {
            emitMessage("Only direct filesystem items can be batch-pasted via WorkManager")
            return
        }
        jobScheduler.enqueueTransfer(
            sourcePaths = filePaths,
            destDirPath = state.currentPathOrUri,
            moveMode = clip.mode == ClipboardMode.MOVE
        )
        _browserState.update { it.copy(clipboard = null) }
        emitMessage("${clip.mode.label} queued in background")
    }

    fun createFolder(name: String) {
        val state = _browserState.value
        if (state.activeCategory != null) {
            emitMessage("Cannot create folders inside a category view")
            return
        }
        viewModelScope.launch {
            storageRepository.createFolder(state.currentPathOrUri, state.currentSourceType, name)
                .onSuccess {
                    emitMessage("Created folder \"$name\"")
                    refreshCurrentDirectory()
                }
                .onFailure { err -> emitMessage(err.message ?: "Could not create folder") }
        }
    }

    fun renameEntry(entry: FileEntry, newName: String) {
        viewModelScope.launch {
            storageRepository.renameEntry(entry, newName)
                .onSuccess {
                    emitMessage("Renamed to \"$newName\"")
                    refreshCurrentDirectory()
                }
                .onFailure { err -> emitMessage(err.message ?: "Rename failed") }
        }
    }

    fun deleteEntries(entries: List<FileEntry>) {
        viewModelScope.launch {
            storageRepository.deleteEntries(entries)
                .onSuccess { count ->
                    emitMessage("Deleted $count item(s)")
                    clearSelection()
                    refreshCurrentDirectory()
                    refreshOverview()
                }
                .onFailure { err -> emitMessage(err.message ?: "Delete failed") }
        }
    }

    fun toggleBookmarkCurrentDirectory() {
        val state = _browserState.value
        if (state.activeCategory != null || state.currentPathOrUri.isBlank()) return
        val label = state.breadcrumbs.lastOrNull()?.label ?: "Folder"
        viewModelScope.launch {
            val exists = bookmarks.value.any { it.pathOrUri == state.currentPathOrUri }
            if (exists) {
                db.bookmarkDao().delete(state.currentPathOrUri)
                emitMessage("Removed bookmark \"$label\"")
            } else {
                db.bookmarkDao().upsert(
                    BookmarkEntity(
                        pathOrUri = state.currentPathOrUri,
                        label = label,
                        sourceType = state.currentSourceType.name
                    )
                )
                emitMessage("Pinned \"$label\"")
            }
        }
    }

    fun removeBookmark(pathOrUri: String) {
        viewModelScope.launch {
            db.bookmarkDao().delete(pathOrUri)
        }
    }

    fun onSafTreeSelected(uri: Uri) {
        viewModelScope.launch {
            val mount = storageRepository.registerSafMount(uri)
            emitMessage("Mounted \"${mount.displayName}\" via SAF")
            refreshOverview()
            openDirectory(
                pathOrUri = mount.treeUri,
                sourceType = FileSourceType.SAF,
                volumeId = "saf_${mount.treeUri.hashCode()}",
                breadcrumbs = listOf(BreadcrumbItem(mount.displayName, mount.treeUri, FileSourceType.SAF))
            )
            _currentDestination.value = AppDestination.BROWSE
        }
    }

    fun removeSafMount(treeUri: String) {
        viewModelScope.launch {
            storageRepository.removeSafMount(treeUri)
            emitMessage("Unmounted SAF folder")
            refreshOverview()
        }
    }

    fun importMediaToWorkspace(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var ok = 0
            uris.forEach { uri ->
                if (storageRepository.importUriToWorkspace(uri).isSuccess) ok++
            }
            emitMessage("Imported $ok file(s) to App Workspace")
            refreshOverview()
            refreshCurrentDirectory()
        }
    }

    // --- Archive Operations ---

    fun compressEntries(entries: List<FileEntry>, archiveName: String, level: Int, password: String?) {
        val state = _browserState.value
        val destFolder = if (state.currentSourceType == FileSourceType.FILE && state.activeCategory == null) {
            File(state.currentPathOrUri)
        } else {
            storageRepository.workspaceDir
        }
        val cleanName = if (archiveName.endsWith(".zip", ignoreCase = true)) archiveName else "$archiveName.zip"
        val outFile = storageRepository.uniqueFile(destFolder, cleanName)
        val sourcePaths = entries.filter { it.sourceType == FileSourceType.FILE }.map { it.pathOrUri }
        if (sourcePaths.isEmpty()) {
            emitMessage("Select local filesystem files to compress")
            return
        }
        jobScheduler.enqueueCompress(
            sourcePaths = sourcePaths,
            outputZipPath = outFile.absolutePath,
            compressionLevel = level,
            password = password?.takeIf { it.isNotBlank() }
        )
        clearSelection()
        emitMessage("Compressing ${outFile.name} (Level $level)")
    }

    fun inspectArchive(entry: FileEntry, password: String? = null) {
        viewModelScope.launch {
            val localFile = runCatching { storageRepository.resolveLocalFileForReader(entry) }.getOrNull()
            if (localFile == null) {
                emitMessage("Could not open archive")
                return@launch
            }
            _archiveInspection.value = ArchiveInspectionState(entry = entry, localFile = localFile, isLoading = true)
            val result = withContext(Dispatchers.IO) {
                runCatching { ArchiveEngine.listEntries(localFile, password) }
            }
            result.onSuccess { list ->
                _archiveInspection.value = ArchiveInspectionState(
                    entry = entry,
                    localFile = localFile,
                    contents = list,
                    isLoading = false
                )
            }.onFailure { err ->
                _archiveInspection.value = ArchiveInspectionState(
                    entry = entry,
                    localFile = localFile,
                    isLoading = false,
                    errorMessage = err.message ?: "Archive may be password-protected or unsupported"
                )
            }
        }
    }

    fun dismissArchiveInspection() {
        _archiveInspection.value = null
    }

    fun extractInspectedArchive(password: String?) {
        val inspected = _archiveInspection.value ?: return
        val parentDir = inspected.localFile.parentFile ?: storageRepository.workspaceDir
        val folderName = inspected.entry.name.substringBeforeLast('.', inspected.entry.name)
        val targetDir = storageRepository.uniqueFile(parentDir, folderName)
        jobScheduler.enqueueExtract(
            archivePath = inspected.localFile.absolutePath,
            destDirPath = targetDir.absolutePath,
            password = password?.takeIf { it.isNotBlank() }
        )
        _archiveInspection.value = null
        emitMessage("Extracting ${inspected.entry.name} to ${targetDir.name}")
    }

    fun cancelJob(id: String) = jobScheduler.cancelJob(id)
    fun pruneJobs() = jobScheduler.pruneFinishedJobs()

    // --- Built-In Previews ---

    fun openFileEntry(entry: FileEntry) {
        if (entry.isDirectory) {
            enterDirectory(entry)
            return
        }
        viewModelScope.launch {
            db.recentFileDao().upsert(
                RecentFileEntity(
                    pathOrUri = entry.pathOrUri,
                    name = entry.name,
                    mimeType = entry.mimeType,
                    sizeBytes = entry.sizeBytes,
                    sourceType = entry.sourceType.name
                )
            )
            when {
                entry.isArchive -> inspectArchive(entry)
                entry.isVideo -> {
                    val uriStr = if (entry.sourceType == FileSourceType.FILE) {
                        Uri.fromFile(File(entry.pathOrUri)).toString()
                    } else {
                        entry.pathOrUri
                    }
                    _audioPlayerEntry.value = null
                    _previewTarget.value = PreviewTarget.Video(entry, uriStr)
                }
                entry.isAudio -> {
                    val uriStr = if (entry.sourceType == FileSourceType.FILE) {
                        Uri.fromFile(File(entry.pathOrUri)).toString()
                    } else {
                        entry.pathOrUri
                    }
                    _audioPlayerEntry.value = entry to uriStr
                }
                entry.isImage -> {
                    val uriStr = if (entry.sourceType == FileSourceType.FILE) {
                        Uri.fromFile(File(entry.pathOrUri)).toString()
                    } else {
                        entry.pathOrUri
                    }
                    _previewTarget.value = PreviewTarget.Image(entry, uriStr)
                }
                entry.isPdf -> {
                    runCatching { storageRepository.resolveLocalFileForReader(entry) }
                        .onSuccess { localFile ->
                            _previewTarget.value = PreviewTarget.Pdf(entry, localFile.absolutePath)
                        }
                        .onFailure { emitMessage("Could not open PDF") }
                }
                entry.isTextLike -> {
                    runCatching { storageRepository.readTextPreview(entry) }
                        .onSuccess { (text, truncated) ->
                            _previewTarget.value = PreviewTarget.Text(entry, text, truncated)
                        }
                        .onFailure { emitMessage("Unable to read text file") }
                }
                else -> emitMessage("No built-in preview for .${entry.extension.ifBlank { "bin" }} — use Share to open externally")
            }
        }
    }

    fun openRecent(recent: RecentFileEntity) {
        val source = FileSourceType.entries.firstOrNull { it.name == recent.sourceType } ?: FileSourceType.FILE
        val entry = FileEntry(
            id = recent.pathOrUri,
            name = recent.name,
            pathOrUri = recent.pathOrUri,
            parentPathOrUri = null,
            isDirectory = false,
            sizeBytes = recent.sizeBytes,
            lastModifiedMs = recent.openedAtMs,
            mimeType = recent.mimeType,
            sourceType = source
        )
        openFileEntry(entry)
    }

    fun closePreview() {
        val current = _previewTarget.value
        _previewTarget.value = null
        if (current != null) {
            val isVaultTemp = when (current) {
                is PreviewTarget.Image -> current.isVaultTemp
                is PreviewTarget.Pdf -> current.isVaultTemp
                is PreviewTarget.Text -> current.isVaultTemp
                is PreviewTarget.Video -> current.isVaultTemp
            }
            if (isVaultTemp) {
                viewModelScope.launch { vaultRepository.wipeEphemeralCache() }
            }
        }
    }

    fun dismissAudioPlayer() {
        _audioPlayerEntry.value = null
    }

    // --- Privacy Vault ---

    fun checkVaultAutoLock() {
        val state = _vaultUiState.value
        if (!state.isUnlocked) return
        val timeoutSec = preferences.value.vaultAutoLockSeconds
        if (timeoutSec == 0) {
            lockVault()
            return
        }
        val elapsedSec = (System.currentTimeMillis() - state.lastActivityMs) / 1000L
        if (elapsedSec >= timeoutSec) {
            lockVault()
        }
    }

    fun touchVaultActivity() {
        if (_vaultUiState.value.isUnlocked) {
            _vaultUiState.update { it.copy(lastActivityMs = System.currentTimeMillis()) }
        }
    }

    fun setupVaultPin(pin: String) {
        if (pin.length < 4) {
            _vaultUiState.update { it.copy(pinError = "PIN must be at least 4 digits") }
            return
        }
        viewModelScope.launch {
            val (hash, salt) = vaultRepository.hashPin(pin)
            preferencesRepository.setVaultPinCredentials(hash, salt)
            _vaultUiState.value = VaultUiState(
                isUnlocked = true,
                lastActivityMs = System.currentTimeMillis(),
                pinError = null
            )
            emitMessage("Vault PIN configured & unlocked")
        }
    }

    fun unlockVaultWithPin(pin: String) {
        val prefs = preferences.value
        val hash = prefs.vaultPinHash ?: return
        val salt = prefs.vaultPinSalt ?: return
        val ok = vaultRepository.verifyPin(pin, hash, salt)
        if (ok) {
            _vaultUiState.value = VaultUiState(
                isUnlocked = true,
                lastActivityMs = System.currentTimeMillis(),
                pinError = null
            )
        } else {
            _vaultUiState.update { it.copy(pinError = "Incorrect PIN") }
        }
    }

    fun onBiometricUnlockSuccess() {
        _vaultUiState.value = VaultUiState(
            isUnlocked = true,
            lastActivityMs = System.currentTimeMillis(),
            pinError = null
        )
    }

    fun lockVault() {
        _vaultUiState.value = VaultUiState(isUnlocked = false)
        viewModelScope.launch { vaultRepository.wipeEphemeralCache() }
    }

    fun encryptEntriesToVault(entries: List<FileEntry>, deleteOriginal: Boolean) {
        val filesOnly = entries.filter { !it.isDirectory }
        if (filesOnly.isEmpty()) {
            emitMessage("Select files (not folders) to encrypt into Vault")
            return
        }
        viewModelScope.launch {
            _vaultUiState.update { it.copy(isBusy = true) }
            var success = 0
            filesOnly.forEach { entry ->
                if (vaultRepository.encryptEntry(entry, deleteOriginal).isSuccess) success++
            }
            _vaultUiState.update { it.copy(isBusy = false) }
            clearSelection()
            refreshCurrentDirectory()
            refreshOverview()
            emitMessage("Encrypted $success file(s) with AES-256-GCM")
        }
    }

    fun encryptPickedUrisToVault(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _vaultUiState.update { it.copy(isBusy = true) }
            var success = 0
            uris.forEach { uri ->
                if (vaultRepository.encryptUri(uri).isSuccess) success++
            }
            _vaultUiState.update { it.copy(isBusy = false, lastActivityMs = System.currentTimeMillis()) }
            emitMessage("Encrypted $success item(s) into Vault")
        }
    }

    fun previewVaultItem(item: VaultItemEntity) {
        touchVaultActivity()
        viewModelScope.launch {
            _vaultUiState.update { it.copy(isBusy = true) }
            vaultRepository.decryptToEphemeralFile(item)
                .onSuccess { decryptedFile ->
                    val virtualEntry = item.toVirtualFileEntry(decryptedFile.absolutePath)
                    val uriStr = Uri.fromFile(decryptedFile).toString()
                    when {
                        virtualEntry.isVideo -> _previewTarget.value = PreviewTarget.Video(virtualEntry, uriStr, isVaultTemp = true)
                        virtualEntry.isImage -> _previewTarget.value = PreviewTarget.Image(virtualEntry, uriStr, isVaultTemp = true)
                        virtualEntry.isPdf -> _previewTarget.value = PreviewTarget.Pdf(virtualEntry, decryptedFile.absolutePath, isVaultTemp = true)
                        virtualEntry.isAudio -> _audioPlayerEntry.value = virtualEntry to uriStr
                        virtualEntry.isTextLike -> {
                            val (content, truncated) = storageRepository.readTextPreview(virtualEntry)
                            _previewTarget.value = PreviewTarget.Text(virtualEntry, content, truncated, isVaultTemp = true)
                        }
                        else -> emitMessage("Decrypted temporarily — export to open this file format")
                    }
                }
                .onFailure { err -> emitMessage(err.message ?: "Decryption failed") }
            _vaultUiState.update { it.copy(isBusy = false) }
        }
    }

    fun exportVaultItem(item: VaultItemEntity, removeFromVault: Boolean) {
        touchVaultActivity()
        viewModelScope.launch {
            vaultRepository.exportAndDecrypt(item, removeFromVault)
                .onSuccess { restored ->
                    emitMessage("Restored \"${restored.name}\" to Workspace/Restored")
                    refreshOverview()
                }
                .onFailure { err -> emitMessage(err.message ?: "Restore failed") }
        }
    }

    fun deleteVaultItem(item: VaultItemEntity) {
        touchVaultActivity()
        viewModelScope.launch {
            vaultRepository.deleteVaultItem(item)
            emitMessage("Permanently removed \"${item.originalName}\"")
        }
    }

    // --- Preferences Updates ---

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    fun setViewMode(mode: ViewMode) = viewModelScope.launch { preferencesRepository.setViewMode(mode) }
    fun setGridColumns(cols: Int) = viewModelScope.launch { preferencesRepository.setGridColumns(cols) }
    fun setSort(field: SortField, ascending: Boolean) = viewModelScope.launch {
        preferencesRepository.setSort(field, ascending)
    }

    fun setShowHiddenFiles(show: Boolean) = viewModelScope.launch {
        preferencesRepository.setShowHiddenFiles(show)
        refreshCurrentDirectory()
    }

    fun setBiometricEnabled(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.setBiometricEnabled(enabled)
    }

    fun setVaultAutoLockSeconds(seconds: Int) = viewModelScope.launch {
        preferencesRepository.setVaultAutoLockSeconds(seconds)
    }

    fun clearRecentFiles() = viewModelScope.launch {
        db.recentFileDao().clearAll()
        emitMessage("Cleared recent file history")
    }

    fun emitMessage(message: String) {
        _snackbarMessages.tryEmit(message)
    }

    private fun buildComparator(field: SortField, ascending: Boolean): Comparator<FileEntry> {
        val base = Comparator<FileEntry> { a, b ->
            // Folders always precede files
            if (a.isDirectory != b.isDirectory) return@Comparator if (a.isDirectory) -1 else 1
            val cmp = when (field) {
                SortField.NAME -> a.name.lowercase().compareTo(b.name.lowercase())
                SortField.DATE -> a.lastModifiedMs.compareTo(b.lastModifiedMs)
                SortField.SIZE -> a.sizeBytes.compareTo(b.sizeBytes)
                SortField.TYPE -> a.extension.compareTo(b.extension)
            }
            if (cmp != 0) cmp else a.name.lowercase().compareTo(b.name.lowercase())
        }
        return if (ascending) base else Comparator { a, b ->
            if (a.isDirectory != b.isDirectory) if (a.isDirectory) -1 else 1 else -base.compare(a, b)
        }
    }
}
