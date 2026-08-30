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
}
