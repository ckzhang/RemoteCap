package com.ckzhang.remotecap

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.Wearable
import java.io.ByteArrayOutputStream

class ScreenCaptureService : Service() {
    private val TAG = "ScreenCapture"
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var isStreaming = false
    private var lastSendTime = 0L
    private var handlerThread: HandlerThread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "START_CAPTURE") {
            val resultCode = intent.getIntExtra("RESULT_CODE", 0)
            val data = intent.getParcelableExtra<Intent>("RESULT_DATA")
            if (data != null) {
                startForegroundNotification()
                Handler(Looper.getMainLooper()).postDelayed({
                    startCapture(resultCode, data)
                }, 500)
            }
        } else if (action == "STOP_CAPTURE") {
            stopCapture()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        Log.d(TAG, "呼叫 startForeground...")
        val channelId = "capture_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Screen Capture", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Remote Cap Live View")
            .setContentText("Streaming to watch...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                Log.d(TAG, "startForeground (Android Q+) 呼叫成功")
            } catch (e: Exception) {
                Log.e(TAG, "startForeground 崩潰了!", e)
            }
        } else {
            startForeground(1, notification)
        }
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {}, null)

        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val density = displayMetrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        handlerThread = HandlerThread("ImageReaderThread").apply { start() }
        val handler = Handler(handlerThread!!.looper)

        imageReader?.setOnImageAvailableListener({ reader ->
            if (!isStreaming) return@setOnImageAvailableListener
            
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastSendTime < 100) {
                reader.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }
            
            var image: Image? = null
            try {
                image = reader.acquireLatestImage()
                if (image != null) {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width
                    
                    val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                    bitmap.copyPixelsFromBuffer(buffer)
                    
                    val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                    val scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, 360, 360, true)
                    sendBitmapToWatch(scaledBitmap)
                    
                    scaledBitmap.recycle()
                    croppedBitmap.recycle()
                    bitmap.recycle()
                    lastSendTime = currentTime
                }
            } catch (e: Exception) {
                Log.e(TAG, "Process image failed", e)
            } finally {
                image?.close()
            }
        }, handler)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
            imageReader?.surface, null, null
        )
        isStreaming = true
    }

    private fun sendBitmapToWatch(bitmap: Bitmap) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, stream)
        val bytes = stream.toByteArray()

        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            val messageClient = Wearable.getMessageClient(this)
            for (node in nodes) {
                messageClient.sendMessage(node.id, "/preview", bytes)
            }
        }
    }

    private fun stopCapture() {
        isStreaming = false
        virtualDisplay?.release()
        imageReader?.close()
        handlerThread?.quitSafely()
        mediaProjection?.stop()
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }
}



