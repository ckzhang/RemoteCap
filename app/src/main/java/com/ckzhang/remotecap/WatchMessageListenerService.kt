package com.ckzhang.remotecap

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class WatchMessageListenerService : WearableListenerService() {
    private val TAG = "WatchListener"

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path
        Log.d(TAG, "收到來自手錶的訊息: $path")
        
        TargetManager.init(applicationContext)

        when (path) {
            "/shutter" -> {
                Log.d(TAG, "觸發拍照點擊!")
                val intent = Intent(this, ShutterAccessibilityService::class.java)
                intent.action = "ACTION_CLICK_SHUTTER"
                startService(intent)
            }
            "/wake_shutter_only" -> {
                Log.d(TAG, "Watch mode: Shutter Only. Waking up background services.")
                // Start floating target service silently
                val intent = Intent(this, FloatingTargetService::class.java)
                startService(intent)
            }
            "/wake_preview" -> {
                Log.d(TAG, "Watch mode: With Preview. Launching MainActivity to request MediaProjection.")
                // Activity is required for MediaProjection prompt
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent)
            }
            "/flashlight_tick" -> {
                Log.d(TAG, "Watch countdown tick, flashing torch.")
                flashTorch()
            }
        }
    }

    private fun flashTorch() {
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0] // Assume first camera has flash
            cameraManager.setTorchMode(cameraId, true)
            
            // Turn off after 100ms
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    cameraManager.setTorchMode(cameraId, false)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to turn off torch", e)
                }
            }, 100)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to use flashlight", e)
        }
    }
}
