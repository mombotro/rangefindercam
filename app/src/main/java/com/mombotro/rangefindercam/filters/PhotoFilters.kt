package com.mombotro.rangefindercam.filters

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

enum class Look {
    BLACK_AND_WHITE,
    SEPIA,
    GRAIN
}

object PhotoFilters {

    fun applyBlackAndWhite(source: Bitmap): Bitmap {
        val desaturate = ColorMatrix().apply { setSaturation(0f) }
        val contrast = 1.6f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        // Applied as two separate draws, not combined via postConcat: concat
        // multiplies the raw 4x5 matrices together algebraically before any
        // clamping happens, so an intermediate overflow (e.g. the sepia
        // matrix below oversaturating red past 255) silently carries through
        // into the next stage's math unclamped. Two draws clamp to 0-255
        // between stages, matching how each stage is meant to see the other's
        // actual output.
        val desaturated = drawWithMatrix(source, desaturate)
        return drawWithMatrix(desaturated, contrastMatrix)
    }

    fun applySepia(source: Bitmap): Bitmap {
        val sepia = ColorMatrix(floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        // Faded look: compress the dynamic range and lift the floor slightly,
        // so pure white lands below 255 and pure black lands above 0.
        val fade = 0.85f
        val fadeTranslate = 255f * (1f - fade) / 2f
        val fadeMatrix = ColorMatrix(floatArrayOf(
            fade, 0f, 0f, 0f, fadeTranslate,
            0f, fade, 0f, 0f, fadeTranslate,
            0f, 0f, fade, 0f, fadeTranslate,
            0f, 0f, 0f, 1f, 0f
        ))
        val toned = drawWithMatrix(source, sepia)
        return drawWithMatrix(toned, fadeMatrix)
    }

    internal fun drawWithMatrix(source: Bitmap, matrix: ColorMatrix): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }
}
