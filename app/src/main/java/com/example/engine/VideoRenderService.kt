package com.example.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.JsonUtil
import com.example.data.models.AspectRatio
import com.example.data.models.ExportConfig
import com.example.data.models.ExportResolution
import com.example.data.models.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

data class RenderServiceState(
    val isRendering: Boolean = false,
    val progressPercent: Int = 0,
    val currentLog: String = "",
    val logsHistory: List<String> = emptyList(),
    val outputFile: File? = null,
    val errorMessage: String? = null
)

class VideoRenderService : Service() {

    private val binder = LocalBinder()
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var encoderEngine: VideoEncoderEngine? = null

    inner class LocalBinder : Binder() {
        fun getService(): VideoRenderService = this@VideoRenderService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    fun startRendering(
        projectId: String,
        mediaItems: List<MediaItem>,
        selectedTransitions: List<String>,
        exportConfig: ExportConfig
    ) {
        if (_renderState.value.isRendering) return

        _renderState.value = RenderServiceState(
            isRendering = true,
            progressPercent = 0,
            currentLog = "Iniciando Serviço de Renderização...",
            logsHistory = listOf("[SYSTEM] Serviço de renderização em primeiro plano iniciado.")
        )

        startForeground(NOTIFICATION_ID, buildNotification(0, "Iniciando renderização de vídeo..."))

        serviceScope.launch(Dispatchers.IO) {
            try {
                encoderEngine = VideoEncoderEngine(
                    context = applicationContext,
                    mediaItems = mediaItems,
                    selectedTransitions = selectedTransitions,
                    exportConfig = exportConfig,
                    onProgress = { percent, logMsg ->
                        val updatedLogs = _renderState.value.logsHistory.toMutableList().apply {
                            add(logMsg)
                            if (size > 80) removeAt(0)
                        }
                        _renderState.value = _renderState.value.copy(
                            progressPercent = percent,
                            currentLog = logMsg,
                            logsHistory = updatedLogs
                        )
                        updateNotification(percent, logMsg)
                    }
                )

                val renderedFile = encoderEngine!!.encodeVideo()

                // Update Room database with rendered video path
                val dao = AppDatabase.getDatabase(applicationContext).projectDao()
                val project = dao.getProjectById(projectId)
                if (project != null) {
                    dao.insertOrUpdateProject(
                        project.copy(
                            renderedVideoPath = renderedFile.absolutePath,
                            thumbnailUri = mediaItems.firstOrNull()?.uri,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }

                _renderState.value = _renderState.value.copy(
                    isRendering = false,
                    progressPercent = 100,
                    outputFile = renderedFile,
                    currentLog = "Renderização concluída com sucesso!"
                )

                updateNotificationFinished(renderedFile)

            } catch (e: Exception) {
                e.printStackTrace()
                _renderState.value = _renderState.value.copy(
                    isRendering = false,
                    errorMessage = e.message ?: "Erro desconhecido durante a renderização",
                    currentLog = "Falha na renderização: ${e.message}"
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }
    }

    fun cancelRendering() {
        encoderEngine?.cancel()
        _renderState.value = _renderState.value.copy(
            isRendering = false,
            currentLog = "Renderização cancelada."
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Processamento de Vídeo em Segundo Plano",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação de progresso da exportação de vídeos"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(progress: Int, contentText: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Renderizando Vídeo ($progress%)")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_slideshow)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun updateNotification(progress: Int, logMsg: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(progress, logMsg))
    }

    private fun updateNotificationFinished(file: File) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Renderização Concluída!")
            .setContentText("Vídeo exportado para: ${file.name}")
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID + 1, notif)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    companion object {
        const val CHANNEL_ID = "motion_render_channel"
        const val NOTIFICATION_ID = 2026

        private val _renderState = MutableStateFlow(RenderServiceState())
        val renderState: StateFlow<RenderServiceState> = _renderState
    }
}
