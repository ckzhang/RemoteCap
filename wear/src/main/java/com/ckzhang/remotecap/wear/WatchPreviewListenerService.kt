package com.ckzhang.remotecap.wear

import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.InputStream
import java.nio.ByteBuffer

class WatchPreviewListenerService : WearableListenerService() {
    private val TAG = "WatchPreviewListener"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    
    // FPS Logging Stats
    private var statsStartTime = 0L
    private var statsFramesReceived = 0
    private var statsTotalBytesReceived = 0L

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/photo") {
            val intent = Intent("ACTION_PREVIEW_FRAME")
            intent.putExtra("image_data", messageEvent.data)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        } else if (messageEvent.path == "/shutter_done") {
            val intent = Intent("ACTION_SHUTTER_DONE")
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }
    }

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (channel.path == "/preview_channel") {
            Log.i(TAG, "ChannelStream opened!")
            scope.launch {
                try {
                    val channelClient = Wearable.getChannelClient(this@WatchPreviewListenerService)
                    val inputStream = channelClient.getInputStream(channel).await()
                    readStream(inputStream)
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading from ChannelStream", e)
                }
            }
        }
    }
    
    private fun readStream(inputStream: InputStream) {
        statsStartTime = System.currentTimeMillis()
        
        try {
            val lengthBuffer = ByteArray(4)
            while (true) {
                // Read 4 bytes for length prefix
                var bytesRead = 0
                while (bytesRead < 4) {
                    val read = inputStream.read(lengthBuffer, bytesRead, 4 - bytesRead)
                    if (read == -1) return // EOF
                    bytesRead += read
                }
                
                val frameLength = ByteBuffer.wrap(lengthBuffer).int
                if (frameLength <= 0 || frameLength > 5 * 1024 * 1024) {
                    Log.w(TAG, "Invalid frame length: $frameLength")
                    continue
                }
                
                // Read frame data
                val frameData = ByteArray(frameLength)
                bytesRead = 0
                while (bytesRead < frameLength) {
                    val read = inputStream.read(frameData, bytesRead, frameLength - bytesRead)
                    if (read == -1) return // EOF
                    bytesRead += read
                }
                
                statsFramesReceived++
                statsTotalBytesReceived += frameLength
                
                // Send broadcast to update UI
                val intent = Intent("ACTION_PREVIEW_FRAME")
                intent.putExtra("image_data", frameData)
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                
                val now = System.currentTimeMillis()
                if (now - statsStartTime >= 1000) {
                    val kbPerSec = statsTotalBytesReceived / 1024
                    Log.d(TAG, "WatchStats [1s Window]: Received=$statsFramesReceived FPS, RX=${kbPerSec} KB/s")
                    
                    statsStartTime = now
                    statsFramesReceived = 0
                    statsTotalBytesReceived = 0
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stream disconnected or error", e)
        }
    }
}
