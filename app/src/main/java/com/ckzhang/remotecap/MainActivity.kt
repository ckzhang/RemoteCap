package com.ckzhang.remotecap

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color
import android.view.Gravity
import androidx.activity.ComponentActivity
import com.google.android.gms.wearable.Wearable

class MainActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private lateinit var btnCountdown: Button
    private val SCREEN_RECORD_REQUEST_CODE = 1001

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
        
        btnCountdown = createStyledButton("⏳ 倒數計時: " + TargetManager.countdownSec + " 秒").apply {
            setOnClickListener {
                val next = when (TargetManager.countdownSec) {
                    0 -> 3
                    3 -> 5
                    5 -> 10
                    else -> 0
                }
                TargetManager.countdownSec = next
                text = "⏳ 倒數計時: " + next + " 秒"
                
                // Notify watch about the new countdown value
                syncCountdownToWatch(next)
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
        
        val btnStartTarget = createStyledButton("3. 顯示/隱藏瞄準星 🎯").apply {
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
        
        val btnPreview = createStyledButton("4. 畫面預覽 (擷取截圖傳至手錶)").apply {
            setOnClickListener {
                val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(mpm.createScreenCaptureIntent(), SCREEN_RECORD_REQUEST_CODE)
            }
        }

        val btnGalleryPermission = createStyledButton("5. 允許讀取相簿權限 (必要, 傳送照片)").apply {
            setOnClickListener {
                requestGalleryPermission()
            }
        }

        layout.addView(btnCountdown)
        layout.addView(btnPermission)
        layout.addView(btnAccSettings)
        layout.addView(btnStartTarget)
        layout.addView(btnPreview)
        layout.addView(btnGalleryPermission)
        
        setContentView(layout)

        checkConnectionStatus()
        handleAutomationIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleAutomationIntent(intent)
    }

    private fun handleAutomationIntent(intent: Intent?) {
        when (intent?.action) {
            "ACTION_AUTO_POSITION_TARGET" -> {
                val x = intent.getIntExtra("SCREEN_X", -1)
                val y = intent.getIntExtra("SCREEN_Y", -1)
                if (x < 0 || y < 0) return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) return
                startService(Intent(this, FloatingTargetService::class.java))
                Handler(Looper.getMainLooper()).postDelayed({
                    FloatingTargetService.instance?.positionTargetAt(x.toFloat(), y.toFloat())
                }, 800)
            }
            "ACTION_AUTO_SET_COUNTDOWN" -> {
                val sec = intent.getIntExtra("COUNTDOWN_SEC", 0)
                TargetManager.countdownSec = sec
                btnCountdown.text = "⏳ 倒數計時: $sec 秒"
                syncCountdownToWatch(sec)
            }
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SCREEN_RECORD_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                Toast.makeText(this, "授權成功，開始傳送預覽畫面...", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, ScreenCaptureService::class.java).apply {
                    action = "START_CAPTURE"
                    putExtra("RESULT_CODE", resultCode)
                    putExtra("RESULT_DATA", data)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } else {
                Toast.makeText(this, "必須允許螢幕錄製才能傳送預覽畫面", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun requestGalleryPermission() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "相簿讀取權限已開啟", Toast.LENGTH_SHORT).show()
        } else {
            requestPermissions(arrayOf(perm), 1002)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1002) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "成功取得相簿讀取權限！", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "未能取得相簿讀取權限，將無法傳送照片至手錶", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun syncCountdownToWatch(sec: Int) {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            val messageClient = Wearable.getMessageClient(this)
            for (node in nodes) {
                messageClient.sendMessage(node.id, "/set_countdown/$sec", byteArrayOf())
            }
        }
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
        stopService(Intent(this, ScreenCaptureService::class.java).apply { action = "STOP_CAPTURE" })
    }
}
