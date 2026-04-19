package com.ramarot.mobile

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import kotlin.math.abs

class RamarotAccessibilityService : AccessibilityService() {

    private val detector = TimingDetector()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastTapAtMs = 0L
    private var prevDelta: Float? = null

    private var overlayView: TextView? = null
    private var windowManager: WindowManager? = null

    private val loop = object : Runnable {
        override fun run() {
            if (!isEnabled()) {
                updateOverlay("⏸ 待機中", Color.parseColor("#AA333333"))
                mainHandler.postDelayed(this, 250)
                return
            }
            processFrame()
            mainHandler.postDelayed(this, 30)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        showOverlay()
        mainHandler.post(loop)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        removeOverlay()
        super.onDestroy()
    }

    private fun processFrame() {
        val bitmap = ScreenCaptureService.latestBitmap.get()
        if (bitmap == null) {
            updateOverlay("📷 画面取得待ち", Color.parseColor("#AA884400"))
            return
        }

        val detected = detector.detect(bitmap)
        if (detected == null) {
            updateOverlay("🔍 バー未検出", Color.parseColor("#AA444444"))
            prevDelta = null
            return
        }

        val delta = detected.whiteX - detected.zoneCenterX
        val withinWindow = detector.shouldTap(detected)
        val crossedCenter = prevDelta?.let {
            it * delta < 0f && abs(delta) < detected.zoneWidth * 0.5f
        } ?: false

        if ((withinWindow || crossedCenter) && canTapNow()) {
            val tapY = detected.barTop + (detected.barBottom - detected.barTop) * 0.5f
            tap(detected.whiteX, tapY)
            lastTapAtMs = System.currentTimeMillis()
            updateOverlay("👆 タップ！", Color.parseColor("#AA006600"))
        } else {
            updateOverlay("✅ 検出中...", Color.parseColor("#AA003366"))
        }

        prevDelta = delta
    }

    private fun canTapNow(): Boolean {
        return System.currentTimeMillis() - lastTapAtMs > 70L
    }

    private fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 12)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun showOverlay() {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val tv = TextView(this).apply {
                text = "🟡 起動中"
                textSize = 14f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#AA000000"))
                setPadding(20, 10, 20, 10)
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 20
                y = 100
            }
            windowManager?.addView(tv, params)
            overlayView = tv
        } catch (_: Exception) {}
    }

    private fun removeOverlay() {
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {}
        overlayView = null
    }

    private fun updateOverlay(text: String, bgColor: Int) {
        mainHandler.post {
            try {
                overlayView?.text = text
                overlayView?.setBackgroundColor(bgColor)
            } catch (_: Exception) {}
        }
    }

    private fun isEnabled(): Boolean {
        val prefs = getSharedPreferences(MainActivity.PREF_NAME, MODE_PRIVATE)
        return prefs.getBoolean(MainActivity.KEY_ENABLED, false)
    }
}
