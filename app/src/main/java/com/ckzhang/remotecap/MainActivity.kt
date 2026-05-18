package com.ckzhang.remotecap

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    private val captureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                action = "START_CAPTURE"
                putExtra("RESULT_CODE", result.resultCode)
                putExtra("RESULT_DATA", result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "開始投射螢幕畫面", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "授權被拒絕", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        TargetManager.init(this)
        
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(50, 50, 50, 50)
        }
        
        val btnPermission = Button(this).apply {
            text = "1. 允許顯示在其他應用程式上層 (用於瞄準星)"
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    startActivity(intent)
                } else {
                    Toast.makeText(context, "顯示在其他應用程式上層權限已開啟", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        val btnAccSettings = Button(this).apply {
            text = "2. 開啟無障礙服務 (用於點擊)"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        
        val btnStartTarget = Button(this).apply {
            text = "3. 顯示/隱藏懸浮游標 🎯"
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(this@MainActivity, FloatingTargetService::class.java)
                    if (FloatingTargetService.instance != null) {
                        stopService(intent)
                    } else {
                        startService(intent)
                    }
                } else {
                    Toast.makeText(context, "請先完成步驟 1", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        val btnStartPreview = Button(this).apply {
            text = "4. 開始將螢幕畫面傳給手錶 (Live View)"
            setOnClickListener {
                val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val captureIntent = mpm.createScreenCaptureIntent()
                captureLauncher.launch(captureIntent)
            }
        }
        
        val btnStopPreview = Button(this).apply {
            text = "5. 停止畫面傳輸"
            setOnClickListener {
                val intent = Intent(this@MainActivity, ScreenCaptureService::class.java)
                intent.action = "STOP_CAPTURE"
                startService(intent)
            }
        }

        layout.addView(btnPermission)
        layout.addView(btnAccSettings)
        layout.addView(btnStartTarget)
        layout.addView(btnStartPreview)
        layout.addView(btnStopPreview)
        
        setContentView(layout)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this, FloatingTargetService::class.java))
    }
}
