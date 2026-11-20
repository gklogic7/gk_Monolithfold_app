package com.example.data.archive

import com.example.model.ArchiveEntryInfo
import com.github.junrar.Archive
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object ArchiveEngine {

    fun listEntries(archiveFile: File, password: String? = null): List<ArchiveEntryInfo> {
        val ext = archiveFile.extension.lowercase(Locale.US)
        return when (ext) {
            "zip" -> {
                val zip = if (!password.isNullOrEmpty()) ZipFile(archiveFile, password.toCharArray()) else ZipFile(archiveFile)
                zip.fileHeaders.map { header ->
                    ArchiveEntryInfo(
                        name = header.fileName,
                        sizeBytes = header.uncompressedSize,
                        isDirectory = header.isDirectory
                    )
                }
            }
            "7z" -> {
                val builder = SevenZFile.builder().setFile(archiveFile)
                if (!password.isNullOrEmpty()) builder.setPassword(password.toCharArray())
                builder.get().use { sevenZ ->
                    sevenZ.entries.map { entry ->
                        ArchiveEntryInfo(
                            name = entry.name,
                            sizeBytes = entry.size,
                            isDirectory = entry.isDirectory
                        )
                    }
                }
            }
            "rar" -> {
                Archive(archiveFile).use { rar ->
                    rar.fileHeaders.map { header ->
                        ArchiveEntryInfo(
                            name = header.fileName.replace('\\', '/'),
                            sizeBytes = header.fullUnpackSize,
                            isDirectory = header.isDirectory
                        )
                    }
                }
            }
            else -> emptyList()
        }
    }

    fun compressToZip(
        sources: List<File>,
        outputZip: File,
        compressionLevel: Int,
        password: String? = null,
        onProgress: (percent: Int, currentName: String) -> Unit
    ) {
        if (outputZip.exists()) outputZip.delete()
        val zipFile = if (!password.isNullOrEmpty()) {
            ZipFile(outputZip, password.toCharArray())
        } else {
            ZipFile(outputZip)
        }

        val params = ZipParameters().apply {
            compressionMethod = if (compressionLevel <= 0) CompressionMethod.STORE else CompressionMethod.DEFLATE
            this.compressionLevel = mapCompressionLevel(compressionLevel)
            if (!password.isNullOrEmpty()) {
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            }
        }

        val total = sources.size.coerceAtLeast(1)
        sources.forEachIndexed { index, source ->
            onProgress(((index * 100) / total).coerceIn(0, 99), source.name)
            if (source.isDirectory) {
                zipFile.addFolder(source, params)
            } else if (source.exists()) {
                zipFile.addFile(source, params)
            }
        }
        onProgress(100, outputZip.name)
    }

    fun extractArchive(
        archiveFile: File,
        destDir: File,
        password: String? = null,
        onProgress: (percent: Int, currentName: String) -> Unit
    ) {
        if (!destDir.exists()) destDir.mkdirs()
        val canonicalDest = destDir.canonicalPath
        val ext = archiveFile.extension.lowercase(Locale.US)

        when (ext) {
            "zip" -> {
                val zip = if (!password.isNullOrEmpty()) {
                    ZipFile(archiveFile, password.toCharArray())
                } else {
                    ZipFile(archiveFile)
                }
                val headers = zip.fileHeaders
                val total = headers.size.coerceAtLeast(1)
                headers.forEachIndexed { index, header ->
                    val target = File(destDir, header.fileName)
                    require(target.canonicalPath.startsWith(canonicalDest)) { "Blocked unsafe archive entry path" }
                    onProgress(((index * 100) / total).coerceIn(0, 99), header.fileName)
                    zip.extractFile(header, destDir.absolutePath)
                }
                onProgress(100, archiveFile.name)
            }
            "7z" -> {
                val builder = SevenZFile.builder().setFile(archiveFile)
                if (!password.isNullOrEmpty()) builder.setPassword(password.toCharArray())
                builder.get().use { sevenZ ->
                    val allCount = sevenZ.entries.count().coerceAtLeast(1)
                    var processed = 0
                    var entry = sevenZ.nextEntry
                    val buffer = ByteArray(32 * 1024)
                    while (entry != null) {
                        val outFile = File(destDir, entry.name)
                        require(outFile.canonicalPath.startsWith(canonicalDest)) { "Blocked unsafe 7z path" }
                        onProgress(((processed * 100) / allCount).coerceIn(0, 99), entry.name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos ->
                                var read = sevenZ.read(buffer)
                                while (read > 0) {
                                    fos.write(buffer, 0, read)
                                    read = sevenZ.read(buffer)
                                }
                            }
                        }
                        processed++
                        entry = sevenZ.nextEntry
                    }
                }
                onProgress(100, archiveFile.name)
            }
            "rar" -> {
                Archive(archiveFile).use { rar ->
                    val headers = rar.fileHeaders
                    val total = headers.size.coerceAtLeast(1)
                    headers.forEachIndexed { index, header ->
                        val normalized = header.fileName.replace('\\', '/')
                        val outFile = File(destDir, normalized)
                        require(outFile.canonicalPath.startsWith(canonicalDest)) { "Blocked unsafe RAR path" }
                        onProgress(((index * 100) / total).coerceIn(0, 99), normalized)
                        if (header.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos ->
                                rar.extractFile(header, fos)
                            }
                        }
                    }
                }
                onProgress(100, archiveFile.name)
            }
            else -> error("Unsupported archive format: .$ext")
        }
    }

    private fun mapCompressionLevel(level: Int): CompressionLevel = when (level.coerceIn(0, 9)) {
        0 -> CompressionLevel.NO_COMPRESSION
        1 -> CompressionLevel.FASTEST
        2, 3 -> CompressionLevel.FASTER
        4 -> CompressionLevel.FAST
        5 -> CompressionLevel.NORMAL
        6 -> CompressionLevel.HIGHER
        7 -> CompressionLevel.MAXIMUM
        8 -> CompressionLevel.PRE_ULTRA
        else -> CompressionLevel.ULTRA
    }
}
