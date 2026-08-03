package com.example.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class RenderingForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "video_editor_rendering_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_RENDER = "ACTION_START_RENDER"
        const val ACTION_STOP_RENDER = "ACTION_STOP_RENDER"
        const val EXTRA_PROGRESS = "EXTRA_PROGRESS"
        const val EXTRA_STATUS = "EXTRA_STATUS"

        fun startService(context: Context, status: String, progress: Int) {
            try {
                val intent = Intent(context, RenderingForegroundService::class.java).apply {
                    action = ACTION_START_RENDER
                    putExtra(EXTRA_STATUS, status)
                    putExtra(EXTRA_PROGRESS, progress)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("RenderingService", "Failed to start foreground service", e)
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, RenderingForegroundService::class.java).apply {
                    action = ACTION_STOP_RENDER
                }
                context.stopService(intent)
            } catch (e: Exception) {
                Log.e("RenderingService", "Failed to stop foreground service", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_RENDER) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
            return START_NOT_STICKY
        }

        val status = intent?.getStringExtra(EXTRA_STATUS) ?: "Renderizando vídeo..."
        val progress = intent?.getIntExtra(EXTRA_PROGRESS, 0) ?: 0

        val notification = buildNotification(status, progress)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("RenderingService", "startForeground failed", e)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Renderização de Vídeo",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação de progresso da renderização e exportação de vídeo"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String, progress: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Editor de Vídeo Automático")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
