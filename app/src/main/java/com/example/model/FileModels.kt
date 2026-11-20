package com.example.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Monochrome Light"),
    DARK("Monochrome Dark")
}

enum class ViewMode(val label: String) {
    LIST("List"),
    GRID("Grid")
}

enum class SortField(val label: String) {
    NAME("Name"),
    DATE("Date Modified"),
    SIZE("Size"),
    TYPE("Type")
}

enum class FileSourceType {
    FILE,
    SAF,
    MEDIASTORE,
    VAULT
}

enum class StorageVolumeKind(val label: String) {
    INTERNAL("Internal Storage"),
    SD_CARD("SD Card"),
    USB_OTG("USB OTG"),
    SAF_MOUNT("Mounted Folder"),
    APP_WORKSPACE("App Workspace")
}

enum class FileCategory(val label: String) {
    VIDEOS("Videos"),
    IMAGES("Images"),
    AUDIO("Audio"),
    DOCUMENTS("Documents"),
    APKS("APKs"),
    DOWNLOADS("Downloads")
}

enum class AppDestination(val label: String) {
    OVERVIEW("Overview"),
    BROWSE("Browse"),
    VAULT("Vault"),
    SETTINGS("Settings")
}

enum class ClipboardMode(val label: String) {
    COPY("Copy"),
    MOVE("Move")
}

enum class VideoAspectMode(val label: String) {
    FIT("Fit"),
    FILL("Fill"),
    ZOOM("Zoom"),
    RATIO_16_9("16:9"),
    RATIO_4_3("4:3")
}

data class StorageVolumeInfo(
    val id: String,
    val label: String,
    val kind: StorageVolumeKind,
    val rootPathOrUri: String,
    val sourceType: FileSourceType,
    val totalBytes: Long,
    val usedBytes: Long,
    val isAvailable: Boolean = true
) {
    val freeBytes: Long get() = (totalBytes - usedBytes).coerceAtLeast(0L)
    val usedFraction: Float
        get() = if (totalBytes > 0L) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

data class CategorySummary(
    val category: FileCategory,
    val itemCount: Int,
    val totalBytes: Long
)

data class FileEntry(
    val id: String,
    val name: String,
    val pathOrUri: String,
    val parentPathOrUri: String?,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
    val mimeType: String,
    val sourceType: FileSourceType,
    val childCount: Int = 0,
    val isHidden: Boolean = name.startsWith(".")
) {
    val extension: String
        get() = if (isDirectory) "" else name.substringAfterLast('.', "").lowercase(Locale.US)

    val isImage: Boolean
        get() = mimeType.startsWith("image/") || extension in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")

    val isVideo: Boolean
        get() = mimeType.startsWith("video/") || extension in setOf("mp4", "mkv", "webm", "mov", "avi", "m4v", "3gp")

    val isAudio: Boolean
        get() = mimeType.startsWith("audio/") || extension in setOf("mp3", "wav", "flac", "ogg", "m4a", "aac", "opus")

    val isPdf: Boolean
        get() = mimeType == "application/pdf" || extension == "pdf"

    val isArchive: Boolean
        get() = extension in setOf("zip", "7z", "rar")

    val isTextLike: Boolean
        get() = mimeType.startsWith("text/") || extension in setOf(
            "txt", "md", "markdown", "json", "xml", "kt", "java", "py", "js", "ts",
            "html", "css", "csv", "log", "yaml", "yml", "ini", "conf", "sh", "gradle", "kts", "properties"
        )
}

data class BreadcrumbItem(
    val label: String,
    val pathOrUri: String,
    val sourceType: FileSourceType
)

data class ClipboardState(
    val items: List<FileEntry>,
    val mode: ClipboardMode
)

data class ArchiveEntryInfo(
    val name: String,
    val sizeBytes: Long,
    val isDirectory: Boolean
)

data class BackgroundJobInfo(
    val id: String,
    val title: String,
    val status: String,
    val progressPercent: Int,
    val isRunning: Boolean,
    val isFailed: Boolean,
    val detail: String = ""
)

sealed class PreviewTarget {
    data class Video(val entry: FileEntry, val uriString: String, val isVaultTemp: Boolean = false) : PreviewTarget()
    data class Image(val entry: FileEntry, val uriString: String, val isVaultTemp: Boolean = false) : PreviewTarget()
    data class Pdf(val entry: FileEntry, val filePath: String, val isVaultTemp: Boolean = false) : PreviewTarget()
    data class Text(val entry: FileEntry, val content: String, val truncated: Boolean, val isVaultTemp: Boolean = false) : PreviewTarget()
}

object Formatters {
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "0 B"
        if (bytes < 1024L) return "$bytes B"
        val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceAtMost(4)
        val unit = "KMGT"[exp - 1]
        val value = bytes / 1024.0.pow(exp.toDouble())
        return String.format(Locale.US, if (value >= 100) "%.0f %sB" else "%.1f %sB", value, unit)
    }

    fun formatDate(epochMs: Long): String {
        if (epochMs <= 0L) return "—"
        return synchronized(dateFormatter) {
            dateFormatter.format(Date(epochMs))
        }
    }

    fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatTimeoutSeconds(seconds: Int): String = when {
        seconds <= 0 -> "Immediate"
        seconds < 60 -> "${seconds}s"
        else -> "${seconds / 60}m"
    }
}
