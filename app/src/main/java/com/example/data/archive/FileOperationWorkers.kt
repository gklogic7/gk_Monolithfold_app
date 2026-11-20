package com.example.data.archive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.model.BackgroundJobInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID

object JobKeys {
    const val CHANNEL_ID = "monolith_file_jobs"
    const val WORK_TAG = "monolith_file_job"
    const val KEY_TITLE = "title"
    const val KEY_PROGRESS = "progress"
    const val KEY_DETAIL = "detail"
    const val KEY_ERROR = "error"
    const val KEY_SOURCES = "sources"
    const val KEY_DESTINATION = "destination"
    const val KEY_COMPRESSION_LEVEL = "compression_level"
    const val KEY_PASSWORD = "password"
    const val KEY_ARCHIVE_PATH = "archive_path"
    const val KEY_MOVE_MODE = "move_mode"
}

private fun Context.createJobForegroundInfo(
    notificationId: Int,
    title: String,
    detail: String,
    progress: Int
): ForegroundInfo {
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            JobKeys.CHANNEL_ID,
            "File & Archive Operations",
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)
    }

    val notification = NotificationCompat.Builder(this, JobKeys.CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(title)
        .setContentText(detail.ifBlank { "$progress%" })
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setProgress(100, progress.coerceIn(0, 100), false)
        .build()

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
        ForegroundInfo(notificationId, notification)
    }
}

class ArchiveCompressWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sourcePaths = inputData.getStringArray(JobKeys.KEY_SOURCES)?.toList().orEmpty()
        val outputPath = inputData.getString(JobKeys.KEY_DESTINATION) ?: return Result.failure()
        val level = inputData.getInt(JobKeys.KEY_COMPRESSION_LEVEL, 5)
        val password = inputData.getString(JobKeys.KEY_PASSWORD)
        val outputFile = File(outputPath)
        val title = "Compressing ${outputFile.name}"
        val notifId = id.hashCode()

        return runCatching {
            runCatching { setForeground(applicationContext.createJobForegroundInfo(notifId, title, "Starting…", 0)) }
            setProgress(workDataOf(JobKeys.KEY_TITLE to title, JobKeys.KEY_PROGRESS to 0, JobKeys.KEY_DETAIL to "Preparing"))

            val files = sourcePaths.map { File(it) }.filter { it.exists() }
            ArchiveEngine.compressToZip(
                sources = files,
                outputZip = outputFile,
                compressionLevel = level,
                password = password
            ) { pct, current ->
                setProgressAsync(
                    workDataOf(
                        JobKeys.KEY_TITLE to title,
                        JobKeys.KEY_PROGRESS to pct,
                        JobKeys.KEY_DETAIL to current
                    )
                )
            }

            Result.success(
                workDataOf(
                    JobKeys.KEY_TITLE to title,
                    JobKeys.KEY_PROGRESS to 100,
                    JobKeys.KEY_DETAIL to "Created ${outputFile.name}"
                )
            )
        }.getOrElse { err ->
            Result.failure(
                workDataOf(
                    JobKeys.KEY_TITLE to title,
                    JobKeys.KEY_ERROR to (err.message ?: "Compression failed")
                )
            )
        }
    }
}

class ArchiveExtractWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val archivePath = inputData.getString(JobKeys.KEY_ARCHIVE_PATH) ?: return Result.failure()
        val destPath = inputData.getString(JobKeys.KEY_DESTINATION) ?: return Result.failure()
        val password = inputData.getString(JobKeys.KEY_PASSWORD)
        val archiveFile = File(archivePath)
        val destDir = File(destPath)
        val title = "Extracting ${archiveFile.name}"
        val notifId = id.hashCode()

        return runCatching {
            runCatching { setForeground(applicationContext.createJobForegroundInfo(notifId, title, "Reading archive…", 0)) }
            setProgress(workDataOf(JobKeys.KEY_TITLE to title, JobKeys.KEY_PROGRESS to 0, JobKeys.KEY_DETAIL to "Unpacking"))

            ArchiveEngine.extractArchive(
                archiveFile = archiveFile,
                destDir = destDir,
                password = password
            ) { pct, current ->
                setProgressAsync(
                    workDataOf(
                        JobKeys.KEY_TITLE to title,
                        JobKeys.KEY_PROGRESS to pct,
                        JobKeys.KEY_DETAIL to current
                    )
                )
            }

            Result.success(
                workDataOf(
                    JobKeys.KEY_TITLE to title,
                    JobKeys.KEY_PROGRESS to 100,
                    JobKeys.KEY_DETAIL to "Extracted to ${destDir.name}"
                )
            )
        }.getOrElse { err ->
            Result.failure(
                workDataOf(
                    JobKeys.KEY_TITLE to title,
                    JobKeys.KEY_ERROR to (err.message ?: "Extraction failed")
                )
            )
        }
    }
}

class BatchFileTransferWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sourcePaths = inputData.getStringArray(JobKeys.KEY_SOURCES)?.toList().orEmpty()
        val destDirPath = inputData.getString(JobKeys.KEY_DESTINATION) ?: return Result.failure()
        val moveMode = inputData.getBoolean(JobKeys.KEY_MOVE_MODE, false)
        val destDir = File(destDirPath)
        val verb = if (moveMode) "Moving" else "Copying"
        val title = "$verb ${sourcePaths.size} item(s)"
        val notifId = id.hashCode()

        return runCatching {
            runCatching { setForeground(applicationContext.createJobForegroundInfo(notifId, title, "Preparing…", 0)) }
            val total = sourcePaths.size.coerceAtLeast(1)
            sourcePaths.forEachIndexed { idx, path ->
                val src = File(path)
                if (!src.exists()) return@forEachIndexed
                setProgress(
                    workDataOf(
                        JobKeys.KEY_TITLE to title,
                        JobKeys.KEY_PROGRESS to ((idx * 100) / total),
                        JobKeys.KEY_DETAIL to src.name
                    )
                )
                val target = resolveUniqueTarget(destDir, src.name)
                if (moveMode && src.renameTo(target)) {
                    // Instant atomic move on same filesystem
                } else {
                    if (src.isDirectory) {
                        src.copyRecursively(target, overwrite = true)
                    } else {
                        src.copyTo(target, overwrite = true)
                    }
                    if (moveMode) src.deleteRecursively()
                }
            }
            Result.success(
                workDataOf(
                    JobKeys.KEY_TITLE to title,
                    JobKeys.KEY_PROGRESS to 100,
                    JobKeys.KEY_DETAIL to "Completed in ${destDir.name}"
                )
            )
        }.getOrElse { err ->
            Result.failure(
                workDataOf(
                    JobKeys.KEY_TITLE to title,
                    JobKeys.KEY_ERROR to (err.message ?: "Transfer failed")
                )
            )
        }
    }

    private fun resolveUniqueTarget(destDir: File, desiredName: String): File {
        var candidate = File(destDir, desiredName)
        if (!candidate.exists()) return candidate
        val base = desiredName.substringBeforeLast('.', desiredName)
        val ext = desiredName.substringAfterLast('.', "")
        var i = 1
        while (candidate.exists()) {
            val next = if (ext.isNotEmpty() && base != desiredName) "$base ($i).$ext" else "$desiredName ($i)"
            candidate = File(destDir, next)
            i++
        }
        return candidate
    }
}

class FileJobScheduler(private val context: Context) {
    private val workManager: WorkManager = WorkManager.getInstance(context)

    fun observeJobs(): Flow<List<BackgroundJobInfo>> =
        workManager.getWorkInfosByTagFlow(JobKeys.WORK_TAG).map { infos ->
            infos.sortedByDescending { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
                .take(15)
                .map { info ->
                    val data: Data = if (info.state.isFinished) info.outputData else info.progress
                    val title = data.getString(JobKeys.KEY_TITLE) ?: "File Operation"
                    val detail = data.getString(JobKeys.KEY_ERROR)
                        ?: data.getString(JobKeys.KEY_DETAIL)
                        ?: info.state.name.lowercase().replaceFirstChar { it.uppercase() }
                    val progress = when (info.state) {
                        WorkInfo.State.SUCCEEDED -> 100
                        WorkInfo.State.RUNNING -> data.getInt(JobKeys.KEY_PROGRESS, 0)
                        else -> 0
                    }
                    BackgroundJobInfo(
                        id = info.id.toString(),
                        title = title,
                        status = info.state.name,
                        progressPercent = progress,
                        isRunning = info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED,
                        isFailed = info.state == WorkInfo.State.FAILED,
                        detail = detail
                    )
                }
        }

    fun enqueueCompress(
        sourcePaths: List<String>,
        outputZipPath: String,
        compressionLevel: Int,
        password: String?
    ): UUID {
        val request = OneTimeWorkRequestBuilder<ArchiveCompressWorker>()
            .addTag(JobKeys.WORK_TAG)
            .setInputData(
                workDataOf(
                    JobKeys.KEY_TITLE to "Compressing ${File(outputZipPath).name}",
                    JobKeys.KEY_SOURCES to sourcePaths.toTypedArray(),
                    JobKeys.KEY_DESTINATION to outputZipPath,
                    JobKeys.KEY_COMPRESSION_LEVEL to compressionLevel,
                    JobKeys.KEY_PASSWORD to password
                )
            )
            .build()
        workManager.enqueue(request)
        return request.id
    }

    fun enqueueExtract(
        archivePath: String,
        destDirPath: String,
        password: String?
    ): UUID {
        val request = OneTimeWorkRequestBuilder<ArchiveExtractWorker>()
            .addTag(JobKeys.WORK_TAG)
            .setInputData(
                workDataOf(
                    JobKeys.KEY_TITLE to "Extracting ${File(archivePath).name}",
                    JobKeys.KEY_ARCHIVE_PATH to archivePath,
                    JobKeys.KEY_DESTINATION to destDirPath,
                    JobKeys.KEY_PASSWORD to password
                )
            )
            .build()
        workManager.enqueue(request)
        return request.id
    }

    fun enqueueTransfer(
        sourcePaths: List<String>,
        destDirPath: String,
        moveMode: Boolean
    ): UUID {
        val verb = if (moveMode) "Moving" else "Copying"
        val request = OneTimeWorkRequestBuilder<BatchFileTransferWorker>()
            .addTag(JobKeys.WORK_TAG)
            .setInputData(
                workDataOf(
                    JobKeys.KEY_TITLE to "$verb ${sourcePaths.size} item(s)",
                    JobKeys.KEY_SOURCES to sourcePaths.toTypedArray(),
                    JobKeys.KEY_DESTINATION to destDirPath,
                    JobKeys.KEY_MOVE_MODE to moveMode
                )
            )
            .build()
        workManager.enqueue(request)
        return request.id
    }

    fun cancelJob(id: String) {
        runCatching { workManager.cancelWorkById(UUID.fromString(id)) }
    }

    fun pruneFinishedJobs() {
        workManager.pruneWork()
    }
}
