package com.inknironapps.bagger.ui.screens.add_disc

import androidx.compose.runtime.Composable
import com.inknironapps.bagger.ui.components.CameraCapture
import java.io.File

/**
 * Thin delegate over the shared [CameraCapture] composable. Kept as a
 * separate symbol so AddDiscRoute can continue referencing CameraStep.
 */
@Composable
fun CameraStep(onPhoto: (File) -> Unit, processing: Boolean) {
    CameraCapture(onPhoto = onPhoto, processing = processing)
}
