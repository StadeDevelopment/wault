package net.wault.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.wault.qr.decodeQr
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

actual val isGalleryScanSupported: Boolean = true

@Composable
actual fun rememberQrGalleryPicker(
    onResult: (String?) -> Unit,
    onExternalFlow: (Boolean) -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()

    return {
        onExternalFlow(true)
        scope.launch {
            val decoded = withContext(Dispatchers.IO) {
                runCatching {
                    chooseImage()?.let { file -> decodeFile(file) }
                }.getOrNull()
            }
            onExternalFlow(false)
            onResult(decoded)
        }
        Unit
    }
}

private fun chooseImage(): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Select a QR code image"
        isMultiSelectionEnabled = false
        fileFilter = FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "bmp", "gif", "webp")
    }
    val outcome = chooser.showOpenDialog(null)
    if (outcome != JFileChooser.APPROVE_OPTION) return null
    return chooser.selectedFile
}

private fun decodeFile(file: File): String? {
    val image = runCatching { ImageIO.read(file) }.getOrNull() ?: return null
    return decodeImage(image)
}

internal fun decodeImage(image: BufferedImage): String? {
    val width = image.width
    val height = image.height
    if (width <= 0 || height <= 0) return null

    val pixels = image.getRGB(0, 0, width, height, null, 0, width)
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
