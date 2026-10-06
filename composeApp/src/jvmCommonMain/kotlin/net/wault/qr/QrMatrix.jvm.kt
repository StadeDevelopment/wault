package net.wault.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

actual fun encodeQr(text: String): QrMatrix? = runCatching {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
        EncodeHintType.MARGIN to 0,
        EncodeHintType.CHARACTER_SET to "UTF-8"
    )
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
    val size = matrix.width
    val modules = BooleanArray(size * matrix.height)
    for (y in 0 until matrix.height) {
        for (x in 0 until size) {
            modules[y * size + x] = matrix.get(x, y)
        }
    }
    QrMatrix(size, modules)
}.getOrNull()

actual fun decodeQr(luminances: ByteArray, width: Int, height: Int): String? = runCatching {
    val source = PlanarYUVLuminanceSource(
        luminances, width, height, 0, 0, width, height, false
    )
    val bitmap = BinaryBitmap(HybridBinarizer(source))
    val hints = mapOf(DecodeHintType.TRY_HARDER to true)
    QRCodeReader().decode(bitmap, hints).text
}.getOrNull()
