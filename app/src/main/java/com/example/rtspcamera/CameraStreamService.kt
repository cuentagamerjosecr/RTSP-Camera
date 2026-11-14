package com.example.rtspcamera

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.rtspserver.RtspServerCamera2

class CameraStreamService : Service(), ConnectChecker {
    private var camera: RtspServerCamera2? = null
    private var audioEnabled = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopStreaming()
            ACTION_START -> startStreaming(intent)
        }
        return START_NOT_STICKY
    }

    private fun startStreaming(intent: Intent) {
        if (camera != null) return

        val width = intent.getIntExtra(EXTRA_WIDTH, 1280)
        val height = intent.getIntExtra(EXTRA_HEIGHT, 720)
        val fps = intent.getIntExtra(EXTRA_FPS, 30)
        val bitrate = intent.getIntExtra(EXTRA_BITRATE, 1_500_000)
        val front = intent.getBooleanExtra(EXTRA_FRONT, false)
        audioEnabled = intent.getBooleanExtra(EXTRA_AUDIO, false)

        createNotificationChannel()
        val notification = buildNotification("Preparando cámara…")
        val serviceType = if (Build.VERSION.SDK_INT >= 29) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                (if (audioEnabled) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
        } else 0

        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)

        try {
            val rtsp = RtspServerCamera2(this, this, PORT)
            val facing = if (front) CameraHelper.Facing.FRONT else CameraHelper.Facing.BACK
            val rotation = CameraHelper.getCameraOrientation(this)
            // En modo Context (sin vista previa), startPreview solo selecciona la cámara.
            rtsp.startPreview(facing, width, height, rotation)
            val videoReady = rtsp.prepareVideo(width, height, fps, bitrate, 2, rotation)
            if (!videoReady) {
                throw IllegalStateException("El codificador H.264 no admite ${width}x${height} a ${fps} FPS.")
            }
            if (audioEnabled && !rtsp.prepareAudio(64 * 1024, 32000, false)) {
                Log.w(TAG, "No se pudo preparar el audio; se continuará sin micrófono.")
            }
            camera = rtsp
            rtsp.startStream()
            updateNotification("Servidor activo en puerto $PORT")
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo iniciar RTSP", e)
            updateNotification("Error: ${e.message ?: "no se pudo iniciar"}")
            stopStreaming()
        }
    }

    private fun stopStreaming() {
        try {
            camera?.stopStream()
            camera = null
        } catch (e: Exception) {
            Log.e(TAG, "Error al detener cámara", e)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Transmisión RTSP", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, CameraStreamService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("RTSP Camera")
            .setContentText(message)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Detener", stopIntent)
            .build()
    }

    private fun updateNotification(message: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(message))
    }

    override fun onDestroy() {
        try { camera?.stopStream() } catch (_: Exception) { }
        camera = null
        super.onDestroy()
    }

    override fun onConnectionStarted(url: String) { Log.d(TAG, "Servidor iniciado: $url") }
    override fun onConnectionSuccess() { Log.d(TAG, "Cliente RTSP conectado") }
    override fun onConnectionFailed(reason: String) { Log.e(TAG, "RTSP: $reason") }
    override fun onNewBitrate(bitrate: Long) { Log.d(TAG, "Bitrate: $bitrate") }
    override fun onDisconnect() { Log.d(TAG, "Cliente RTSP desconectado") }
    override fun onAuthError() { Log.e(TAG, "Error de autenticación RTSP") }
    override fun onAuthSuccess() { Log.d(TAG, "Autenticación RTSP correcta") }

    companion object {
        const val ACTION_START = "com.example.rtspcamera.START"
        const val ACTION_STOP = "com.example.rtspcamera.STOP"
        const val EXTRA_WIDTH = "width"
        const val EXTRA_HEIGHT = "height"
        const val EXTRA_FPS = "fps"
        const val EXTRA_BITRATE = "bitrate"
        const val EXTRA_FRONT = "front"
        const val EXTRA_AUDIO = "audio"
        private const val TAG = "CameraStreamService"
        private const val PORT = 8554
        private const val CHANNEL_ID = "rtsp_stream"
        private const val NOTIFICATION_ID = 8554
    }
}
