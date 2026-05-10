package com.inknironapps.bagger.ui.components

import android.Manifest
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.inknironapps.bagger.di.PhotoStorageEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.io.File

/**
 * Reusable camera capture composable. Asks for camera permission, then
 * shows a CameraX preview with a centered capture button. Saves the photo
 * via PhotoStorage and invokes [onPhoto] with the resulting File.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraCapture(
    onPhoto: (File) -> Unit,
    processing: Boolean = false,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val perm = rememberPermissionState(Manifest.permission.CAMERA)
    when {
        perm.status.isGranted -> CameraView(onPhoto = onPhoto, processing = processing, modifier = modifier)
        else -> PermissionPrompt(onGrant = { perm.launchPermissionRequest() }, modifier = modifier)
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit, modifier: Modifier) {
    Column(
        modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Camera permission needed", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("To capture a disc photo, Bagger needs to use your camera.")
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGrant) { Text("Grant permission") }
    }
}

@Composable
private fun CameraView(onPhoto: (File) -> Unit, processing: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val photoStorage = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            PhotoStorageEntryPoint::class.java
        ).photoStorage()
    }

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = androidx.camera.core.Preview.Builder().build().also {
                            it.setSurfaceProvider(surfaceProvider)
                        }
                        val capture = ImageCapture.Builder().build()
                        imageCapture = capture
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                capture
                            )
                        } catch (_: Exception) {
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            }
        )

        FilledIconButton(
            onClick = {
                val capture = imageCapture ?: return@FilledIconButton
                val file = photoStorage.newPhotoFile()
                val output = ImageCapture.OutputFileOptions.Builder(file).build()
                capture.takePicture(
                    output,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                            onPhoto(file)
                        }

                        override fun onError(exception: ImageCaptureException) {}
                    }
                )
            },
            enabled = !processing,
            modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp).size(72.dp)
        ) {
            Icon(Icons.Filled.Camera, "Capture", modifier = Modifier.size(36.dp))
        }

        if (processing) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
