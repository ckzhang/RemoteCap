package com.ckzhang.remotecap

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class ShutterAccessibilityService : AccessibilityService() {

    companion object {
        var instance: ShutterAccessibilityService? = null
    }

    override fun onServiceConnected() {
        instance = this
        // Initialize TargetManager in case the Service is woken up while Activity is dead
        TargetManager.init(applicationContext)
        Log.d("ShutterAccessibility", "無障礙服務已連接")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Also initialize here in case onStartCommand is called directly
        TargetManager.init(applicationContext)
        if (intent?.action == "ACTION_CLICK_SHUTTER") {
            performShutterClick()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    fun performShutterClick() {
        val x = TargetManager.targetX
        val y = TargetManager.targetY
        Log.d("ShutterAccessibility", "準備點擊懸浮窗目標座標: X=$x, Y=$y")

        if (x == 0f && y == 0f) {
            Log.d("ShutterAccessibility", "目標座標為 0, 請先開啟懸浮窗！")
            return
        }

        // 1. 隱藏瞄準星，並設定 FLAG_NOT_TOUCHABLE 讓點擊事件可以穿透到下方的相機
        FloatingTargetService.instance?.hideAndPassThrough()

        // 2. 改用 Handler 延遲，避免阻塞主執行緒導致 UI 更新卡死
        Handler(Looper.getMainLooper()).postDelayed({
            val path = Path()
            path.moveTo(x, y)
            path.lineTo(x + 2, y + 2)

            val gestureBuilder = GestureDescription.Builder()
            val clickStroke = GestureDescription.StrokeDescription(path, 0, 150)
            gestureBuilder.addStroke(clickStroke)
            
            val result = dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d("ShutterAccessibility", "目標座標點擊成功！")
                    // 3. 延遲 500ms 等相機拍照動畫跑完，再把瞄準星顯示回來
                    Handler(Looper.getMainLooper()).postDelayed({
                        FloatingTargetService.instance?.showAndCatch()
                    }, 500)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.d("ShutterAccessibility", "目標座標點擊失敗(取消)")
                    FloatingTargetService.instance?.showAndCatch()
                }
            }, null)
            Log.d("ShutterAccessibility", "dispatchGesture 呼叫結果: $result")
        }, 100) // 給 WindowManager 100ms 的時間去把 FLAG_NOT_TOUCHABLE 真正套用到畫面上
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}
}
