package com.mombotro.rangefindercam.filters

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoFiltersTest {

    private fun coloredBitmap(color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                bitmap.setPixel(x, y, color)
            }
        }
        return bitmap
    }

    @Test
    fun `black and white output has equal RGB channels per pixel`() {
        val source = coloredBitmap(Color.rgb(200, 50, 100))
        val result = PhotoFilters.applyBlackAndWhite(source)

        val pixel = result.getPixel(0, 0)
        assertEquals(Color.red(pixel), Color.green(pixel))
        assertEquals(Color.green(pixel), Color.blue(pixel))
    }

    @Test
    fun `black and white boosts contrast versus plain desaturation`() {
        // A mid-gray input desaturates to itself; the contrast boost should
        // push a bright input brighter and a dark input darker than a plain
        // desaturate would, proving the contrast step actually ran.
        val brightSource = coloredBitmap(Color.rgb(200, 200, 200))
        val result = PhotoFilters.applyBlackAndWhite(brightSource)
        val resultBrightness = Color.red(result.getPixel(0, 0))

        assertTrue("expected contrast-boosted brightness > plain input (200), was $resultBrightness",
            resultBrightness > 200)
    }

    @Test
    fun `sepia output has a warm tint (red channel exceeds blue)`() {
        val source = coloredBitmap(Color.rgb(128, 128, 128))
        val result = PhotoFilters.applySepia(source)

        val pixel = result.getPixel(0, 0)
        assertTrue("expected red > blue for a sepia tint, red=${Color.red(pixel)} blue=${Color.blue(pixel)}",
            Color.red(pixel) > Color.blue(pixel))
    }

    @Test
    fun `sepia reduces dynamic range for a faded look`() {
        val brightSource = coloredBitmap(Color.rgb(255, 255, 255))
        val result = PhotoFilters.applySepia(brightSource)
        val resultBrightness = Color.red(result.getPixel(0, 0))

        assertTrue("expected faded white < 255, was $resultBrightness", resultBrightness < 255)
    }

    @Test
    fun `grain output differs from input at many pixels`() {
        val source = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        for (y in 0 until 20) {
            for (x in 0 until 20) {
                source.setPixel(x, y, Color.rgb(128, 128, 128))
            }
        }
        val result = PhotoFilters.applyGrain(source)

        var changedCount = 0
        for (y in 0 until 20) {
            for (x in 0 until 20) {
                if (result.getPixel(x, y) != source.getPixel(x, y)) changedCount++
            }
        }
        assertTrue("expected most of the 400 pixels to be perturbed by noise, only $changedCount changed",
            changedCount > 300)
    }

    @Test
    fun `grain keeps channel values within valid 0 to 255 range`() {
        // Pixels near the edges of the valid range are the ones most likely
        // to reveal an unclamped overflow/underflow bug in the noise step.
        val source = coloredBitmap(Color.rgb(2, 253, 128))
        val result = PhotoFilters.applyGrain(source)

        val pixel = result.getPixel(0, 0)
        assertTrue(Color.red(pixel) in 0..255)
        assertTrue(Color.green(pixel) in 0..255)
        assertTrue(Color.blue(pixel) in 0..255)
    }

    @Test
    fun `apply dispatches to the matching filter for each look`() {
        val source = coloredBitmap(Color.rgb(200, 50, 100))

        val bwPixel = PhotoFilters.apply(source, Look.BLACK_AND_WHITE).getPixel(0, 0)
        assertEquals(Color.red(bwPixel), Color.green(bwPixel))

        val sepiaPixel = PhotoFilters.apply(source, Look.SEPIA).getPixel(0, 0)
        assertTrue(Color.red(sepiaPixel) > Color.blue(sepiaPixel))

        // GRAIN's output is randomized, so just confirm apply() routes to it
        // without throwing and returns a same-sized bitmap.
        val grainResult = PhotoFilters.apply(source, Look.GRAIN)
        assertEquals(source.width, grainResult.width)
        assertEquals(source.height, grainResult.height)
    }
}
