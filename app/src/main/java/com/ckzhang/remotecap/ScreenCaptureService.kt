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
import com.google.android.gms.wearable.ChannelClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.OutputStream

class ScreenCaptureService : Service() {
    private val TAG = "ScreenCapture"
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var isStreaming = false
    private var transmissionIntervalMs = 250L // Default: 4 FPS
    private var lastSendTime = 0L
    private var handlerThread: HandlerThread? = null
    private var currentQuality = 15
    private var currentScale = 200

    // ChannelClient streaming
    private var channelClient: ChannelClient? = null
    private var activeChannel: ChannelClient.Channel? = null
    private var channelOutputStream: OutputStream? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    
    // FPS & Performance Logging Stats
    private var statsStartTime = 0L
    private var statsFramesProcessed = 0
    private var statsFramesSent = 0
    private var statsTotalProcessTimeMs = 0L
    private var statsTotalBytesSent = 0L
    
    // Bitmap Reuse
    private var reusableBitmap: Bitmap? = null

    companion object {
        var instance: ScreenCaptureService? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

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
            .setContentTitle("AnyCam Live View")
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
        
        channelClient = Wearable.getChannelClient(this)
        setupChannelStream()
        
        statsStartTime = System.currentTimeMillis()

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        handlerThread = HandlerThread("ImageReaderThread").apply { start() }
        val handler = Handler(handlerThread!!.looper)

        imageReader?.setOnImageAvailableListener({ reader ->
            if (!isStreaming) return@setOnImageAvailableListener
            
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastSendTime < transmissionIntervalMs) {
                reader.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }
            
            var image: Image? = null
            val processStartTime = System.currentTimeMillis()
            try {
                image = reader.acquireLatestImage()
                if (image != null) {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width
                    
                    val bitmapWidth = width + rowPadding / pixelStride
                    
                    if (reusableBitmap == null || reusableBitmap!!.width != bitmapWidth || reusableBitmap!!.height != height) {
                        reusableBitmap?.recycle()
                        reusableBitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
                    }
                    
                    reusableBitmap!!.copyPixelsFromBuffer(buffer)
                    
                    val croppedBitmap = Bitmap.createBitmap(reusableBitmap!!, 0, 0, width, height)
                    val scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, currentScale, currentScale, true)
                    
                    val processTime = System.currentTimeMillis() - processStartTime
                    
                    sendBitmapToWatch(scaledBitmap, processTime)
                    
                    scaledBitmap.recycle()
                    croppedBitmap.recycle()
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

    private fun setupChannelStream() {
        scope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@ScreenCaptureService).connectedNodes.await()
                for (node in nodes) {
                    val channel = channelClient?.openChannel(node.id, "/preview_channel")?.await()
                    if (channel != null) {
                        activeChannel = channel
                        channelOutputStream = channelClient?.getOutputStream(channel)?.await()
                        Log.i(TAG, "ChannelStream opened to node: ${node.id}")
                        break // Just connect to the first available watch
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to setup ChannelStream", e)
            }
        }
    }

    private var isSendingFrame = false

    private fun sendBitmapToWatch(bitmap: Bitmap, processTimeMs: Long) {
        if (isSendingFrame) {
            return
        }
        
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality, stream)
        val bytes = stream.toByteArray()
        val dataSize = bytes.size
        
        isSendingFrame = true
        
        scope.launch {
            try {
                // Prepend 4 bytes for length prefix so the receiver knows the frame size
                val lengthPrefix = java.nio.ByteBuffer.allocate(4).putInt(dataSize).array()
                channelOutputStream?.write(lengthPrefix)
                channelOutputStream?.write(bytes)
                channelOutputStream?.flush()
                
                // Logging Stats
                statsFramesSent++
                statsTotalBytesSent += dataSize
                statsTotalProcessTimeMs += processTimeMs
            } catch (e: Exception) {
                Log.e(TAG, "ChannelStream write failed, trying to reconnect", e)
                setupChannelStream() // Try reconnecting
            } finally {
                isSendingFrame = false
            }
        }
        
        statsFramesProcessed++
        val now = System.currentTimeMillis()
        if (now - statsStartTime >= 1000) {
            val kbPerSec = statsTotalBytesSent / 1024
            val avgProcessTime = if (statsFramesProcessed > 0) statsTotalProcessTimeMs / statsFramesProcessed else 0
            Log.d(TAG, "PhoneStats [1s Window]: Captured=$statsFramesProcessed, Sent=$statsFramesSent FPS, Avg Process=${avgProcessTime}ms, TX=${kbPerSec} KB/s")
            
            statsStartTime = now
            statsFramesProcessed = 0
            statsFramesSent = 0
            statsTotalProcessTimeMs = 0
            statsTotalBytesSent = 0
        }
    }

    // Wear Data Layer limits: High FPS or High Res causes massive lag queueing
    // We adjust this dynamically based on watch state.
    fun setStreamingState(active: Boolean) {
        if (!active) {
            transmissionIntervalMs = 5000L // 0.2 FPS (almost paused) when watch is ambient/inactive
            Log.d(TAG, "Watch is ambient/inactive. Reduced stream to 0.2 FPS to save battery.")
        } else {
            transmissionIntervalMs = 250L // 4 FPS normal
            Log.d(TAG, "Watch is active. Restored stream to 4 FPS.")
        }
    }

    fun setDynamicQuality(isHighBandwidth: Boolean) {
        if (isHighBandwidth) {
            transmissionIntervalMs = 66L // ~15 FPS
            currentScale = 400
            currentQuality = 30
            Log.d(TAG, "Network quality: HIGH. Streaming at 15 FPS, 400x400.")
        } else {
            transmissionIntervalMs = 250L // 4 FPS
            currentScale = 200
            currentQuality = 15
            Log.d(TAG, "Network quality: LOW (Bluetooth). Streaming at 4 FPS, 200x200.")
        }
    }

    private fun stopCapture() {
        isStreaming = false
        virtualDisplay?.release()
        imageReader?.close()
        handlerThread?.quitSafely()
        mediaProjection?.stop()
        reusableBitmap?.recycle()
        reusableBitmap = null
        
        try {
            channelOutputStream?.close()
            activeChannel?.let { channelClient?.close(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error closing channel", e)
        }
    }

    override fun onDestroy() {
        stopCapture()
        instance = null
        super.onDestroy()
    }
}



