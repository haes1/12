package com.example.eyeswipe

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private var face: Rect? = null
    private var imageWidth = 1
    private var imageHeight = 1
    private var rotation = 0

    fun setFace(rect: Rect, width: Int, height: Int, rotationDegrees: Int) {
        face = Rect(rect)
        imageWidth = width
        imageHeight = height
        rotation = rotationDegrees
        invalidate()
    }

    fun clear() {
        face = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rect = face ?: return
        if (imageWidth <= 0 || imageHeight <= 0) return

        val points = arrayOf(
            floatArrayOf(rect.left.toFloat(), rect.top.toFloat()),
            floatArrayOf(rect.right.toFloat(), rect.top.toFloat()),
            floatArrayOf(rect.right.toFloat(), rect.bottom.toFloat()),
            floatArrayOf(rect.left.toFloat(), rect.bottom.toFloat())
        ).map { transform(it[0], it[1]) }

        val minX = points.minOf { it[0] }
        val maxX = points.maxOf { it[0] }
        val minY = points.minOf { it[1] }
        val maxY = points.maxOf { it[1] }

        // Center-crop mapping, matching PreviewView's default scale behavior.
        val scale = max(width / 1f, height / 1f) /
            max(transformedWidth(), transformedHeight()).coerceAtLeast(1f)
        val outW = transformedWidth() * scale
        val outH = transformedHeight() * scale
        val dx = (width - outW) / 2f
        val dy = (height - outH) / 2f

        paint.color = 0xFF55E6A5.toInt()
        canvas.drawRoundRect(
            minX * transformedWidth() * scale + dx,
            minY * transformedHeight() * scale + dy,
            maxX * transformedWidth() * scale + dx,
            maxY * transformedHeight() * scale + dy,
            28f, 28f, paint
        )
    }

    private fun transformedWidth() = if (rotation == 90 || rotation == 270) imageHeight.toFloat() else imageWidth.toFloat()
    private fun transformedHeight() = if (rotation == 90 || rotation == 270) imageWidth.toFloat() else imageHeight.toFloat()

    private fun transform(x: Float, y: Float): FloatArray {
        val w = imageWidth.toFloat()
        val h = imageHeight.toFloat()
        val tx: Float
        val ty: Float

        when (rotation) {
            90 -> { tx = 1f - y / h; ty = x / w }
            180 -> { tx = 1f - x / w; ty = 1f - y / h }
            270 -> { tx = y / h; ty = 1f - x / w }
            else -> { tx = x / w; ty = y / h }
        }

        // Front camera preview is mirrored horizontally.
        return floatArrayOf(1f - tx, ty)
    }
}
