package com.mombotro.rangefindercam.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A rotary dial: touch-drag in a circle around it to step through a bounded
 * list of values, one "click" (with a short vibration) per fixed angular
 * increment. Has hard stops at both ends - dragging past the first or last
 * value doesn't wrap around, matching how a real camera dial behaves, with
 * a stronger vibration marking the end stop. Ticks are drawn across a fixed
 * 270-degree arc rather than a full circle, so the bounded range reads
 * visually too.
 */
class RotaryDialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private companion object {
        const val DEGREES_PER_STEP = 18f
        const val ARC_DEGREES = 270f
        const val START_DEGREES = -135f
        const val CLICK_VIBRATE_MS = 15L
        const val END_STOP_VIBRATE_MS = 40L
    }

    private var values: List<String> = emptyList()
    private var currentIndex = 0
    private var onValueChanged: ((Int, String) -> Unit)? = null

    private var lastTouchAngle = 0f
    private var accumulatedDegrees = 0f

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
        accumulatedDegrees = 0f
        invalidate()
    }

    fun setOnValueChangedListener(listener: (Int, String) -> Unit) {
        onValueChanged = listener
    }

    private fun angleForFraction(fraction: Float): Double =
        Math.toRadians((START_DEGREES + fraction * ARC_DEGREES).toDouble())

    private fun fractionForIndex(index: Int): Float =
        if (values.size <= 1) 0f else index.toFloat() / (values.size - 1)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - ringPaint.strokeWidth
        canvas.drawCircle(cx, cy, radius, ringPaint)

        if (values.isEmpty()) return

        values.indices.forEach { index ->
            val angle = angleForFraction(fractionForIndex(index))
            val innerR = radius - 14f
            val x1 = cx + innerR * cos(angle).toFloat()
            val y1 = cy + innerR * sin(angle).toFloat()
            val x2 = cx + radius * cos(angle).toFloat()
            val y2 = cy + radius * sin(angle).toFloat()
            canvas.drawLine(x1, y1, x2, y2, tickPaint)
        }

        val pointerAngle = angleForFraction(fractionForIndex(currentIndex))
        val pointerR = radius - 22f
        val px = cx + pointerR * cos(pointerAngle).toFloat()
        val py = cy + pointerR * sin(pointerAngle).toFloat()
        canvas.drawCircle(px, py, 6f, pointerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cx = width / 2f
        val cy = height / 2f
        val angleDegrees = Math.toDegrees(
            atan2((event.y - cy).toDouble(), (event.x - cx).toDouble())
        ).toFloat()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchAngle = angleDegrees
                accumulatedDegrees = 0f
            }
            MotionEvent.ACTION_MOVE -> {
                var delta = angleDegrees - lastTouchAngle
                // Normalize the wrap at +/-180deg so a drag crossing that
                // seam doesn't register as one huge jump.
                if (delta > 180f) delta -= 360f
                if (delta < -180f) delta += 360f
                lastTouchAngle = angleDegrees
                accumulatedDegrees += delta

                while (accumulatedDegrees >= DEGREES_PER_STEP) {
                    accumulatedDegrees -= DEGREES_PER_STEP
                    step(1)
                }
                while (accumulatedDegrees <= -DEGREES_PER_STEP) {
                    accumulatedDegrees += DEGREES_PER_STEP
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
