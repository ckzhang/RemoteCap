package com.ckzhang.remotecap

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class FloatingTargetService : Service() {
    companion object {
        var instance: FloatingTargetService? = null
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: TextView
    private lateinit var params: WindowManager.LayoutParams

    private val fadeHandler = Handler(Looper.getMainLooper())
    private val fadeRunnable = Runnable {
        if (::floatingView.isInitialized) {
            floatingView.alpha = 0.2f
            floatingView.animate().scaleX(0.7f).scaleY(0.7f).setDuration(200).start()
        }
    }

    private fun resetFadeTimer() {
        if (::floatingView.isInitialized) {
            floatingView.alpha = 1.0f
            floatingView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
        }
        fadeHandler.removeCallbacks(fadeRunnable)
        fadeHandler.postDelayed(fadeRunnable, 3000)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_POSITION_TARGET") {
            val screenX = intent.getIntExtra("SCREEN_X", -1)
            val screenY = intent.getIntExtra("SCREEN_Y", -1)
            if (screenX >= 0 && screenY >= 0) {
                positionTargetAt(screenX.toFloat(), screenY.toFloat())
            }
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        floatingView = TextView(this).apply {
            text = "🎯"
            textSize = 45f
            setTextColor(Color.RED)
            setShadowLayer(5f, 0f, 0f, Color.BLACK)
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            flags,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        params.gravity = Gravity.TOP or Gravity.START
        
        val displayMetrics = resources.displayMetrics
        params.x = (displayMetrics.widthPixels / 2) - 50
        params.y = (displayMetrics.heightPixels * 0.8).toInt()

        floatingView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        resetFadeTimer()
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        resetFadeTimer()
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        resetFadeTimer()
                        val loc = IntArray(2)
                        floatingView.getLocationOnScreen(loc)
                        TargetManager.targetX = loc[0].toFloat() + (floatingView.width / 2f)
                        TargetManager.targetY = loc[1].toFloat() + (floatingView.height / 2f)
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(floatingView, params)
        
        floatingView.post {
            val loc = IntArray(2)
            floatingView.getLocationOnScreen(loc)
            TargetManager.targetX = loc[0].toFloat() + (floatingView.width / 2f)
            TargetManager.targetY = loc[1].toFloat() + (floatingView.height / 2f)
            resetFadeTimer()
        }
    }

    /** Positions crosshair center at screen coordinates (for ADB/automation). */
    fun positionTargetAt(screenCenterX: Float, screenCenterY: Float) {
        if (!::floatingView.isInitialized) return
        floatingView.post {
            val halfW = floatingView.width / 2f
            val halfH = floatingView.height / 2f
            params.x = (screenCenterX - halfW).toInt().coerceAtLeast(0)
            params.y = (screenCenterY - halfH).toInt().coerceAtLeast(0)
            windowManager.updateViewLayout(floatingView, params)
            TargetManager.init(applicationContext)
            TargetManager.targetX = screenCenterX
            TargetManager.targetY = screenCenterY
        }
    }

    fun hideAndPassThrough() {
        Handler(Looper.getMainLooper()).post {
            floatingView.visibility = View.INVISIBLE
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            windowManager.updateViewLayout(floatingView, params)
        }
    }

    fun showAndCatch() {
        Handler(Looper.getMainLooper()).post {
            floatingView.visibility = View.VISIBLE
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            windowManager.updateViewLayout(floatingView, params)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        fadeHandler.removeCallbacks(fadeRunnable)
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
