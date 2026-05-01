package com.inknironapps.bagger.ui.screens.comparison

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.inknironapps.bagger.data.db.entity.DiscEntity
import kotlin.math.PI
import kotlin.math.sin

/**
 * Crude flight-shape projection.
 * Maps speed (forward distance), glide (drift), turn (initial right), fade (terminal left)
 * onto a 2D path drawn from launcher (bottom-center) up the canvas.
 */
@Composable
fun FlightChartCanvas(discs: List<Pair<DiscEntity, Color>>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w / 2f

        discs.forEach { (disc, color) ->
            val path = flightPath(disc, cx, h, w)
            drawPath(path, color = color, style = Stroke(width = 6f))
        }
    }
}

private fun flightPath(disc: DiscEntity, cx: Float, h: Float, w: Float): Path {
    val path = Path()
    path.moveTo(cx, h)
    val steps = 60
    for (i in 1..steps) {
        val t = i / steps.toFloat()
        val y = h - (disc.speed / 14f) * h * t
        val turnPhase = sin(PI * t).toFloat() * disc.turn / 5f
        val fadePhase = ((t * t) * disc.fade / 6f) * -1
        val xOffset = (turnPhase + fadePhase) * (w * 0.3f)
        path.lineTo(cx + xOffset, y)
    }
    return path
}
