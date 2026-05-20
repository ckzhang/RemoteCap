package com.ckzhang.remotecap

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

class WatchMessageListenerService : WearableListenerService() {
    private val TAG = "WatchListener"

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path
        val sourceNodeId = messageEvent.sourceNodeId
        Log.i(TAG, "收到來自手錶的訊息: $path")
        
        TargetManager.init(applicationContext)

        when (path) {
            "/shutter" -> {
                Log.i(TAG, "觸發拍照點擊!")
                val intent = Intent(this, ShutterAccessibilityService::class.java)
                intent.action = "ACTION_CLICK_SHUTTER"
                startService(intent)
            }
            "/wake_shutter_only" -> {
                Log.i(TAG, "Watch mode: Shutter Only. Waking up background services.")
                val intent = Intent(this, FloatingTargetService::class.java)
                startService(intent)
            }
            "/wake_preview" -> {
                Log.i(TAG, "Watch mode: With Preview. Launching MainActivity to request MediaProjection.")
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent)
            }
            "/get_countdown" -> {
                Log.i(TAG, "Watch requested countdown config.")
                val currentSec = TargetManager.countdownSec
                Wearable.getMessageClient(this).sendMessage(sourceNodeId, "/set_countdown/$currentSec", byteArrayOf())
            }
        }
    }
}
