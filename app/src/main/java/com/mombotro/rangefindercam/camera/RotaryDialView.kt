package com.mombotro.rangefindercam.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * A thumbwheel dial drawn side-on (a knurled cylinder viewed from the side,
 * like a real camera's ISO/EV wheel), not face-on like a clock. Drag left
 * or right to spin it, one "click" (with a short vibration) per fixed
 * horizontal drag distance. Has hard stops at both ends - dragging past the
 * first or last value doesn't wrap around, matching how a real camera dial
 * behaves, with a stronger vibration marking the end stop.
 */
class RotaryDialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private companion object {
        const val PIXELS_PER_STEP = 36f
        const val CLICK_VIBRATE_MS = 15L
        const val END_STOP_VIBRATE_MS = 40L
    }

    private var values: List<String> = emptyList()
    private var currentIndex = 0
    private var onValueChanged: ((Int, String) -> Unit)? = null

    private var lastTouchX = 0f
    private var accumulatedPixels = 0f

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val knurlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 3f
    }
    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val vibrator: Vibrator? by lazy {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun setValues(newValues: List<String>, initialIndex: Int = 0) {
        values = newValues
        currentIndex = initialIndex.coerceIn(0, (newValues.size - 1).coerceAtLeast(0))
        accumulatedPixels = 0f
        invalidate()
    }

    fun setOnValueChangedListener(listener: (Int, String) -> Unit) {
        onValueChanged = listener
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val strokeInset = bodyPaint.strokeWidth
        val bodyRect = RectF(strokeInset, strokeInset * 3, width - strokeInset, height - strokeInset * 3)
        val cornerRadius = bodyRect.height() / 2f
        canvas.drawRoundRect(bodyRect, cornerRadius, cornerRadius, bodyPaint)

        if (values.isEmpty()) return

        // Knurl lines are drawn at fixed positions across the body (this
        // small, fixed value set doesn't need an actually-scrolling reel) -
        // one per value, evenly spaced with margin so the outermost lines
        // don't sit on the rounded end caps. The current position's line is
        // drawn taller and gets a pointer triangle above it, like a
        // highlighted notch on a real thumbwheel.
        val margin = cornerRadius * 0.8f
        val usableWidth = bodyRect.width() - margin * 2f
        val shortHalfHeight = bodyRect.height() * 0.28f
        val tallHalfHeight = bodyRect.height() * 0.42f

        values.indices.forEach { index ->
            val fraction = if (values.size <= 1) 0.5f else index.toFloat() / (values.size - 1)
            val x = bodyRect.left + margin + fraction * usableWidth
            val halfHeight = if (index == currentIndex) tallHalfHeight else shortHalfHeight
            canvas.drawLine(x, bodyRect.centerY() - halfHeight, x, bodyRect.centerY() + halfHeight, knurlPaint)
        }

        val currentFraction = if (values.size <= 1) 0.5f else currentIndex.toFloat() / (values.size - 1)
        val pointerX = bodyRect.left + margin + currentFraction * usableWidth
        val pointerTop = strokeInset
        val pointerPath = Path().apply {
            moveTo(pointerX - 6f, pointerTop)
            lineTo(pointerX + 6f, pointerTop)
            lineTo(pointerX, pointerTop + 8f)
            close()
        }
        canvas.drawPath(pointerPath, pointerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                accumulatedPixels = 0f
            }
            MotionEvent.ACTION_MOVE -> {
                val delta = event.x - lastTouchX
                lastTouchX = event.x
                accumulatedPixels += delta

                while (accumulatedPixels >= PIXELS_PER_STEP) {
                    accumulatedPixels -= PIXELS_PER_STEP
                    step(1)
                }
                while (accumulatedPixels <= -PIXELS_PER_STEP) {
                    accumulatedPixels += PIXELS_PER_STEP
                    step(-1)
                }
            }
        }
        return true
    }

    private fun step(direction: Int) {
        if (values.isEmpty()) return
        val next = DialStepper.step(currentIndex, direction, values.size)
        if (next == currentIndex) {
            vibrate(END_STOP_VIBRATE_MS)
            return
        }
        currentIndex = next
        vibrate(CLICK_VIBRATE_MS)
        invalidate()
        onValueChanged?.invoke(currentIndex, values[currentIndex])
    }

    private fun vibrate(durationMs: Long) {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(durationMs)
        }
    }
}
