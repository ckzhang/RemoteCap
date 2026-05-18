package com.ckzhang.remotecap.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.Wearable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

        LocalBroadcastManager.getInstance(this).registerReceiver(
            previewReceiver, IntentFilter("ACTION_PREVIEW_FRAME")
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(previewReceiver)
    }

    private fun sendShutterMessage() {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) {
                Toast.makeText(this, "未連接到手機", Toast.LENGTH_SHORT).show()
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
