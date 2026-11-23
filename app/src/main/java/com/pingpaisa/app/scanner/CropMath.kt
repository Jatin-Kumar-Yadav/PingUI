package com.pingpaisa.app.scanner

import com.pingpaisa.app.recognition.Box
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class CropHandle { TOP_LEFT, TOP, TOP_RIGHT, RIGHT, BOTTOM_RIGHT, BOTTOM, BOTTOM_LEFT, LEFT, MOVE, NONE }

data class PixelRect(val left: Int, val top: Int, val width: Int, val height: Int)

/** Geometry of the crop editor, kept free of Android types so it can be unit-tested. All rectangles are in view pixels. */
object CropMath {
    /** Where an image of [imgW]x[imgH] is drawn when fitted (no stretching) in a [viewW]x[viewH] area. */
    fun fitInside(viewW: Float, viewH: Float, imgW: Int, imgH: Int): Box {
        if (imgW <= 0 || imgH <= 0 || viewW <= 0f || viewH <= 0f) return Box(0f, 0f, 0f, 0f)
        val s = min(viewW / imgW, viewH / imgH)
        val w = imgW * s
        val h = imgH * s
        val l = (viewW - w) / 2f
        val t = (viewH - h) / 2f
        return Box(l, t, l + w, t + h)
    }

    /** Starting selection: the shown image with a small margin, so all four handles are reachable. */
    fun defaultRect(shown: Box): Box {
        val mx = shown.width * 0.08f
        val my = shown.height * 0.08f
        return Box(shown.left + mx, shown.top + my, shown.right - mx, shown.bottom - my)
    }

    fun hitTest(r: Box, x: Float, y: Float, slop: Float): CropHandle {
        val nearL = abs(x - r.left) <= slop
        val nearR = abs(x - r.right) <= slop
        val nearT = abs(y - r.top) <= slop
        val nearB = abs(y - r.bottom) <= slop
        val inX = x >= r.left - slop && x <= r.right + slop
        val inY = y >= r.top - slop && y <= r.bottom + slop
        return when {
            nearL && nearT -> CropHandle.TOP_LEFT
            nearR && nearT -> CropHandle.TOP_RIGHT
            nearL && nearB -> CropHandle.BOTTOM_LEFT
            nearR && nearB -> CropHandle.BOTTOM_RIGHT
            nearT && inX -> CropHandle.TOP
            nearB && inX -> CropHandle.BOTTOM
            nearL && inY -> CropHandle.LEFT
            nearR && inY -> CropHandle.RIGHT
            x > r.left && x < r.right && y > r.top && y < r.bottom -> CropHandle.MOVE
            else -> CropHandle.NONE
        }
    }

    private fun abs(v: Float) = if (v < 0f) -v else v

    /** Moves or resizes [r] by (dx, dy). Free aspect ratio; never leaves [bounds]; never smaller than [minSize]. */
    fun drag(r: Box, handle: CropHandle, dx: Float, dy: Float, bounds: Box, minSize: Float): Box {
        if (handle == CropHandle.NONE) return r
        if (handle == CropHandle.MOVE) {
            val w = r.width
            val h = r.height
            val l = (r.left + dx).coerceIn(bounds.left, max(bounds.left, bounds.right - w))
            val t = (r.top + dy).coerceIn(bounds.top, max(bounds.top, bounds.bottom - h))
            return Box(l, t, l + w, t + h)
        }
        var l = r.left; var t = r.top; var rt = r.right; var b = r.bottom
        val moveL = handle == CropHandle.LEFT || handle == CropHandle.TOP_LEFT || handle == CropHandle.BOTTOM_LEFT
        val moveR = handle == CropHandle.RIGHT || handle == CropHandle.TOP_RIGHT || handle == CropHandle.BOTTOM_RIGHT
        val moveT = handle == CropHandle.TOP || handle == CropHandle.TOP_LEFT || handle == CropHandle.TOP_RIGHT
        val moveB = handle == CropHandle.BOTTOM || handle == CropHandle.BOTTOM_LEFT || handle == CropHandle.BOTTOM_RIGHT
        if (moveL) l = (l + dx).coerceIn(bounds.left, rt - minSize)
        if (moveR) rt = (rt + dx).coerceIn(l + minSize, bounds.right)
        if (moveT) t = (t + dy).coerceIn(bounds.top, b - minSize)
        if (moveB) b = (b + dy).coerceIn(t + minSize, bounds.bottom)
        return Box(l, t, rt, b)
    }

    /** The selection in the image's own pixels. Always at least 1x1 and inside the image. */
    fun toPixels(sel: Box, shown: Box, imgW: Int, imgH: Int): PixelRect {
        if (shown.width <= 0f || shown.height <= 0f) return PixelRect(0, 0, imgW, imgH)
        val sx = imgW / shown.width
        val sy = imgH / shown.height
        val l = ((sel.left - shown.left) * sx).roundToInt().coerceIn(0, imgW - 1)
        val t = ((sel.top - shown.top) * sy).roundToInt().coerceIn(0, imgH - 1)
        val r = ((sel.right - shown.left) * sx).roundToInt().coerceIn(l + 1, imgW)
        val b = ((sel.bottom - shown.top) * sy).roundToInt().coerceIn(t + 1, imgH)
        return PixelRect(l, t, r - l, b - t)
    }
}
