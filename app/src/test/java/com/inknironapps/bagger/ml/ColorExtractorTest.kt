package com.inknironapps.bagger.ml

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ColorExtractorTest {

    @Test fun extractsDominantColorFromSolidBitmap() {
        val bmp = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(220, 30, 40))
        }
        val color = ColorExtractor.extractCenter(bmp)
        assertTrue(color.r in 215..225)
        assertTrue(color.g in 25..35)
        assertTrue(color.b in 35..45)
    }

    @Test fun handlesSmallerBitmapWithoutCrash() {
        val bmp = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(0, 0, 255))
        }
        val color = ColorExtractor.extractCenter(bmp)
        assertTrue(color.b > 200)
    }
}
