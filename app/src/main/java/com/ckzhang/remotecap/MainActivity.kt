package com.ckzhang.remotecap

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color
import android.view.Gravity
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TargetManager.init(this)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(64, 80, 64, 80)
            setBackgroundColor(Color.parseColor("#121212")) // Dark background
        }
        
        val title = TextView(this).apply {
            text = "Remote Cap \uD83D\uDCF8"
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 80)
        }
        
        layout.addView(title)
        
        val btnCountdown = createStyledButton("⏳ 倒數計時: " + TargetManager.countdownSec + " 秒").apply {
            setOnClickListener {
                val next = when (TargetManager.countdownSec) {
                    0 -> 3
                    3 -> 5
                    5 -> 10
                    else -> 0
                }
                TargetManager.countdownSec = next
                text = "⏳ 倒數計時: " + next + " 秒"
            }
        }
        
        val btnPermission = createStyledButton("1. 允許懸浮窗權限").apply {
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName))
                    startActivity(intent)
                } else {
                    Toast.makeText(context, "權限已開啟", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        val btnAccSettings = createStyledButton("2. 開啟無障礙服務").apply {
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        
        val btnStartTarget = createStyledButton("3. 顯示/隱藏瞄準星 \uD83C\uDFAF").apply {
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(this@MainActivity, FloatingTargetService::class.java)
                    if (FloatingTargetService.instance != null) {
                        stopService(intent)
                    } else {
                        startService(intent)
                    }
                } else {
                    Toast.makeText(context, "請先開啟懸浮窗權限", Toast.LENGTH_SHORT).show()
                }
            }
        }

        layout.addView(btnCountdown)
        layout.addView(btnPermission)
        layout.addView(btnAccSettings)
        layout.addView(btnStartTarget)
        
        setContentView(layout)
    }
    
    private fun createStyledButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            this.textSize = 16f
            this.setTextColor(Color.WHITE)
            this.isAllCaps = false
            
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                160
            )
            params.setMargins(0, 0, 0, 32)
            this.layoutParams = params
            
            this.setBackgroundColor(Color.parseColor("#FF2A65"))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this, FloatingTargetService::class.java))
    }
}
