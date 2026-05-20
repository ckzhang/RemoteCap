package com.ckzhang.remotecap.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.Wearable

class MainActivity : ComponentActivity() {
    private val TAG = "WearShutter"
    
    // UI Elements
    private lateinit var mainLayout: FrameLayout
    private lateinit var promptLayout: LinearLayout
    private lateinit var cameraLayout: FrameLayout
    private lateinit var ivPreview: ImageView
    private lateinit var tvStatus: TextView
    private lateinit var tvCountdown: TextView

    private val previewReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val bytes = intent?.getByteArrayExtra("image_data")
            if (bytes != null) {
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ivPreview.setImageBitmap(bitmap)
            }
        }
    }
    
    private val shutterDoneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "收到拍照完成通知，執行雙重震動")
            vibrateSuccess()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        mainLayout = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        
        // --- 1. Prompt Layout (Connection Status & Mode Selection) ---
        promptLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 16)
        }
        
        tvStatus = TextView(this).apply {
            text = "連線中..."
            textSize = 12f
            setTextColor(android.graphics.Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        
        val btnShutterOnly = Button(this).apply {
            text = "Shutter Only (N)"
            textSize = 14f
            setOnClickListener { startCameraMode(false) }
        }
        
        val btnWithPreview = Button(this).apply {
            text = "With Preview (Y)"
            textSize = 14f
            setOnClickListener { startCameraMode(true) }
        }
        
        promptLayout.addView(tvStatus)
        promptLayout.addView(btnShutterOnly)
        promptLayout.addView(btnWithPreview)
        
        // --- 2. Camera Layout (Preview & Shutter Button) ---
        cameraLayout = FrameLayout(this).apply {
            visibility = View.GONE
        }
        
        ivPreview = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        
        tvCountdown = TextView(this).apply {
            text = ""
            textSize = 64f
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setShadowLayer(5f, 0f, 0f, android.graphics.Color.BLACK)
            visibility = View.GONE
        }
        
        val btnShutter = Button(this).apply {
            text = "📸"
            textSize = 24f
            layoutParams = FrameLayout.LayoutParams(
                120, 120, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = 20 }
            setOnClickListener { startCountdown(3) }
        }
        
        cameraLayout.addView(ivPreview)
        cameraLayout.addView(tvCountdown)
        cameraLayout.addView(btnShutter)
        
        // Add to main layout
        mainLayout.addView(cameraLayout)
        mainLayout.addView(promptLayout)
        
        setContentView(mainLayout)

        // Register Receivers
        LocalBroadcastManager.getInstance(this).apply {
            registerReceiver(previewReceiver, IntentFilter("ACTION_PREVIEW_FRAME"))
            registerReceiver(shutterDoneReceiver, IntentFilter("ACTION_SHUTTER_DONE"))
        }
        
        checkConnectionStatus()
    }
    
    private fun checkConnectionStatus() {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            val hasNode = nodes.isNotEmpty()
            Wearable.getCapabilityClient(this)
                .getCapability("remote_cap_phone_app", com.google.android.gms.wearable.CapabilityClient.FILTER_REACHABLE)
                .addOnSuccessListener { capabilityInfo ->
                    val hasApp = capabilityInfo.nodes.isNotEmpty()
                    
                    val nodeStatus = if (hasNode) "✅ OS" else "❌ OS"
                    val appStatus = if (hasApp) "✅ App" else "❌ App"
                    
                    runOnUiThread {
                        tvStatus.text = "Phone Connection:\n$nodeStatus | $appStatus"
                    }
                }
        }
    }
    
    private fun startCameraMode(withPreview: Boolean) {
        promptLayout.visibility = View.GONE
        cameraLayout.visibility = View.VISIBLE
        
        // Send wake signal to phone
        val path = if (withPreview) "/wake_preview" else "/wake_shutter_only"
        
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            val messageClient = Wearable.getMessageClient(this)
            for (node in nodes) {
                messageClient.sendMessage(node.id, path, byteArrayOf())
            }
        }
    }

    private fun startCountdown(seconds: Int) {
        tvCountdown.visibility = View.VISIBLE
        
        object : CountDownTimer((seconds * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secLeft = (millisUntilFinished / 1000) + 1
                tvCountdown.text = secLeft.toString()
                
                // Signal phone to flash torch
                sendSignalToPhone("/flashlight_tick")
                
                vibrateTrigger()
            }

            override fun onFinish() {
                tvCountdown.text = ""
                tvCountdown.visibility = View.GONE
                sendShutterMessage()
            }
        }.start()
    }
    
    private fun sendSignalToPhone(path: String) {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            val messageClient = Wearable.getMessageClient(this)
            for (node in nodes) {
                messageClient.sendMessage(node.id, path, byteArrayOf())
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).apply {
            unregisterReceiver(previewReceiver)
            unregisterReceiver(shutterDoneReceiver)
        }
    }
    
    private fun vibrateTrigger() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(50)
        }
    }
    
    private fun vibrateSuccess() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 100, 50, 100)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
        } else {
            vibrator.vibrate(longArrayOf(0, 100, 50, 100), -1)
        }
    }

    private fun sendShutterMessage() {
        sendSignalToPhone("/shutter")
    }
}
