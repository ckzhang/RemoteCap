package com.ckzhang.remotecap.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.Wearable

class MainActivity : ComponentActivity() {
    private val TAG = "WearShutter"
    private lateinit var ivPreview: ImageView

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
        
        val layout = android.widget.FrameLayout(this)
        layout.setBackgroundColor(android.graphics.Color.BLACK)
        
        ivPreview = ImageView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        
        val btnShutter = Button(this).apply {
            text = "📸"
            textSize = 24f
            layoutParams = android.widget.FrameLayout.LayoutParams(
                120, 120, android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = 20 }
            setOnClickListener { sendShutterMessage() }
        }
        
        layout.addView(ivPreview)
        layout.addView(btnShutter)
        setContentView(layout)

        LocalBroadcastManager.getInstance(this).apply {
            registerReceiver(previewReceiver, IntentFilter("ACTION_PREVIEW_FRAME"))
            registerReceiver(shutterDoneReceiver, IntentFilter("ACTION_SHUTTER_DONE"))
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
            val timings = longArrayOf(0, 100, 50, 100) // 等待, 震100ms, 停50ms, 震100ms
            vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
        } else {
            vibrator.vibrate(longArrayOf(0, 100, 50, 100), -1)
        }
    }

    private fun sendShutterMessage() {
        vibrateTrigger() // 發送訊號時先短震一次
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) {
                Toast.makeText(this, "找不到連線手機", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }
            val messageClient = Wearable.getMessageClient(this)
            for (node in nodes) {
                messageClient.sendMessage(node.id, "/shutter", byteArrayOf())
                    .addOnFailureListener { Log.e(TAG, "發送失敗", it) }
            }
        }
    }
}
