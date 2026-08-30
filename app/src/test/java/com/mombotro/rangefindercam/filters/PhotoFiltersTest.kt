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
    fun `grain noise is monochrome (same delta on every channel), not colored speckle`() {
        // A gray input (R==G==B) run through grain must still have R==G==B
        // afterward for every pixel - proving one shared noise value is
        // applied per pixel, not three independent per-channel deltas
        // (which would produce visible colored speckling instead of real
        // film-grain texture).
        val width = 20
        val height = 20
        val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                source.setPixel(x, y, Color.rgb(128, 128, 128))
            }
        }

        val result = PhotoFilters.applyGrain(source)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = result.getPixel(x, y)
                assertEquals("pixel ($x,$y) red != green", Color.red(pixel), Color.green(pixel))
                assertEquals("pixel ($x,$y) green != blue", Color.green(pixel), Color.blue(pixel))
            }
        }
    }

    @Test
    fun `apply dispatches to the matching filter for each look, plus grain on all of them`() {
        // apply() always composes grain on top of the chosen look, so exact
        // per-pixel assertions (like B&W's R==G==B) no longer hold - grain
        // perturbs each channel independently. Check aggregate tendencies
        // over a bigger bitmap instead, which survives the noise.
        val width = 20
        val height = 20
        fun coloredSource(color: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    bitmap.setPixel(x, y, color)
                }
            }
            return bitmap
        }

        fun averageRedMinusBlue(bitmap: Bitmap): Double {
            var total = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = bitmap.getPixel(x, y)
                    total += Color.red(pixel) - Color.blue(pixel)
                }
            }
            return total.toDouble() / (width * height)
        }

        val bwSource = coloredSource(Color.rgb(200, 50, 100))
        val bwResult = PhotoFilters.apply(bwSource, Look.BLACK_AND_WHITE)
        // Grain applies one shared noise delta per pixel (monochrome, not
        // per-channel), so a desaturated (R==G==B before grain) image stays
        // exactly R==G==B after grain too - not just close on average.
        assertEquals(0.0, averageRedMinusBlue(bwResult), 0.0)

        val sepiaSource = coloredSource(Color.rgb(128, 128, 128))
        val sepiaResult = PhotoFilters.apply(sepiaSource, Look.SEPIA)
        assertTrue("expected a clear warm-tint average even with grain noise, was ${averageRedMinusBlue(sepiaResult)}",
            averageRedMinusBlue(sepiaResult) > 20)

        // GRAIN routes to grain-only (no tone change) plus grain, so just
        // confirm apply() returns a same-sized bitmap without throwing.
        val grainSource = coloredSource(Color.rgb(200, 50, 100))
        val grainResult = PhotoFilters.apply(grainSource, Look.GRAIN)
        assertEquals(grainSource.width, grainResult.width)
        assertEquals(grainSource.height, grainResult.height)
    }
}
