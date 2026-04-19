package com.ramarot.mobile

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import androidx.annotation.RequiresApi
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

@RequiresApi(Build.VERSION_CODES.R)
class RamarotAccessibilityService : AccessibilityService() {
    private val detector = TimingDetector()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val busy = AtomicBoolean(false)

    private var lastTapAtMs = 0L
    private var prevDelta: Float? = null

    // オーバーレイ
    private var overlayView: TextView? = null
    private var windowManager: WindowManager? = null

    private val loop = object : Runnable {
        override fun run() {
            if (!isEnabled()) {
                updateOverlay("⏸ 待機中", Color.parseColor("#888888"))
                mainHandler.postDelayed(this, 250)
                return
            }
            requestFrameAndAct()
            mainHandler.postDelayed(this, 28)
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

    private fun showOverlay() {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val tv = TextView(this).apply {
                text = "🟡 起動中"
                textSize = 14f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#CC000000"))
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
        } catch (e: Exception) {
            // オーバーレイ失敗しても動作は継続
        }
    }

    private fun removeOverlay() {
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
    }

    private fun updateOverlay(text: String, bgColor: Int) {
        mainHandler.post {
            overlayView?.text = text
            overlayView?.setBackgroundColor(bgColor)
        }
    }

    private fun requestFrameAndAct() {
        if (!busy.compareAndSet(false, true)) return

        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                try {
                    val bitmap = screenshot.toBitmap() ?: run {
                        updateOverlay("❌ 画面取得失敗", Color.parseColor("#CC880000"))
                        return
                    }
                    val detected = detector.detect(bitmap)
                    if (detected == null) {
                        updateOverlay("🔍 バー未検出", Color.parseColor("#CC444444"))
                        return
                    }

                    val delta = detected.whiteX - detected.zoneCenterX
                    val withinWindow = detector.shouldTap(detected)
                    val crossedCenter = prevDelta?.let {
                        it * delta < 0f && abs(delta) < detected.zoneWidth * 0.5f
                    } ?: false

                    if ((withinWindow || crossedCenter) && canTapNow()) {
                        tap(detected.whiteX, detected.barTop + (detected.barBottom - detected.barTop) * 0.5f)
                        lastTapAtMs = System.currentTimeMillis()
                        updateOverlay("👆 タップ！", Color.parseColor("#CC00AA00"))
                    } else {
                        updateOverlay("✅ 検出中...", Color.parseColor("#CC004488"))
                    }

                    prevDelta = delta
                } finally {
                    busy.set(false)
                    screenshot.hardwareBuffer.close()
                }
            }

            override fun onFailure(errorCode: Int) {
                busy.set(false)
                updateOverlay("❌ エラー($errorCode)", Color.parseColor("#CC880000"))
            }
        })
    }

    private fun ScreenshotResult.toBitmap(): Bitmap? {
        val buffer: HardwareBuffer = hardwareBuffer
        return Bitmap.wrapHardwareBuffer(buffer, colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
    }

    private fun canTapNow(): Boolean {
        val now = System.currentTimeMillis()
        return now - lastTapAtMs > 70L
    }

    private fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 12)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun isEnabled(): Boolean {
        val prefs = getSharedPreferences(MainActivity.PREF_NAME, MODE_PRIVATE)
        return prefs.getBoolean(MainActivity.KEY_ENABLED, false)
    }
}
