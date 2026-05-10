package com.inknironapps.bagger.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.io.File

/**
 * Full-screen photo viewer with pinch-zoom and tap-to-dismiss.
 *
 * Pass a non-null [photoPath] to show. Tapping the close button or outside
 * the dialog dismisses via [onDismiss].
 */
@Composable
fun PhotoLightbox(photoPath: String?, onDismiss: () -> Unit) {
    AnimatedVisibility(visible = photoPath != null, enter = fadeIn(), exit = fadeOut()) {
        val path = photoPath ?: return@AnimatedVisibility
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = true
            )
        ) {
            var scale by remember { mutableStateOf(1f) }
            var offsetX by remember { mutableStateOf(0f) }
            var offsetY by remember { mutableStateOf(0f) }

            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
            ) {
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}
