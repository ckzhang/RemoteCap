package com.ckzhang.remotecap

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ContentUris
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.google.android.gms.wearable.Wearable
import java.io.ByteArrayOutputStream

class ShutterAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ShutterAccessibility"
        private const val PHOTO_MESSAGE_PATH = "/photo"
        private const val MAX_PHOTO_FETCH_ATTEMPTS = 10
        private const val QUERY_ARG_RELAXED_AUTHORIZATION = "android:query-arg-relaxed-authorization"
        private const val QUERY_ARG_SQL_SELECTION = "android:query-arg-sql-selection"
        private const val QUERY_ARG_SQL_SELECTION_ARGS = "android:query-arg-sql-selection-args"
        private const val QUERY_ARG_SORT_COLUMNS = "android:query-arg-sort-columns"
        private const val QUERY_ARG_SORT_DIRECTION = "android:query-arg-sort-direction"
        private const val QUERY_SORT_DIRECTION_DESCENDING = 1
        private const val QUERY_ARG_LIMIT = "android:query-arg-limit"
        var instance: ShutterAccessibilityService? = null
    }

    private var captureAfterEpochSec: Long = 0L

    override fun onServiceConnected() {
        instance = this
        TargetManager.init(applicationContext)
        Log.i(TAG, "無障礙服務已連接")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        TargetManager.init(applicationContext)
        if (intent?.action == "ACTION_CLICK_SHUTTER") {
            Log.i(TAG, "收到快門指令，立即點擊（倒數僅在手錶執行）")
            performShutterClick()
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
        Log.i(TAG, "準備抓取最新照片傳給手錶...")
        fetchAndSendLatestPhoto(attempt = 0)
    }

    private fun fetchAndSendLatestPhoto(attempt: Int) {
        if (attempt >= MAX_PHOTO_FETCH_ATTEMPTS) {
            Log.w(TAG, "多次嘗試後仍無法取得新照片")
            return
        }
        val delayMs = 1500L + attempt * 1000L
        Handler(Looper.getMainLooper()).postDelayed({
            val photoBytes = loadLatestCapturedPhotoBytes()
            if (photoBytes != null) {
                Log.i(TAG, "圖片壓縮完成，大小: ${photoBytes.size} bytes，準備傳送")
                sendPhotoBytesToWatch(photoBytes)
            } else {
                Log.i(TAG, "第 ${attempt + 1} 次尚未找到新照片，稍後重試 (epochSec=$captureAfterEpochSec)")
                fetchAndSendLatestPhoto(attempt + 1)
            }
        }, delayMs)
    }

    private fun loadLatestCapturedPhotoBytes(): ByteArray? {
        if (!hasMediaReadPermission()) {
            Log.e(TAG, "缺少讀取媒體庫權限，無法傳送照片到手錶")
            return null
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ?"
        val selectionArgs = arrayOf(captureAfterEpochSec.toString())

        var cursor = queryLatestImages(uri, projection, selection, selectionArgs, limit = 5)
        if (cursor == null || !cursor.moveToFirst()) {
            cursor?.close()
            Log.i(TAG, "Retry MediaStore query without date filter (relaxed auth)")
            cursor = queryLatestImages(uri, projection, null, null, limit = 8)
        }

        cursor ?: return null

        cursor.use {
            if (!it.moveToFirst()) return null
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val dateAdded = it.getLong(dateColumn)
            if (dateAdded < captureAfterEpochSec) {
                Log.i(TAG, "最新照片太舊 (added=$dateAdded, need>=$captureAfterEpochSec)")
                return null
            }
            val id = it.getLong(idColumn)
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            Log.i(TAG, "Found latest photo URI: $uri")

            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            val bitmap = contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return null

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
            bitmap.recycle()
            return baos.toByteArray()
        }
    }

    private fun queryLatestImages(
        uri: android.net.Uri,
        projection: Array<String>,
        selection: String?,
        selectionArgs: Array<String>?,
        limit: Int
    ): android.database.Cursor? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val queryArgs = Bundle().apply {
                putBoolean(QUERY_ARG_RELAXED_AUTHORIZATION, true)
                if (selection != null) {
                    putString(QUERY_ARG_SQL_SELECTION, selection)
                    if (selectionArgs != null) {
                        putStringArray(QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                    }
                }
                putStringArray(QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Images.Media.DATE_ADDED))
                putInt(QUERY_ARG_SORT_DIRECTION, QUERY_SORT_DIRECTION_DESCENDING)
                putInt(QUERY_ARG_LIMIT, limit)
            }
            contentResolver.query(uri, projection, queryArgs, null)
        } else {
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT $limit"
            contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
        }
    }

    private fun hasMediaReadPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun sendPhotoBytesToWatch(photoBytes: ByteArray) {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) {
                Log.w(TAG, "沒有已連線的手錶節點，無法傳送照片")
                return@addOnSuccessListener
            }
            val messageClient = Wearable.getMessageClient(this)
            for (node in nodes) {
                messageClient.sendMessage(node.id, PHOTO_MESSAGE_PATH, photoBytes)
                    .addOnSuccessListener {
                        Log.i(TAG, "Photo sent to watch node ${node.id}")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "傳送照片至手錶失敗", e)
                    }
            }
        }
    }

    fun performShutterClick() {
        val x = TargetManager.targetX
        val y = TargetManager.targetY
        Log.i(TAG, "準備點擊懸浮窗目標座標: X=$x, Y=$y")

        if (x == 0f && y == 0f) {
            Log.i(TAG, "目標座標為 0, 請先開啟懸浮窗！")
            return
        }

        captureAfterEpochSec = (System.currentTimeMillis() / 1000L) - 5L
        FloatingTargetService.instance?.hideAndPassThrough()

        Handler(Looper.getMainLooper()).postDelayed({
            val path = Path()
            path.moveTo(x, y)
            path.lineTo(x, y)

            val gestureBuilder = GestureDescription.Builder()
            val clickStroke = GestureDescription.StrokeDescription(path, 0, 80)
            gestureBuilder.addStroke(clickStroke)

            val result = dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.i(TAG, "目標座標點擊成功！通知手錶震動")
                    sendShutterDoneToWatch()
                    sendLatestPhotoToWatch()

                    Handler(Looper.getMainLooper()).postDelayed({
                        FloatingTargetService.instance?.showAndCatch()
                    }, 500)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.i(TAG, "目標座標點擊失敗(取消)")
                    FloatingTargetService.instance?.showAndCatch()
                }
            }, null)
            Log.i(TAG, "dispatchGesture 呼叫結果: $result")
        }, 300)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}
}
