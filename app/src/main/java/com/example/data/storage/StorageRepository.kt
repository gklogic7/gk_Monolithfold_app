package com.example.data.storage

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.example.data.local.SafMountDao
import com.example.data.local.SafMountEntity
import com.example.model.CategorySummary
import com.example.model.FileCategory
import com.example.model.FileEntry
import com.example.model.FileSourceType
import com.example.model.StorageVolumeInfo
import com.example.model.StorageVolumeKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class StorageRepository(
    private val context: Context,
    private val safMountDao: SafMountDao
) {
    val workspaceDir: File
        get() = File(context.filesDir, "Workspace").apply {
            if (!exists()) mkdirs()
        }

    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            false
        }
    }

    suspend fun ensureWorkspaceSeeded() = withContext(Dispatchers.IO) {
        val marker = File(context.filesDir, ".workspace_seeded_v1")
        if (marker.exists()) return@withContext

        val root = workspaceDir
        val notesDir = File(root, "Notes").apply { mkdirs() }
        val archivesDir = File(root, "Archives").apply { mkdirs() }

        val readme = File(root, "README.md")
        if (!readme.exists()) {
            readme.writeText(
                """
                # Monolith Files — Workspace

                Welcome to your private on-device sandbox directory.
                Every action here uses real local file I/O:

                - Browse with Breadcrumbs, List/Grid toggle, Sort dropdown & Density slider
                - Compress files into `.zip` with Compression Level (0–9) & AES password
                - Extract `.zip`, `.7z`, and `.rar` archives in the background via WorkManager
                - Preview Markdown/code with adjustable font size, images with pinch-zoom,
                  PDFs with page scrubber, and videos in the built-in Media3 gesture player
                - Encrypt sensitive files into the Biometric/PIN Privacy Vault (AES-256-GCM)

                To browse full device folders or external SD/USB drives, mount a folder via
                Storage Access Framework (SAF) or enable optional All-Files Access in Settings.
                """.trimIndent()
            )
        }

        val config = File(notesDir, "layout_spec.json")
        if (!config.exists()) {
            config.writeText(
                """
                {
                  "theme": "monochrome",
                  "typography": {
                    "display": "Space Grotesk",
                    "body": "Inter",
                    "mono": "JetBrains Mono"
                  },
                  "controls": ["dropdown_menu", "slider"],
                  "adaptiveFormFactors": ["mobile", "tablet", "desktop"]
                }
                """.trimIndent()
            )
        }

        val sampleZip = File(archivesDir, "monolith_kit.zip")
        if (!sampleZip.exists()) {
            runCatching {
                ZipOutputStream(FileOutputStream(sampleZip)).use { zos ->
                    zos.putNextEntry(ZipEntry("manifest.txt"))
                    zos.write("Monolith Files Archive Verification Payload\nFormat: ZIP\n".toByteArray())
                    zos.closeEntry()
                    zos.putNextEntry(ZipEntry("docs/shortcuts.md"))
                    zos.write("# Controls\n- Long-press item for multi-select\n- Drag grid slider to scale columns\n".toByteArray())
                    zos.closeEntry()
                }
            }
        }

        marker.writeText("seeded")
    }

    suspend fun getStorageVolumes(): List<StorageVolumeInfo> = withContext(Dispatchers.IO) {
        val volumes = mutableListOf<StorageVolumeInfo>()

        // 1. Internal Storage (always queried via StatFs)
        val internalDir = Environment.getExternalStorageDirectory()
        val internalStats = safeStatFs(internalDir)
        volumes.add(
            StorageVolumeInfo(
                id = "internal",
                label = "Internal Storage",
                kind = StorageVolumeKind.INTERNAL,
                rootPathOrUri = internalDir.absolutePath,
                sourceType = FileSourceType.FILE,
                totalBytes = internalStats.first,
                usedBytes = internalStats.second,
                isAvailable = internalDir.exists()
            )
        )

        // 2. Removable SD Card & USB OTG via StorageManager (API 30+ directory API)
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        val discoveredRemovable = mutableListOf<StorageVolumeInfo>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && storageManager != null) {
            storageManager.storageVolumes.forEachIndexed { idx, vol ->
                if (!vol.isPrimary && vol.isRemovable) {
                    val dir = vol.directory
                    val stats = if (dir != null && dir.exists()) safeStatFs(dir) else 0L to 0L
                    val desc = vol.getDescription(context) ?: "Removable Drive"
                    val kind = if (desc.contains("USB", ignoreCase = true) || desc.contains("OTG", ignoreCase = true)) {
                        StorageVolumeKind.USB_OTG
                    } else {
                        StorageVolumeKind.SD_CARD
                    }
                    discoveredRemovable.add(
                        StorageVolumeInfo(
                            id = "removable_${vol.uuid ?: idx}",
                            label = desc,
                            kind = kind,
                            rootPathOrUri = dir?.absolutePath ?: "",
                            sourceType = FileSourceType.FILE,
                            totalBytes = stats.first,
                            usedBytes = stats.second,
                            isAvailable = dir != null && dir.exists() && dir.canRead()
                        )
                    )
                }
            }
        }

        val hasSd = discoveredRemovable.any { it.kind == StorageVolumeKind.SD_CARD }
        val hasUsb = discoveredRemovable.any { it.kind == StorageVolumeKind.USB_OTG }
        volumes.addAll(discoveredRemovable)

        if (!hasSd) {
            volumes.add(
                StorageVolumeInfo(
                    id = "sd_placeholder",
                    label = "SD Card",
                    kind = StorageVolumeKind.SD_CARD,
                    rootPathOrUri = "",
                    sourceType = FileSourceType.SAF,
                    totalBytes = 0L,
                    usedBytes = 0L,
                    isAvailable = false
                )
            )
        }
        if (!hasUsb) {
            volumes.add(
                StorageVolumeInfo(
                    id = "usb_placeholder",
                    label = "USB OTG",
                    kind = StorageVolumeKind.USB_OTG,
                    rootPathOrUri = "",
                    sourceType = FileSourceType.SAF,
                    totalBytes = 0L,
                    usedBytes = 0L,
                    isAvailable = false
                )
            )
        }

        // 3. App Workspace (always accessible without broad permissions)
        val workspaceBytes = computeFolderSize(workspaceDir)
        volumes.add(
            StorageVolumeInfo(
                id = "workspace",
                label = "App Workspace",
                kind = StorageVolumeKind.APP_WORKSPACE,
                rootPathOrUri = workspaceDir.absolutePath,
                sourceType = FileSourceType.FILE,
                totalBytes = internalStats.first.coerceAtLeast(1L),
                usedBytes = workspaceBytes,
                isAvailable = true
            )
        )

        // 4. Persisted SAF Mounted Folders
        val mounts = safMountDao.getAll()
        mounts.forEach { mount ->
            val doc = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(mount.treeUri)) }.getOrNull()
            if (doc != null && doc.exists()) {
                volumes.add(
                    StorageVolumeInfo(
                        id = "saf_${mount.treeUri.hashCode()}",
                        label = mount.displayName,
                        kind = StorageVolumeKind.SAF_MOUNT,
                        rootPathOrUri = mount.treeUri,
                        sourceType = FileSourceType.SAF,
                        totalBytes = 0L,
                        usedBytes = 0L,
                        isAvailable = doc.canRead()
                    )
                )
            }
        }

        volumes
    }

    suspend fun getCategorySummaries(): List<CategorySummary> = withContext(Dispatchers.IO) {
        FileCategory.entries.map { category ->
            val items = listCategoryFiles(category)
            CategorySummary(
                category = category,
                itemCount = items.size,
                totalBytes = items.sumOf { it.sizeBytes }
            )
        }
    }

    suspend fun listDirectory(
        pathOrUri: String,
        sourceType: FileSourceType,
        showHidden: Boolean
    ): List<FileEntry> = withContext(Dispatchers.IO) {
        when (sourceType) {
            FileSourceType.FILE -> listFileDirectory(File(pathOrUri), showHidden)
            FileSourceType.SAF -> listSafDirectory(Uri.parse(pathOrUri), showHidden)
            else -> emptyList()
        }
    }

    private fun listFileDirectory(dir: File, showHidden: Boolean): List<FileEntry> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val children = dir.listFiles() ?: return emptyList()
        return children
            .filter { showHidden || !it.name.startsWith(".") }
            .map { file -> file.toFileEntry() }
    }

    private fun listSafDirectory(uri: Uri, showHidden: Boolean): List<FileEntry> {
        val doc = DocumentFile.fromTreeUri(context, uri)
            ?: DocumentFile.fromSingleUri(context, uri)
            ?: return emptyList()
        if (!doc.exists() || !doc.isDirectory) return emptyList()
        return doc.listFiles()
            .filter { child ->
                val name = child.name ?: return@filter false
                showHidden || !name.startsWith(".")
            }
            .map { child ->
                val name = child.name ?: "Untitled"
                FileEntry(
                    id = child.uri.toString(),
                    name = name,
                    pathOrUri = child.uri.toString(),
                    parentPathOrUri = uri.toString(),
                    isDirectory = child.isDirectory,
                    sizeBytes = if (child.isDirectory) 0L else child.length(),
                    lastModifiedMs = child.lastModified(),
                    mimeType = if (child.isDirectory) "inode/directory" else (child.type ?: guessMimeType(name)),
                    sourceType = FileSourceType.SAF,
                    childCount = 0,
                    isHidden = name.startsWith(".")
                )
            }
    }

    suspend fun listCategoryFiles(category: FileCategory): List<FileEntry> = withContext(Dispatchers.IO) {
        val results = LinkedHashMap<String, FileEntry>()

        // 1. MediaStore queries (clean modern Android storage access)
        val mediaStoreEntries = queryMediaStoreCategory(category)
        mediaStoreEntries.forEach { results[it.id] = it }

        // 2. Also scan App Workspace (and Internal Storage Downloads/Documents if All-Files granted)
        val scanRoots = mutableListOf(workspaceDir)
        if (hasAllFilesAccess()) {
            val ext = Environment.getExternalStorageDirectory()
            when (category) {
                FileCategory.DOWNLOADS -> {
                    val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (dl.exists()) scanRoots.add(dl)
                }
                FileCategory.APKS -> scanRoots.add(ext)
                FileCategory.DOCUMENTS -> {
                    val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                    if (docs.exists()) scanRoots.add(docs)
                    val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (dl.exists()) scanRoots.add(dl)
                }
                else -> Unit
            }
        }

        scanRoots.forEach { root ->
            scanFilesMatchingCategory(root, category, maxDepth = 4).forEach { entry ->
                if (!results.containsKey(entry.pathOrUri)) {
                    results[entry.pathOrUri] = entry
                }
            }
        }

        results.values.sortedByDescending { it.lastModifiedMs }
    }

    private fun queryMediaStoreCategory(category: FileCategory): List<FileEntry> {
        val entries = mutableListOf<FileEntry>()
        val contentUri = when (category) {
            FileCategory.VIDEOS -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            FileCategory.IMAGES -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            FileCategory.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            FileCategory.DOWNLOADS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Files.getContentUri("external")
            }
            FileCategory.DOCUMENTS, FileCategory.APKS -> MediaStore.Files.getContentUri("external")
        }

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE
        )

        val selection: String? = when (category) {
            FileCategory.APKS -> "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.apk'"
            FileCategory.DOCUMENTS -> "${MediaStore.MediaColumns.MIME_TYPE} IN ('application/pdf','text/plain','text/markdown','application/json','application/msword','application/vnd.openxmlformats-officedocument.wordprocessingml.document')"
            else -> null
        }

        runCatching {
            context.contentResolver.query(
                contentUri,
                projection,
                selection,
                null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)

                var count = 0
                while (cursor.moveToNext() && count < 300) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val size = cursor.getLong(sizeCol)
                    val modifiedSec = cursor.getLong(dateCol)
                    val mime = cursor.getString(mimeCol) ?: guessMimeType(name)
                    val itemUri = ContentUris.withAppendedId(contentUri, id).toString()

                    entries.add(
                        FileEntry(
                            id = itemUri,
                            name = name,
                            pathOrUri = itemUri,
                            parentPathOrUri = null,
                            isDirectory = false,
                            sizeBytes = size,
                            lastModifiedMs = modifiedSec * 1000L,
                            mimeType = mime,
                            sourceType = FileSourceType.MEDIASTORE
                        )
                    )
                    count++
                }
            }
        }
        return entries
    }

    private fun scanFilesMatchingCategory(root: File, category: FileCategory, maxDepth: Int): List<FileEntry> {
        val out = mutableListOf<FileEntry>()
        fun walk(dir: File, depth: Int) {
            if (depth > maxDepth || out.size >= 250) return
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (f.name.startsWith(".")) continue
                if (f.isDirectory) {
                    if (f.name != "Android") walk(f, depth + 1)
                } else {
                    val entry = f.toFileEntry()
                    val matches = when (category) {
                        FileCategory.VIDEOS -> entry.isVideo
                        FileCategory.IMAGES -> entry.isImage
                        FileCategory.AUDIO -> entry.isAudio
                        FileCategory.DOCUMENTS -> entry.isPdf || entry.isTextLike || entry.extension in setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx")
                        FileCategory.APKS -> entry.extension == "apk"
                        FileCategory.DOWNLOADS -> true
                    }
                    if (matches) out.add(entry)
                }
            }
        }
        if (root.exists() && root.isDirectory) walk(root, 0)
        return out
    }

    suspend fun registerSafMount(treeUri: Uri): SafMountEntity = withContext(Dispatchers.IO) {
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
        }
        val doc = DocumentFile.fromTreeUri(context, treeUri)
        val label = doc?.name ?: treeUri.lastPathSegment?.substringAfterLast(':') ?: "Mounted Folder"
        val entity = SafMountEntity(treeUri = treeUri.toString(), displayName = label)
        safMountDao.upsert(entity)
        entity
    }

    suspend fun removeSafMount(treeUri: String) = withContext(Dispatchers.IO) {
        runCatching {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.releasePersistableUriPermission(Uri.parse(treeUri), flags)
        }
        safMountDao.delete(treeUri)
    }

    suspend fun createFolder(parentPathOrUri: String, sourceType: FileSourceType, folderName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sanitized = folderName.trim()
                require(sanitized.isNotEmpty() && !sanitized.contains("/")) { "Invalid folder name" }
                when (sourceType) {
                    FileSourceType.FILE -> {
                        val target = File(parentPathOrUri, sanitized)
                        if (!target.mkdirs()) error("Could not create folder (check storage permissions)")
                    }
                    FileSourceType.SAF -> {
                        val parent = DocumentFile.fromTreeUri(context, Uri.parse(parentPathOrUri))
                            ?: error("Mounted folder unavailable")
                        parent.createDirectory(sanitized) ?: error("Could not create SAF directory")
                    }
                    else -> error("Folder creation not supported in this view")
                }
                Unit
            }
        }

    suspend fun renameEntry(entry: FileEntry, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val sanitized = newName.trim()
            require(sanitized.isNotEmpty() && !sanitized.contains("/")) { "Invalid name" }
            when (entry.sourceType) {
                FileSourceType.FILE -> {
                    val file = File(entry.pathOrUri)
                    val dest = File(file.parentFile, sanitized)
                    if (!file.renameTo(dest)) error("Rename failed")
                }
                FileSourceType.SAF -> {
                    val doc = DocumentFile.fromSingleUri(context, Uri.parse(entry.pathOrUri))
                        ?: DocumentFile.fromTreeUri(context, Uri.parse(entry.pathOrUri))
                        ?: error("Document not found")
                    if (!doc.renameTo(sanitized)) error("SAF rename failed")
                }
                else -> error("Rename not supported for this item")
            }
        }
    }

    suspend fun deleteEntries(entries: List<FileEntry>): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var deleted = 0
            entries.forEach { entry ->
                when (entry.sourceType) {
                    FileSourceType.FILE -> {
                        val f = File(entry.pathOrUri)
                        if (f.deleteRecursively()) deleted++
                    }
                    FileSourceType.SAF -> {
                        val doc = DocumentFile.fromSingleUri(context, Uri.parse(entry.pathOrUri))
                            ?: DocumentFile.fromTreeUri(context, Uri.parse(entry.pathOrUri))
                        if (doc?.delete() == true) deleted++
                    }
                    FileSourceType.MEDIASTORE -> {
                        val rows = context.contentResolver.delete(Uri.parse(entry.pathOrUri), null, null)
                        if (rows > 0) deleted++
                    }
                    FileSourceType.VAULT -> Unit
                }
            }
            deleted
        }
    }

    suspend fun readTextPreview(entry: FileEntry, maxBytes: Int = 256 * 1024): Pair<String, Boolean> =
        withContext(Dispatchers.IO) {
            openInputStream(entry).use { input ->
                val buffer = ByteArray(maxBytes + 1)
                var totalRead = 0
                while (totalRead < buffer.size) {
                    val read = input.read(buffer, totalRead, buffer.size - totalRead)
                    if (read <= 0) break
                    totalRead += read
                }
                val truncated = totalRead > maxBytes
                val actualLen = if (truncated) maxBytes else totalRead
                String(buffer, 0, actualLen, Charsets.UTF_8) to truncated
            }
        }

    suspend fun resolveLocalFileForReader(entry: FileEntry): File = withContext(Dispatchers.IO) {
        if (entry.sourceType == FileSourceType.FILE) {
            return@withContext File(entry.pathOrUri)
        }
        val cacheDir = File(context.cacheDir, "reader_temp").apply { mkdirs() }
        val tempFile = File(cacheDir, "${entry.id.hashCode()}_${entry.name.replace('/', '_')}")
        openInputStream(entry).use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        tempFile
    }

    suspend fun importUriToWorkspace(uri: Uri): Result<FileEntry> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = DocumentFile.fromSingleUri(context, uri)
            val name = doc?.name ?: "imported_${System.currentTimeMillis()}"
            val dest = uniqueFile(workspaceDir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            } ?: error("Cannot read selected file")
            dest.toFileEntry()
        }
    }

    fun openInputStream(entry: FileEntry): InputStream {
        return when (entry.sourceType) {
            FileSourceType.FILE -> FileInputStream(File(entry.pathOrUri))
            FileSourceType.SAF, FileSourceType.MEDIASTORE -> {
                context.contentResolver.openInputStream(Uri.parse(entry.pathOrUri))
                    ?: error("Unable to open stream for ${entry.name}")
            }
            FileSourceType.VAULT -> error("Vault items must be decrypted via VaultRepository")
        }
    }

    fun getShareUri(entry: FileEntry): Uri {
        return when (entry.sourceType) {
            FileSourceType.FILE -> {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    File(entry.pathOrUri)
                )
            }
            FileSourceType.SAF, FileSourceType.MEDIASTORE -> Uri.parse(entry.pathOrUri)
            FileSourceType.VAULT -> error("Decrypt file before sharing")
        }
    }

    fun uniqueFile(parent: File, desiredName: String): File {
        var candidate = File(parent, desiredName)
        if (!candidate.exists()) return candidate
        val base = desiredName.substringBeforeLast('.', desiredName)
        val ext = desiredName.substringAfterLast('.', "")
        var index = 1
        while (candidate.exists()) {
            val name = if (ext.isNotEmpty() && base != desiredName) "$base ($index).$ext" else "$desiredName ($index)"
            candidate = File(parent, name)
            index++
        }
        return candidate
    }

    private fun safeStatFs(dir: File): Pair<Long, Long> {
        return runCatching {
            val stat = StatFs(dir.absolutePath)
            val total = stat.totalBytes
            val available = stat.availableBytes
            total to (total - available).coerceAtLeast(0L)
        }.getOrElse { 0L to 0L }
    }

    private fun computeFolderSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        dir.walkTopDown().maxDepth(5).forEach { f ->
            if (f.isFile) total += f.length()
        }
        return total
    }

    private fun File.toFileEntry(): FileEntry {
        val isDir = isDirectory
        val children = if (isDir) listFiles()?.size ?: 0 else 0
        return FileEntry(
            id = absolutePath,
            name = name,
            pathOrUri = absolutePath,
            parentPathOrUri = parent,
            isDirectory = isDir,
            sizeBytes = if (isDir) 0L else length(),
            lastModifiedMs = lastModified(),
            mimeType = if (isDir) "inode/directory" else guessMimeType(name),
            sourceType = FileSourceType.FILE,
            childCount = children,
            isHidden = name.startsWith(".")
        )
    }

    companion object {
        fun guessMimeType(name: String): String {
            val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
            if (ext.isEmpty()) return "application/octet-stream"
            return when (ext) {
                "md", "markdown" -> "text/markdown"
                "json" -> "application/json"
                "kt", "kts", "java", "py", "ts", "js", "yaml", "yml", "log", "sh" -> "text/plain"
                "7z" -> "application/x-7z-compressed"
                "rar" -> "application/vnd.rar"
                "apk" -> "application/vnd.android.package-archive"
                else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
            }
        }
    }
}
