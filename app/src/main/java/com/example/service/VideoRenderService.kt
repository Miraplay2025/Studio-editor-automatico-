package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.data.db.MediaItemEntity
import com.example.data.model.ExportOptions
import com.example.data.model.RenderLogMessage
import com.example.data.model.RenderProgressState
import com.example.data.model.TransitionType
import com.example.engine.VideoRenderEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class VideoRenderService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var renderJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): VideoRenderService = this@VideoRenderService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    fun startRenderTask(
        mediaItems: List<MediaItemEntity>,
        activeTransitions: List<TransitionType>,
        transitionDurationSec: Float,
        narrationAudioFile: File?,
        exportOptions: ExportOptions
    ) {
        val notification = createNotification("Iniciando CineCut...", 0)
        startForeground(NOTIFICATION_ID, notification)

        _renderState.value = RenderProgressState.Processing(
            progressPercent = 0,
            currentStep = "Inicializando pipeline...",
            logs = listOf(RenderLogMessage(getCurrentTime(), "Serviço Foreground ativado"))
        )

        renderJob?.cancel()
        renderJob = serviceScope.launch {
            val engine = VideoRenderEngine(this@VideoRenderService)
            val currentLogs = mutableListOf<RenderLogMessage>()

            try {
                val result = engine.renderVideo(
                    mediaItems = mediaItems,
                    activeTransitions = activeTransitions,
                    transitionDurationSec = transitionDurationSec,
                    narrationAudioFile = narrationAudioFile,
                    exportOptions = exportOptions,
                    onProgressUpdate = { percent, status, logMsg ->
                        currentLogs.add(RenderLogMessage(getCurrentTime(), logMsg))
                        _renderState.value = RenderProgressState.Processing(
                            progressPercent = percent,
                            currentStep = status,
                            logs = currentLogs.toList()
                        )
                        updateNotification(status, percent)
                    }
                )

                _renderState.value = RenderProgressState.Success(
                    outputFilePath = result.first.absolutePath,
                    outputFileUri = result.second
                )
                updateNotification("Edição concluída com sucesso!", 100)
            } catch (e: Exception) {
                if (e is CancellationException) {
                    _renderState.value = RenderProgressState.Idle
                } else {
                    e.printStackTrace()
                    _renderState.value = RenderProgressState.Error(
                        e.localizedMessage ?: "Erro desconhecido ao renderizar vídeo."
                    )
                }
            } finally {
                stopForeground(STOP_FOREGROUND_DETACH)
            }
        }
    }

    fun cancelRendering() {
        renderJob?.cancel()
        _renderState.value = RenderProgressState.Idle
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun updateNotification(text: String, progress: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(text, progress))
    }

    private fun createNotification(text: String, progress: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CineCut - Renderizando Vídeo")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Renderização de Vídeo CineCut",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação de progresso da edição de vídeo em segundo plano"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "cinecut_render_channel"
        const val NOTIFICATION_ID = 1001

        private val _renderState = MutableStateFlow<RenderProgressState>(RenderProgressState.Idle)
        val renderState: StateFlow<RenderProgressState> = _renderState.asStateFlow()
    }
}
