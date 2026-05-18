package com.ckzhang.remotecap

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class WatchMessageListenerService : WearableListenerService() {
    private val TAG = "WatchListener"

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path
        Log.d(TAG, "收到來自手錶的訊息: $path")
        
        TargetManager.init(applicationContext)

        if (path == "/shutter") {
            Log.d(TAG, "觸發拍照點擊!")
            val intent = Intent(this, ShutterAccessibilityService::class.java)
            intent.action = "ACTION_CLICK_SHUTTER"
            startService(intent)
        }
    }
}
