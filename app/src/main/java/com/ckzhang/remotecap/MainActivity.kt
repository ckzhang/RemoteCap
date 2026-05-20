package com.ckzhang.remotecap

import android.content.Context
import android.content.Intent
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
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.Wearable

class MainActivity : ComponentActivity() {

    private lateinit var statusText: TextView

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "權限已允許", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "權限被拒絕", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TargetManager.init(this)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(64, 80, 64, 80)
            setBackgroundColor(Color.parseColor("#121212"))
        }
        
        val title = TextView(this).apply {
            text = "Remote Cap 📸"
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }

        statusText = TextView(this).apply {
            text = "連線狀態: 檢查中...\n手錶 App: 檢查中..."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 60)
        }
        
        layout.addView(title)
        layout.addView(statusText)
        
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
        
        val btnPermission = createStyledButton("1. 允許懸浮窗權限 (必要)").apply {
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName))
                    startActivity(intent)
                } else {
                    Toast.makeText(context, "權限已開啟", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        val btnAccSettings = createStyledButton("2. 開啟無障礙服務 (必要, 控制快門)").apply {
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val btnCameraPermission = createStyledButton("3. 授權相機 (閃光燈倒數用)").apply {
            setOnClickListener {
                val permission = android.Manifest.permission.CAMERA
                if (ContextCompat.checkSelfPermission(this@MainActivity, permission) == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this@MainActivity, "相機權限已允許", Toast.LENGTH_SHORT).show()
                } else {
                    requestPermissionLauncher.launch(permission)
                }
            }
        }
        
        val btnStartTarget = createStyledButton("4. 顯示/隱藏瞄準星 🎯").apply {
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
        layout.addView(btnCameraPermission)
        layout.addView(btnStartTarget)
        
        setContentView(layout)

        checkConnectionStatus()
    }
    
    private fun checkConnectionStatus() {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            val hasNode = nodes.isNotEmpty()
            Wearable.getCapabilityClient(this)
                .getCapability("remote_cap_watch_app", com.google.android.gms.wearable.CapabilityClient.FILTER_REACHABLE)
                .addOnSuccessListener { capabilityInfo ->
                    val hasApp = capabilityInfo.nodes.isNotEmpty()
                    
                    val nodeStatus = if (hasNode) "✅ 已連線" else "❌ 未連線"
                    val appStatus = if (hasApp) "✅ 已安裝" else "❌ 未安裝或未開啟"
                    
                    runOnUiThread {
                        statusText.text = "手錶連線 (OS): $nodeStatus\n手錶 App (Capability): $appStatus"
                    }
                }
        }
    }

    private fun createStyledButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            this.textSize = 14f
            this.setTextColor(Color.WHITE)
            this.isAllCaps = false
            
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                140
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
