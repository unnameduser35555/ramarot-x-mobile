package com.ramarot.mobile

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.hardware.HardwareBuffer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
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

    private val loop = object : Runnable {
        override fun run() {
            if (!isEnabled()) {
                mainHandler.postDelayed(this, 250)
                return
            }

            requestFrameAndAct()
            mainHandler.postDelayed(this, 28)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        mainHandler.post(loop)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun requestFrameAndAct() {
        if (!busy.compareAndSet(false, true)) return

        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                try {
                    val bitmap = screenshot.toBitmap() ?: return
                    val detected = detector.detect(bitmap) ?: return
                    val delta = detected.whiteX - detected.zoneCenterX

                    val withinWindow = detector.shouldTap(detected)
                    val crossedCenter = prevDelta?.let { it * delta < 0f && abs(delta) < detected.zoneWidth * 0.5f } ?: false

                    if ((withinWindow || crossedCenter) && canTapNow()) {
                        tap(detected.whiteX, detected.barTop + (detected.barBottom - detected.barTop) * 0.5f)
                        lastTapAtMs = System.currentTimeMillis()
                    }

                    prevDelta = delta
                } finally {
                    busy.set(false)
                    screenshot.hardwareBuffer.close()
                }
            }

            override fun onFailure(errorCode: Int) {
                busy.set(false)
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
