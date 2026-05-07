package com.inknironapps.bagger.ml

import android.graphics.Bitmap

/**
 * Extracts a dominant color from the center 100×100 region of a bitmap.
 * Used as a tiebreaker hint for ambiguous OCR matches and to pre-fill
 * the color field in the add-disc form.
 */
object ColorExtractor {

    data class DominantColor(val r: Int, val g: Int, val b: Int) {
        fun toHex(): String = "#%02X%02X%02X".format(r, g, b)
    }

    fun extractCenter(bitmap: Bitmap, sampleSize: Int = 100): DominantColor {
        val cx = bitmap.width / 2
        val cy = bitmap.height / 2
        val half = sampleSize / 2
        val left = (cx - half).coerceAtLeast(0)
        val top = (cy - half).coerceAtLeast(0)
        val right = (cx + half).coerceAtMost(bitmap.width)
        val bottom = (cy + half).coerceAtMost(bitmap.height)

        var rTotal = 0L
        var gTotal = 0L
        var bTotal = 0L
        var n = 0L
        for (y in top until bottom step 4) {
            for (x in left until right step 4) {
                val pixel = bitmap.getPixel(x, y)
                rTotal += (pixel shr 16) and 0xFF
                gTotal += (pixel shr 8) and 0xFF
                bTotal += pixel and 0xFF
                n++
            }
        }
        if (n == 0L) return DominantColor(0, 0, 0)
        return DominantColor((rTotal / n).toInt(), (gTotal / n).toInt(), (bTotal / n).toInt())
    }
}
