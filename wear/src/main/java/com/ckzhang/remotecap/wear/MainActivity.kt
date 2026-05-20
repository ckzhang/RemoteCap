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

import androidx.fragment.app.FragmentActivity
import androidx.wear.ambient.AmbientModeSupport

class MainActivity : FragmentActivity(), AmbientModeSupport.AmbientCallbackProvider {
    private val TAG = "WearShutter"
    private lateinit var ambientController: AmbientModeSupport.AmbientController
    
    // UI Elements
    private lateinit var mainLayout: FrameLayout
    private lateinit var promptLayout: LinearLayout
    private lateinit var cameraLayout: FrameLayout
    private lateinit var settingsLayout: LinearLayout
    private lateinit var ivPreview: ImageView
    private lateinit var tvStatus: TextView
    private lateinit var tvCountdown: TextView
    private lateinit var btnShutter: Button
    private lateinit var btnCycleCountdown: Button
    private lateinit var btnToggleHaptic: Button
    private lateinit var btnCameraBack: Button
    
    // Default countdown value until we fetch it from phone
    private var configuredCountdownSec = 3
    private var isHapticEnabled = true

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
            text = "連線中...\n"
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
        
        val btnSettings = Button(this).apply {
            text = "⚙️ Settings"
            textSize = 14f
            setOnClickListener { showSettingsMode(true) }
        }
        
        promptLayout.addView(tvStatus)
        promptLayout.addView(btnShutterOnly)
        promptLayout.addView(btnWithPreview)
        promptLayout.addView(btnSettings)
        
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
        
        btnShutter = Button(this).apply {
            text = "📸 (${configuredCountdownSec}s)"
            textSize = 20f
            layoutParams = FrameLayout.LayoutParams(
                140, 140, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = 20 }
            setOnClickListener { 
                if (configuredCountdownSec > 0) {
                    startCountdown(configuredCountdownSec) 
                } else {
                    sendShutterMessage()
                }
            }
        }
        
        cameraLayout.addView(ivPreview)
        cameraLayout.addView(tvCountdown)
        cameraLayout.addView(btnShutter)

        btnCameraBack = Button(this).apply {
            text = "X"
            textSize = 14f
            layoutParams = FrameLayout.LayoutParams(
                70, 70, Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply {
                topMargin = 40
            }
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.DKGRAY))
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener {
                showPromptModeFromCamera()
            }
        }
        cameraLayout.addView(btnCameraBack)
        
        // --- 3. Settings Layout (Watch Settings) ---
        settingsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 16)
            visibility = View.GONE
        }
        
        val tvSettingsTitle = TextView(this).apply {
            text = "Settings"
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12)
        }
        
        btnCycleCountdown = Button(this).apply {
            text = "Countdown: ${configuredCountdownSec}s"
            textSize = 14f
            setOnClickListener { cycleCountdown() }
        }
        
        btnToggleHaptic = Button(this).apply {
            text = "Haptic: ON"
            textSize = 14f
            setOnClickListener { toggleHaptic() }
        }
        
        val btnBackSettings = Button(this).apply {
            text = "◀ Back"
            textSize = 14f
            setOnClickListener { showSettingsMode(false) }
        }
        
        settingsLayout.addView(tvSettingsTitle)
        settingsLayout.addView(btnCycleCountdown)
        settingsLayout.addView(btnToggleHaptic)
        settingsLayout.addView(btnBackSettings)
        
        // Add to main layout
        mainLayout.addView(cameraLayout)
        mainLayout.addView(promptLayout)
        mainLayout.addView(settingsLayout)
        
        setContentView(mainLayout)

        // Register Receivers
        LocalBroadcastManager.getInstance(this).apply {
            registerReceiver(previewReceiver, IntentFilter("ACTION_PREVIEW_FRAME"))
            registerReceiver(shutterDoneReceiver, IntentFilter("ACTION_SHUTTER_DONE"))
        }
        
        checkConnectionStatus()
        fetchCountdownFromPhone()
        ambientController = AmbientModeSupport.attach(this)
    }
    
    private fun checkConnectionStatus() {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            val hasNode = nodes.isNotEmpty()
            Wearable.getCapabilityClient(this)
                .getCapability("remote_cap_phone_app", com.google.android.gms.wearable.CapabilityClient.FILTER_REACHABLE)
                .addOnSuccessListener { capabilityInfo ->
                    val hasApp = capabilityInfo.nodes.isNotEmpty()
                    
                    val nodeStatus = if (hasNode) "✅ OS" else "❌ App"
                    val appStatus = if (hasApp) "✅ App" else "❌ App"
                    
                    runOnUiThread {
                        val currentText = tvStatus.text.toString()
                        val parts = currentText.split("\n")
                        val countdownPart = if (parts.size > 1) parts[1] else ""
                        tvStatus.text = "Phone: $nodeStatus | $appStatus\n$countdownPart"
                    }
                }
        }
    }
    
    private fun fetchCountdownFromPhone() {
        sendSignalToPhone("/get_countdown")
    }
    
    private val messageClientListener = com.google.android.gms.wearable.MessageClient.OnMessageReceivedListener { messageEvent ->
        if (messageEvent.path.startsWith("/set_countdown/")) {
            val secStr = messageEvent.path.substringAfter("/set_countdown/")
            try {
                val sec = secStr.toInt()
                runOnUiThread {
                    configuredCountdownSec = sec
                    val modeText = if (sec > 0) "Countdown: ${sec}s" else "No Countdown"
                    
                    val currentText = tvStatus.text.toString()
                    val parts = currentText.split("\n")
                    val connectionPart = if (parts.isNotEmpty()) parts[0] else "Phone Connection:"
                    tvStatus.text = "$connectionPart\n$modeText"
                    
                    btnShutter.text = if (sec > 0) "📸 (${sec}s)" else "📸"
                    if (::btnCycleCountdown.isInitialized) {
                        btnCycleCountdown.text = if (sec > 0) "Countdown: ${sec}s" else "Countdown: Off"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing countdown", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getMessageClient(this).addListener(messageClientListener)
        fetchCountdownFromPhone()
    }

    override fun onPause() {
        super.onPause()
        Wearable.getMessageClient(this).removeListener(messageClientListener)
    }
    
    private fun startCameraMode(withPreview: Boolean) {
        promptLayout.visibility = View.GONE
        settingsLayout.visibility = View.GONE
        cameraLayout.visibility = View.VISIBLE
        
        val path = if (withPreview) "/wake_preview" else "/wake_shutter_only"
        sendSignalToPhone(path)
    }

    private fun showPromptModeFromCamera() {
        cameraLayout.visibility = View.GONE
        settingsLayout.visibility = View.GONE
        promptLayout.visibility = View.VISIBLE
        // Notify phone to stop preview if needed
        sendSignalToPhone("/stop_preview")
    }
    
    private fun showSettingsMode(show: Boolean) {
        if (show) {
            promptLayout.visibility = View.GONE
            cameraLayout.visibility = View.GONE
            settingsLayout.visibility = View.VISIBLE
        } else {
            settingsLayout.visibility = View.GONE
            cameraLayout.visibility = View.GONE
            promptLayout.visibility = View.VISIBLE
        }
    }
    
    private fun cycleCountdown() {
        val next = when (configuredCountdownSec) {
            0 -> 3
            3 -> 5
            5 -> 10
            10 -> 0
            else -> 0
        }
        configuredCountdownSec = next
        btnCycleCountdown.text = if (next > 0) "Countdown: ${next}s" else "Countdown: Off"
        btnShutter.text = if (next > 0) "📸 (${next}s)" else "📸"
        
        // Update phone
        sendSignalToPhone("/set_countdown/$next")
        
        // Show status update on prompt layout
        val currentText = tvStatus.text.toString()
        val parts = currentText.split("\n")
        val connectionPart = if (parts.isNotEmpty()) parts[0] else "Phone Connection:"
        val modeText = if (next > 0) "Countdown: ${next}s" else "No Countdown"
        tvStatus.text = "$connectionPart\n$modeText"
    }

    private fun toggleHaptic() {
        isHapticEnabled = !isHapticEnabled
        btnToggleHaptic.text = if (isHapticEnabled) "Haptic: ON" else "Haptic: OFF"
    }

    private fun startCountdown(seconds: Int) {
        tvCountdown.visibility = View.VISIBLE
        
        object : CountDownTimer((seconds * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secLeft = (millisUntilFinished / 1000) + 1
                tvCountdown.text = secLeft.toString()
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
    
    override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback = MyAmbientCallback()

    private inner class MyAmbientCallback : AmbientModeSupport.AmbientCallback() {
        override fun onEnterAmbient(ambientDetails: Bundle?) {
            super.onEnterAmbient(ambientDetails)
            Log.d(TAG, "Entering ambient mode")
            sendSignalToPhone("/ambient_mode_on")
        }

        override fun onExitAmbient() {
            super.onExitAmbient()
            Log.d(TAG, "Exiting ambient mode")
            sendSignalToPhone("/ambient_mode_off")
        }

        override fun onUpdateAmbient() {
            super.onUpdateAmbient()
            // Update UI periodically if needed
        }
    }

    private fun vibrateTrigger() {
        if (!isHapticEnabled) return
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(50)
        }
    }
    
    private fun vibrateSuccess() {
        if (!isHapticEnabled) return
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
