package com.ckzhang.remotecap

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.google.android.gms.wearable.Wearable

class ShutterAccessibilityService : AccessibilityService() {

    companion object {
        var instance: ShutterAccessibilityService? = null
    }

    override fun onServiceConnected() {
        instance = this
        TargetManager.init(applicationContext)
        Log.d("ShutterAccessibility", "無障礙服務已連接")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        TargetManager.init(applicationContext)
        if (intent?.action == "ACTION_CLICK_SHUTTER") {
            val countdown = TargetManager.countdownSec
            if (countdown > 0) {
                Log.d("ShutterAccessibility", "開始倒數 $countdown 秒...")
                Handler(Looper.getMainLooper()).postDelayed({
                    performShutterClick()
                }, countdown * 1000L)
            } else {
                performShutterClick()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun sendShutterDoneToWatch() {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            val messageClient = Wearable.getMessageClient(this)
            for (node in nodes) {
                messageClient.sendMessage(node.id, "/shutter_done", byteArrayOf())
            }
        }
    }

    fun performShutterClick() {
        val x = TargetManager.targetX
        val y = TargetManager.targetY
        Log.d("ShutterAccessibility", "準備點擊懸浮窗目標座標: X=$x, Y=$y")

        if (x == 0f && y == 0f) {
            Log.d("ShutterAccessibility", "目標座標為 0, 請先開啟懸浮窗！")
            return
        }

        FloatingTargetService.instance?.hideAndPassThrough()

        Handler(Looper.getMainLooper()).postDelayed({
            val path = Path()
            path.moveTo(x, y)
            path.lineTo(x + 2, y + 2)

            val gestureBuilder = GestureDescription.Builder()
            val clickStroke = GestureDescription.StrokeDescription(path, 0, 150)
            gestureBuilder.addStroke(clickStroke)
            
            val result = dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d("ShutterAccessibility", "目標座標點擊成功！通知手錶震動")
                    sendShutterDoneToWatch()
                    
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
        }, 100)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}
}
