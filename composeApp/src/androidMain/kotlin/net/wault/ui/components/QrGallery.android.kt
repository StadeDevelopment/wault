package net.wault.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.wault.qr.decodeQr

private const val MAX_EDGE = 2048

actual val isGalleryScanSupported: Boolean = true

@Composable
actual fun rememberQrGalleryPicker(
    onResult: (String?) -> Unit,
    onExternalFlow: (Boolean) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        onExternalFlow(false)
        if (uri == null) {
            onResult(null)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val decoded = withContext(Dispatchers.Default) {
                runCatching { decodeFromUri(context, uri) }.getOrNull()
            }
            onResult(decoded)
        }
    }

    return {
        onExternalFlow(true)
        runCatching {
            launcher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }.onFailure {
            onExternalFlow(false)
            onResult(null)
        }
    }
}

private fun decodeFromUri(context: android.content.Context, uri: Uri): String? {
    val bitmap = loadDownsampled(context, uri) ?: return null
    return try {
        decodeBitmap(bitmap)
    } finally {
        bitmap.recycle()
    }
}

private fun loadDownsampled(context: android.content.Context, uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

    val longestEdge = maxOf(bounds.outWidth, bounds.outHeight)
    if (longestEdge <= 0) return null

    var sample = 1
    while (longestEdge / sample > MAX_EDGE) sample *= 2

    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    }
}

private fun decodeBitmap(bitmap: Bitmap): String? {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return null

    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val luminances = ByteArray(width * height)
    for (index in pixels.indices) {
        val pixel = pixels[index]
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        luminances[index] = ((r * 299 + g * 587 + b * 114) / 1000).toByte()
    }
    return decodeQr(luminances, width, height)
}
