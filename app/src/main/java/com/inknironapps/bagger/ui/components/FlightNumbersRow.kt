package com.inknironapps.bagger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun FlightNumbersRow(speed: Float, glide: Float, turn: Float, fade: Float, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlightCircle(speed.formatFlight(), MaterialTheme.colorScheme.primary)
        FlightCircle(glide.formatFlight(), MaterialTheme.colorScheme.secondary)
        FlightCircle(turn.formatFlight(), MaterialTheme.colorScheme.tertiary)
        FlightCircle(fade.formatFlight(), MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun FlightCircle(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier.size(28.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun Float.formatFlight(): String =
    if (this == this.toInt().toFloat()) this.toInt().toString() else "%.1f".format(this)
