package com.ckzhang.remotecap

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.Secure
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.android.gms.wearable.Wearable

class MainActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private lateinit var layoutContent: LinearLayout
    private lateinit var billingManager: BillingManager
    private val SCREEN_RECORD_REQUEST_CODE = 1001
    
    // For easter egg testing
    private var titleClickCount = 0
    private var lastTitleClickTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TargetManager.init(this)
        
        billingManager = BillingManager(this, this) { isPro ->
            renderOnboardingUI()
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#121212"))
        }

        layoutContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(64, 80, 64, 80)
        }

        val title = TextView(this).apply {
            text = "AnyCam 📸"
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
            setOnClickListener {
                val now = System.currentTimeMillis()
                if (now - lastTitleClickTime > 500) {
                    titleClickCount = 0
                }
                titleClickCount++
                lastTitleClickTime = now
                if (titleClickCount >= 5) {
                    titleClickCount = 0
                    val currentProState = billingManager.isPro
                    billingManager.isPro = !currentProState
                    Toast.makeText(context, "Pro Mode (Test): ${!currentProState}", Toast.LENGTH_SHORT).show()
                    renderOnboardingUI()
                }
            }
        }

        statusText = TextView(this).apply {
            text = "連線狀態: 檢查中...\n手錶 App: 檢查中..."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 60)
        }

        layoutContent.addView(title)
        layoutContent.addView(statusText)

        scrollView.addView(layoutContent)
        setContentView(scrollView)

        checkConnectionStatus()
    }

    override fun onResume() {
        super.onResume()
        // Refresh UI state whenever the user returns to the app
        renderOnboardingUI()
    }

    private fun renderOnboardingUI() {
        // Clear all buttons/guides to redraw
        val childCount = layoutContent.childCount
        if (childCount > 2) {
            layoutContent.removeViews(2, childCount - 2)
        }

        val hasOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        val hasAccessibility = isAccessibilityServiceEnabled()
        val hasGalleryPermission = checkGalleryPermission()

        if (!hasOverlay) {
            // Step 1: Overlay
            layoutContent.addView(createStepText("第一步：允許顯示在其他應用程式上層 (必要)", "這用來顯示我們用來觸發拍照的瞄準星 🎯。"))
            val btnOverlay = createStyledButton("點此開啟懸浮窗權限").apply {
                setOnClickListener {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                        startActivity(intent)
                    }
                }
            }
            layoutContent.addView(btnOverlay)
            return
        }

        if (!hasAccessibility) {
            // Step 2: Accessibility
            layoutContent.addView(createStepText("✅ 懸浮窗已開啟\n\n第二步：允許無障礙服務 (必要)", "這讓我們能在您按下手錶快門時，在手機螢幕的瞄準星位置模擬點擊。"))
            val btnAcc = createStyledButton("點此開啟無障礙服務").apply {
                setOnClickListener {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
            layoutContent.addView(btnAcc)
            return
        }
        
        if (!hasGalleryPermission) {
            // Step 3: Gallery Permission
            layoutContent.addView(createStepText("✅ 無障礙服務已開啟\n\n第三步：允許讀取相簿權限 (必要)", "這用來在拍照後，自動將最新的照片傳送到手錶上顯示。"))
            val btnGallery = createStyledButton("點此允許讀取相簿權限").apply {
                setOnClickListener {
                    requestGalleryPermission()
                }
            }
            layoutContent.addView(btnGallery)
            return
        }

        // All setup complete, show main controls
        layoutContent.addView(createStepText("🎉 準備就緒！", "懸浮窗與快門服務皆已啟用。"))

        val btnStartTarget = createStyledButton("顯示 / 隱藏瞄準星 🎯").apply {
            setOnClickListener {
                if (!isAccessibilityServiceEnabled()) {
                    Toast.makeText(this@MainActivity, "請注意：無障礙服務已被系統休眠，請重新開啟！", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    return@setOnClickListener
                }
                val intent = Intent(this@MainActivity, FloatingTargetService::class.java)
                if (FloatingTargetService.instance != null) {
                    stopService(intent)
                } else {
                    startService(intent)
                }
            }
        }

        layoutContent.addView(btnStartTarget)

        if (billingManager.isPro) {
            layoutContent.addView(createStepText("👑 Pro 會員", "已解鎖手錶即時預覽與進階功能。"))
            val btnPreview = createStyledButton("啟動手錶即時預覽 📺").apply {
                setOnClickListener {
                    if (!isAccessibilityServiceEnabled()) {
                        Toast.makeText(this@MainActivity, "請注意：無障礙服務已被系統休眠，請重新開啟！", Toast.LENGTH_LONG).show()
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        return@setOnClickListener
                    }
                    val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    startActivityForResult(mpm.createScreenCaptureIntent(), SCREEN_RECORD_REQUEST_CODE)
                }
            }
            layoutContent.addView(btnPreview)
        } else {
            val btnUpgrade = createStyledButton("解鎖 Pro 版 ($2.99) 👑").apply {
                setBackgroundColor(Color.parseColor("#FFD700")) // Gold
                setTextColor(Color.BLACK)
                setOnClickListener {
                    billingManager.initiatePurchaseFlow()
                }
            }
            val proDesc = TextView(this).apply {
                text = "升級 Pro 版可解鎖：\n- 📺 手錶端即時預覽畫面\n- 🎯 隱藏瞄準星浮水印\n- ⏱️ 手錶端自訂倒數秒數"
                textSize = 14f
                setTextColor(Color.LTGRAY)
                setPadding(0, 0, 0, 48)
            }
            layoutContent.addView(btnUpgrade)
            layoutContent.addView(proDesc)
        }
    }

    private fun checkGalleryPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun requestGalleryPermission() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        requestPermissions(arrayOf(perm), 1002)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1002) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "成功取得相簿讀取權限！", Toast.LENGTH_SHORT).show()
                renderOnboardingUI()
            } else {
                Toast.makeText(this, "未能取得相簿讀取權限，將無法傳送照片至手錶", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = packageName + "/" + ShutterAccessibilityService::class.java.canonicalName
        var enabledServicesSetting: String? = null
        try {
            enabledServicesSetting = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        } catch (e: Settings.SettingNotFoundException) {
            // Ignore
        }
        if (enabledServicesSetting.isNullOrEmpty()) return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            if (componentNameString.equals(expectedComponentName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun createStepText(title: String, desc: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, 24)
            layoutParams = params

            val tTitle = TextView(context).apply {
                text = title
                textSize = 18f
                setTextColor(Color.WHITE)
                setPadding(0, 0, 0, 8)
            }

            val tDesc = TextView(context).apply {
                text = desc
                textSize = 14f
                setTextColor(Color.LTGRAY)
            }

            addView(tTitle)
            addView(tDesc)
        }
    }

    private fun createStyledButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            this.textSize = 15f
            this.setTextColor(Color.WHITE)
            this.isAllCaps = false

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                150
            )
            params.setMargins(0, 0, 0, 48)
            this.layoutParams = params

            this.setBackgroundColor(Color.parseColor("#FF2A65"))
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

    private fun checkConnectionStatus() {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            val hasNode = nodes.isNotEmpty()
            val isWifiDirect = nodes.any { it.isNearby } // Simplified check for nearby/high-bandwidth
            
            ScreenCaptureService.instance?.setDynamicQuality(isWifiDirect)

            Wearable.getCapabilityClient(this)
                .getCapability("remote_cap_watch_app", com.google.android.gms.wearable.CapabilityClient.FILTER_REACHABLE)
                .addOnSuccessListener { capabilityInfo ->
                    val hasApp = capabilityInfo.nodes.isNotEmpty()

                    val nodeStatus = if (hasNode) "✅ 已連線 (${if (isWifiDirect) "高速" else "藍牙"})" else "❌ 未連線"
                    val appStatus = if (hasApp) "✅ 已安裝" else "❌ 未安裝或未開啟"

                    runOnUiThread {
                        statusText.text = "手錶連線 (OS): $nodeStatus\n手錶 App (Capability): $appStatus"
                    }
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this, FloatingTargetService::class.java))
        stopService(Intent(this, ScreenCaptureService::class.java).apply { action = "STOP_CAPTURE" })
    }
}
