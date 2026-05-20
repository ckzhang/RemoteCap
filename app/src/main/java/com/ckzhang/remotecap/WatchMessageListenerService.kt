package com.ckzhang.remotecap

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

class WatchMessageListenerService : WearableListenerService() {
    private val TAG = "WatchListener"

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path
        val sourceNodeId = messageEvent.sourceNodeId
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
                val intent = Intent(this, FloatingTargetService::class.java)
                startService(intent)
            }
            "/wake_preview" -> {
                Log.d(TAG, "Watch mode: With Preview. Launching MainActivity to request MediaProjection.")
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent)
            }
            "/flashlight_tick" -> {
                Log.d(TAG, "Watch countdown tick, flashing torch.")
                flashTorch()
            }
            "/get_countdown" -> {
                Log.d(TAG, "Watch requested countdown config.")
                val currentSec = TargetManager.countdownSec
                Wearable.getMessageClient(this).sendMessage(sourceNodeId, "/set_countdown/$currentSec", byteArrayOf())
            }
        }
    }

    private fun flashTorch() {
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraIdList = cameraManager.cameraIdList
            if (cameraIdList.isEmpty()) {
                Log.e(TAG, "No cameras found!")
                return
            }
            
            // Try to find a back-facing camera with flash
            var targetCameraId = cameraIdList[0] // fallback to first
            for (id in cameraIdList) {
                try {
                    val characteristics = cameraManager.getCameraCharacteristics(id)
                    val hasFlash = characteristics.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    val facing = characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
                    if (hasFlash && facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK) {
                        targetCameraId = id
                        break
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking camera $id", e)
                }
            }

            Log.d(TAG, "Turning on torch for camera $targetCameraId")
            cameraManager.setTorchMode(targetCameraId, true)
            
            // Turn off after 150ms
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    Log.d(TAG, "Turning off torch for camera $targetCameraId")
                    cameraManager.setTorchMode(targetCameraId, false)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to turn off torch", e)
                }
            }, 150)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to use flashlight", e)
        }
    }
}
