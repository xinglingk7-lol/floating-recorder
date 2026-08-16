package com.example.floatingrecorder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenRecordService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
        const val CHANNEL_ID = "recorder_channel"

        var projectionResultCode: Int = 0
        var projectionResultData: Intent? = null
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var isRecording = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun buildNotification() = run {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Gravação de tela", NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gravando tela")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .build()
    }

    private fun startRecording() {
        if (isRecording) return
        val data = projectionResultData ?: run {
            Toast.makeText(this, "Permissão de captura não encontrada. Abra o app novamente.", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        startForeground(1, buildNotification())

        val projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(projectionResultCode, data)

        val metrics = DisplayMetrics()
        windowManagerDefaultDisplay(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val appDir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "FloatingRecorder")
        if (!appDir.exists()) appDir.mkdirs()
        val fileName = "REC_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.mp4"
        currentFile = File(appDir, fileName)

        mediaRecorder = MediaRecorder().apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoSize(width, height)
            setVideoEncodingBitRate(8_000_000)
            setVideoFrameRate(30)
            setOutputFile(currentFile!!.absolutePath)
            prepare()
        }

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "FloatingRecorderDisplay", width, height, density,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder!!.surface, null, null
        )

        mediaRecorder?.start()
        isRecording = true
    }

    private fun windowManagerDefaultDisplay(metrics: DisplayMetrics) {
        val wm = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
    }

    private fun pauseRecording() {
        if (!isRecording) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder?.pause()
        }
    }

    private fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder?.resume()
        }
    }

    private fun stopRecording() {
        if (!isRecording) {
            stopSelf()
            return
        }
        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder?.release()
        virtualDisplay?.release()
        mediaProjection?.stop()
        isRecording = false

        currentFile?.let { saveToDeviceGallery(it) }

        stopForeground(true)
        stopSelf()
    }

    private fun saveToDeviceGallery(file: File) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/FloatingRecorder")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)

        uri?.let {
            resolver.openOutputStream(it)?.use { out ->
                FileInputStream(file).use { input -> input.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(it, values, null, null)
            }
            Toast.makeText(this, "Vídeo salvo na galeria!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
