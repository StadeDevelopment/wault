package net.wault.ui.components

import net.wault.qr.encodeQr
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class QrGalleryDecodeTest {

    private fun render(text: String, scale: Int = 8, quiet: Int = 4): BufferedImage {
        val matrix = assertNotNull(encodeQr(text), "the payload must be encodable as a QR code")
        val side = (matrix.size + quiet * 2) * scale
        val image = BufferedImage(side, side, BufferedImage.TYPE_INT_RGB)

        val canvas = image.createGraphics()
        canvas.color = Color.WHITE
        canvas.fillRect(0, 0, side, side)
        canvas.color = Color.BLACK
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (matrix[x, y]) {
                    canvas.fillRect((x + quiet) * scale, (y + quiet) * scale, scale, scale)
                }
            }
        }
        canvas.dispose()
        return image
    }

    @Test
    fun `a qr code picked from the gallery decodes back to its payload`() {
        val payload = "otpauth://totp/GitHub:eren?secret=JBSWY3DPEHPK3PXP&issuer=GitHub"
        assertEquals(payload, decodeImage(render(payload)))
    }

    @Test
    fun `a long google authenticator export image decodes`() {
        val payload = "otpauth-migration://offline?data=" +
            "CioKCkhlbGxvId6tvu8SDmVyZW5AZ21haWwuY29tGgZHb29nbGUgASgBMAIQARgBIAA%3D"
        assertEquals(payload, decodeImage(render(payload)))
    }

    @Test
    fun `a photo with no qr code in it returns nothing`() {
        val blank = BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB)
        val canvas = blank.createGraphics()
        canvas.color = Color(120, 180, 90)
        canvas.fillRect(0, 0, 600, 400)
        canvas.dispose()

        assertNull(decodeImage(blank), "a holiday snap must not be reported as a code")
    }

    @Test
    fun `a one pixel image is refused rather than crashing`() {
        assertNull(decodeImage(BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)))
    }

    @Test
    fun `a screenshot sized image still decodes`() {
        val payload = "otpauth://totp/Big:account?secret=JBSWY3DPEHPK3PXP"
        val code = render(payload, scale = 12, quiet = 6)

        val padded = BufferedImage(1080, 1920, BufferedImage.TYPE_INT_RGB)
        val canvas = padded.createGraphics()
        canvas.color = Color.WHITE
        canvas.fillRect(0, 0, 1080, 1920)
        canvas.drawImage(code, (1080 - code.width) / 2, (1920 - code.height) / 2, null)
        canvas.dispose()

        assertEquals(payload, decodeImage(padded), "a code inside a full screenshot must still be found")
    }
}
