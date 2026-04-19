package com.ramarot.mobile

import android.graphics.Bitmap
import kotlin.math.abs

/**
 * Detects timing-game elements from a screenshot frame.
 *
 * Expected UI:
 * - horizontal dark/blue bar
 * - moving white line
 * - cyan target zone (changes position/size)
 */
class TimingDetector {
    data class Detection(
        val barLeft: Int,
        val barTop: Int,
        val barRight: Int,
        val barBottom: Int,
        val whiteX: Float,
        val zoneLeft: Float,
        val zoneRight: Float
    ) {
        val zoneCenterX: Float get() = (zoneLeft + zoneRight) * 0.5f
        val zoneWidth: Float get() = zoneRight - zoneLeft
    }

    fun detect(bitmap: Bitmap): Detection? {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 50 || h < 50) return null

        val barRect = findBlueBarRect(bitmap) ?: return null
        val whiteX = findWhiteLineX(bitmap, barRect) ?: return null
        val zone = findCyanZone(bitmap, barRect) ?: return null

        return Detection(
            barLeft = barRect.left,
            barTop = barRect.top,
            barRight = barRect.right,
            barBottom = barRect.bottom,
            whiteX = whiteX,
            zoneLeft = zone.first,
            zoneRight = zone.second
        )
    }

    fun shouldTap(d: Detection): Boolean {
        val margin = (d.zoneWidth * 0.18f).coerceAtLeast(4f)
        return abs(d.whiteX - d.zoneCenterX) <= margin
    }

    private data class RectI(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private fun findBlueBarRect(bitmap: Bitmap): RectI? {
        val w = bitmap.width
        val h = bitmap.height
        val startY = (h * 0.2f).toInt()
        val endY = (h * 0.75f).toInt()

        var bestY = -1
        var bestLen = 0
        var bestStart = 0
        var bestEnd = 0

        val row = IntArray(w)
        for (y in startY until endY step 2) {
            bitmap.getPixels(row, 0, w, 0, y, w, 1)
            var curStart = -1
            var x = 0
            while (x < w) {
                if (isBarBlue(row[x])) {
                    if (curStart == -1) curStart = x
                } else if (curStart != -1) {
                    val len = x - curStart
                    if (len > bestLen) {
                        bestLen = len
                        bestY = y
                        bestStart = curStart
                        bestEnd = x
                    }
                    curStart = -1
                }
                x++
            }
            if (curStart != -1) {
                val len = w - curStart
                if (len > bestLen) {
                    bestLen = len
                    bestY = y
                    bestStart = curStart
                    bestEnd = w
                }
            }
        }

        if (bestY == -1 || bestLen < w * 0.35f) return null

        var top = bestY
        var bottom = bestY
        while (top > 0 && rowBlueRatio(bitmap, top, bestStart, bestEnd) > 0.5f) top--
        while (bottom < h - 1 && rowBlueRatio(bitmap, bottom, bestStart, bestEnd) > 0.5f) bottom++

        return RectI(bestStart, top, bestEnd, bottom)
    }

    private fun rowBlueRatio(bitmap: Bitmap, y: Int, left: Int, right: Int): Float {
        val width = right - left
        if (width <= 0) return 0f
        val row = IntArray(width)
        bitmap.getPixels(row, 0, width, left, y, width, 1)
        var blueCount = 0
        for (px in row) {
            if (isBarBlue(px)) blueCount++
        }
        return blueCount.toFloat() / width
    }

    private fun findWhiteLineX(bitmap: Bitmap, bar: RectI): Float? {
        val width = bar.right - bar.left
        val height = bar.bottom - bar.top
        if (width <= 0 || height <= 0) return null

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, bar.left, bar.top, width, height)

        var sumX = 0f
        var count = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val px = pixels[y * width + x]
                if (isWhiteLine(px)) {
                    sumX += x
                    count++
                }
            }
        }
        if (count < height * 2) return null

        return bar.left + (sumX / count)
    }

    private fun findCyanZone(bitmap: Bitmap, bar: RectI): Pair<Float, Float>? {
        val width = bar.right - bar.left
        val height = bar.bottom - bar.top
        if (width <= 0 || height <= 0) return null

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, bar.left, bar.top, width, height)

        val hits = IntArray(width)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val px = pixels[y * width + x]
                if (isCyanZone(px)) hits[x]++
            }
        }

        var bestL = -1
        var bestR = -1
        var x = 0
        while (x < width) {
            if (hits[x] > height * 0.22f) {
                val l = x
                while (x < width && hits[x] > height * 0.22f) x++
                val r = x - 1
                if ((r - l) > (bestR - bestL)) {
                    bestL = l
                    bestR = r
                }
            }
            x++
        }

        if (bestL < 0 || bestR <= bestL) return null
        return (bar.left + bestL).toFloat() to (bar.left + bestR).toFloat()
    }

    private fun isBarBlue(px: Int): Boolean {
        val r = (px shr 16) and 0xFF
        val g = (px shr 8) and 0xFF
        val b = px and 0xFF
        return b > 120 && b > g + 35 && b > r + 35
    }

    private fun isWhiteLine(px: Int): Boolean {
        val r = (px shr 16) and 0xFF
        val g = (px shr 8) and 0xFF
        val b = px and 0xFF
        return r > 220 && g > 220 && b > 220
    }

    private fun isCyanZone(px: Int): Boolean {
        val r = (px shr 16) and 0xFF
        val g = (px shr 8) and 0xFF
        val b = px and 0xFF
        return r < 140 && g > 130 && b > 130 && abs(g - b) < 70
    }
}
