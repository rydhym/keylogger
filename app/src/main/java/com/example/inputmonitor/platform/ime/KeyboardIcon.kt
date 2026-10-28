package com.example.inputmonitor.platform.ime

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

/** Font-independent keyboard symbols, drawn in a 24-unit square. */
internal enum class KeyboardIcon {
    SMILE, SHIFT, CAPS, DELETE, RETURN, STICKER, PALETTE;

    fun draw(canvas: Canvas, paint: Paint, width: Int, height: Int, size: Float) {
        val checkpoint = canvas.save()
        canvas.translate((width - size) / 2f, (height - size) / 2f)
        canvas.scale(size / 24f, size / 24f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        fun lines(vararg points: Float) {
            val path = Path().apply {
                moveTo(points[0], points[1])
                for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
            }
            canvas.drawPath(path, paint)
        }
        when (this) {
            SMILE -> {
                canvas.drawCircle(12f, 12f, 8.5f, paint)
                canvas.drawPoint(9f, 9f, paint); canvas.drawPoint(15f, 9f, paint)
                canvas.drawArc(7.5f, 9f, 16.5f, 17f, 25f, 130f, false, paint)
            }
            SHIFT, CAPS -> {
                lines(3f, 11f, 12f, 3f, 21f, 11f, 16f, 11f, 16f, 20f,
                    8f, 20f, 8f, 11f, 3f, 11f)
                if (this == CAPS) canvas.drawLine(8f, 23f, 16f, 23f, paint)
            }
            DELETE -> {
                lines(9f, 5f, 21f, 5f, 21f, 19f, 9f, 19f, 2f, 12f, 9f, 5f)
                lines(11f, 9f, 17f, 15f); lines(17f, 9f, 11f, 15f)
            }
            RETURN -> {
                lines(20f, 6f, 20f, 13f, 5f, 13f)
                lines(10f, 8f, 5f, 13f, 10f, 18f)
            }
            STICKER -> {
                lines(4f, 4f, 20f, 4f, 20f, 14f, 14f, 20f, 4f, 20f, 4f, 4f)
                lines(20f, 14f, 14f, 14f, 14f, 20f)
                canvas.drawPoint(8f, 9f, paint); canvas.drawPoint(14f, 9f, paint)
                canvas.drawArc(7f, 9f, 15f, 15f, 30f, 120f, false, paint)
            }
            PALETTE -> {
                canvas.drawCircle(12f, 12f, 9f, paint)
                canvas.drawCircle(8f, 9f, 1f, paint)
                canvas.drawCircle(13f, 7f, 1f, paint)
                canvas.drawCircle(17f, 11f, 1f, paint)
                canvas.drawCircle(8f, 15f, 1f, paint)
            }
        }
        canvas.restoreToCount(checkpoint)
    }
}
