package com.pingpaisa.app.recognition

import kotlin.math.abs

/**
 * Finds the horizontal strike-through line that shopping sites draw over the list price (MRP).
 * Works on the grey values of the small image region around ONE printed amount, so it is pure and testable.
 * Digits alone never ink 90% of a row; a strike line does.
 */
object StrikeDetector {
    fun isStruck(lum: IntArray, w: Int, h: Int): Boolean {
        if (w < 12 || h < 8 || lum.size < w * h) return false
        val hist = IntArray(256)
        for (i in 0 until w * h) hist[lum[i].coerceIn(0, 255)]++
        var acc = 0
        var bg = 255
        for (v in 0..255) { acc += hist[v]; if (acc >= w * h / 2) { bg = v; break } }
        val c0 = (w * 0.06f).toInt()
        val c1 = (w * 0.94f).toInt()
        if (c1 - c0 < 8) return false
        val r0 = (h * 0.30f).toInt()
        val r1 = (h * 0.78f).toInt().coerceAtMost(h - 1)
        var totalInk = 0
        for (i in 0 until w * h) if (abs(lum[i] - bg) > 70) totalInk++
        if (totalInk > w * h * 0.8) return false      // a solid blob, not text
        for (r in r0..r1) {
            var ink = 0
            for (c in c0 until c1) if (abs(lum[r * w + c] - bg) > 70) ink++
            if (ink >= (c1 - c0) * 0.90) return true
        }
        return false
    }
}
