package com.ckzhang.remotecap

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.google.android.gms.wearable.Wearable
import java.io.ByteArrayOutputStream

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
                Log.d("ShutterAccessibility", "開始倒數 " + countdown + " 秒...")
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

    private fun sendLatestPhotoToWatch() {
        Log.d("ShutterAccessibility", "準備抓取最新照片傳給手錶...")
        Handler(Looper.getMainLooper()).postDelayed({
            val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA)
            val sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC"
            val cursor = contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            if (cursor != null && cursor.moveToFirst()) {
                val dataColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val imagePath = cursor.getString(dataColumnIndex)
                cursor.close()

                Log.d("ShutterAccessibility", "找到最新照片: " + imagePath)

                val options = BitmapFactory.Options()
                options.inSampleSize = 8
                val bitmap = BitmapFactory.decodeFile(imagePath, options)
                
                if (bitmap != null) {
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos)
                    val photoBytes = baos.toByteArray()
                    bitmap.recycle()

                    Log.d("ShutterAccessibility", "圖片壓縮完成，大小: " + photoBytes.size + " bytes，準備傳送")

                    Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
                        val messageClient = Wearable.getMessageClient(this)
                        for (node in nodes) {
                            messageClient.sendMessage(node.id, "/preview", photoBytes)
                        }
                    }
                }
            } else {
                cursor?.close()
                Log.d("ShutterAccessibility", "找不到最新照片")
            }
        }, 2000)
    }

    fun performShutterClick() {
        val x = TargetManager.targetX
        val y = TargetManager.targetY
        Log.d("ShutterAccessibility", "準備點擊懸浮窗目標座標: X=" + x + ", Y=" + y)

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
                    
                    sendLatestPhotoToWatch()
                    
                    Handler(Looper.getMainLooper()).postDelayed({
                        FloatingTargetService.instance?.showAndCatch()
                    }, 500)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.d("ShutterAccessibility", "目標座標點擊失敗(取消)")
                    FloatingTargetService.instance?.showAndCatch()
                }
            }, null)
            Log.d("ShutterAccessibility", "dispatchGesture 呼叫結果: " + result)
        }, 100)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}
}
