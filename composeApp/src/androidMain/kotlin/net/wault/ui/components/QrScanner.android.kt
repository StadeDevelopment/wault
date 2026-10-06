package net.wault.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import net.wault.qr.decodeQr
import net.wault.ui.i18n.LocalStrings
import java.util.concurrent.Executors

actual val isCameraScanSupported: Boolean = true

@Composable
actual fun QrScanner(
    onScanned: (String) -> Unit,
    onPermissionFlow: (Boolean) -> Unit,
    modifier: Modifier
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        granted = result
        onPermissionFlow(false)
    }

    if (!granted) {
        Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
            Button(onClick = {
                onPermissionFlow(true)
                launcher.launch(Manifest.permission.CAMERA)
            }) {
                Text(strings.pairGrantCamera)
            }
        }
        return
    }

    val executor = remember { Executors.newSingleThreadExecutor() }
    var delivered by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { viewContext ->
            val previewView = PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val providerFuture = ProcessCameraProvider.getInstance(viewContext)
            providerFuture.addListener({
                val provider = runCatching { providerFuture.get() }.getOrNull()
                    ?: return@addListener

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(executor) { image ->
                    if (!delivered) {
                        val text = readQr(image)
                        if (text != null) {
                            delivered = true
                            ContextCompat.getMainExecutor(viewContext).execute { onScanned(text) }
                        }
                    }
                    image.close()
                }

                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }
            }, ContextCompat.getMainExecutor(viewContext))
            previewView
        }
    )
}

private fun readQr(image: ImageProxy): String? {
    val plane = image.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val width = image.width
    val height = image.height

    val luminances = ByteArray(width * height)
    if (rowStride == width) {
        buffer.get(luminances, 0, minOf(buffer.remaining(), luminances.size))
    } else {
        val row = ByteArray(rowStride)
        var offset = 0
        for (y in 0 until height) {
            if (buffer.remaining() < rowStride) break
            buffer.get(row, 0, rowStride)
            val copy = minOf(width, luminances.size - offset)
            if (copy <= 0) break
            row.copyInto(luminances, offset, 0, copy)
            offset += width
        }
    }

    return decodeQr(luminances, width, height)
}
