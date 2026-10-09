package com.roadsearch.openeditvideo.export

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.roadsearch.openeditvideo.R
import com.roadsearch.openeditvideo.data.EditorStateCodec
import com.roadsearch.openeditvideo.data.ProjectRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import com.roadsearch.openeditvideo.core.TimelineValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class VideoExportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: ProjectRepository,
    private val publisher: MediaStorePublisher,
    private val exporter: VideoExporter,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            setForeground(createForegroundInfo(0))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (refused: Exception) {
            // Android 12+ can refuse a foreground service started from the background: export without the notification.
        }

        val project = repository.get(inputData.getString(ExportKeys.PROJECT_ID) ?: ProjectRepository.DEFAULT_PROJECT_ID)
            ?: return@withContext Result.failure(workDataOf(ExportKeys.ERROR to "Projet introuvable"))
        val state = runCatching { TimelineValidator.repair(EditorStateCodec.decode(project.documentJson)) }
            .getOrElse { error ->
                return@withContext Result.failure(
                    workDataOf(ExportKeys.ERROR to "Projet illisible: ${error.message ?: "JSON invalide"}")
                )
            }

        val capabilityErrors = ExportCapabilityAnalyzer.errors(state)
        val validation = MediaSourceValidator.validate(applicationContext, state.clips, state.audioClips)
        if (capabilityErrors.isNotEmpty() || !validation.isValid) {
            return@withContext Result.failure(
                workDataOf(
                    ExportKeys.ERROR to (capabilityErrors + validation.errors).distinct().joinToString(" ")
                )
            )
        }

        val output = applicationContext
            .let { java.io.File(it.cacheDir, "openedit_export_${System.currentTimeMillis()}.mp4") }
        try {
            setProgress(workDataOf(ExportKeys.PROGRESS to 0))
            val settings = ExportSettings(
                resolution = runCatching { ExportResolution.valueOf(inputData.getString(ExportKeys.RESOLUTION) ?: "") }.getOrDefault(ExportResolution.FHD),
                fps = inputData.getInt(ExportKeys.FPS, 30),
                highQuality = inputData.getBoolean(ExportKeys.HIGH_QUALITY, true),
            )
            exporter.export(state, output, settings) { progress ->
                val percent = (progress * 100f).toInt().coerceIn(0, 100)
                // setProgressAsync is safe from the exporter callback without creating a detached scope.
                setProgressAsync(workDataOf(ExportKeys.PROGRESS to percent))
            }

            val uri = publisher.publishVideo(
                source = output,
                displayName = ExportFileName.normalize(
                    inputData.getString(ExportKeys.OUTPUT_NAME)
                        ?: "OpenEditVideo_${System.currentTimeMillis()}"
                ),
            )
            setProgress(workDataOf(ExportKeys.PROGRESS to 100, ExportKeys.OUTPUT_URI to uri.toString()))
            Result.success(workDataOf(ExportKeys.PROGRESS to 100, ExportKeys.OUTPUT_URI to uri.toString()))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            Result.failure(workDataOf(ExportKeys.ERROR to (t.message ?: t.javaClass.simpleName)))
        } finally {
            output.delete()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo(0)

    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        val channelId = "video_export"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Export vidéo", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val bounded = progress.coerceIn(0, 100)
        val cancelPendingIntent = androidx.work.WorkManager
            .getInstance(applicationContext)
            .createCancelPendingIntent(id)
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_export)
            .setContentTitle("MotionFlow")
            .setContentText("Export vidéo… $bounded%")
            .setOngoing(bounded < 100)
            .setOnlyAlertOnce(true)
            .setProgress(100, bounded, bounded == 0)
            .apply {
                if (bounded < 100) addAction(NotificationCompat.Action(0, "Annuler", cancelPendingIntent))
            }
            .build()
        val type = if (Build.VERSION.SDK_INT >= 35) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        return ForegroundInfo(1001, notification, type)
    }
}
