package net.wault.totp

import net.wault.crypto.base32ToBytes
import net.wault.item.TotpAlgorithm
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class TotpImportTest {

    private fun varint(value: Int): ByteArray {
        var v = value
        val out = ArrayList<Byte>()
        while (true) {
            val b = v and 0x7F
            v = v ushr 7
            if (v == 0) {
                out.add(b.toByte()); break
            }
            out.add((b or 0x80).toByte())
        }
        return out.toByteArray()
    }

    private fun lengthDelimited(field: Int, payload: ByteArray): ByteArray =
        varint((field shl 3) or 2) + varint(payload.size) + payload

    private fun varintField(field: Int, value: Int): ByteArray =
        varint((field shl 3) or 0) + varint(value)

    /** Builds a Google Authenticator migration payload the same way the real export does. */
    private fun googlePayload(
        secret: ByteArray,
        name: String,
        issuer: String,
        algorithm: Int = 1,
        digits: Int = 1,
        type: Int = 2
    ): String {
        val params = lengthDelimited(1, secret) +
            lengthDelimited(2, name.encodeToByteArray()) +
            lengthDelimited(3, issuer.encodeToByteArray()) +
            varintField(4, algorithm) +
            varintField(5, digits) +
            varintField(6, type)
        val body = lengthDelimited(1, params)
        val encoded = Base64.Default.encode(body)
        val escaped = encoded.replace("+", "%2B").replace("/", "%2F").replace("=", "%3D")
        return "otpauth-migration://offline?data=$escaped"
    }

    @Test
    fun `a single otpauth uri imports`() {
        val preview = TotpImport.preview("otpauth://totp/GitHub:eren?secret=JBSWY3DPEHPK3PXP&issuer=GitHub")
        assertNotNull(preview)
        assertEquals(TotpImportFormat.OtpAuthUri, preview.format)
        assertEquals(1, preview.codes.size)
        assertEquals("JBSWY3DPEHPK3PXP", preview.codes[0].secret.secret)
        assertEquals("GitHub", preview.codes[0].secret.issuer)
    }

    @Test
    fun `several uris on separate lines all import`() {
        val payload = """
            otpauth://totp/One:a?secret=JBSWY3DPEHPK3PXP&issuer=One
            otpauth://totp/Two:b?secret=KRSXG5CTMVRXEZLU&issuer=Two
        """.trimIndent()
        val preview = TotpImport.preview(payload)
        assertNotNull(preview)
        assertEquals(2, preview.codes.size)
    }

    @Test
    fun `a google authenticator export decodes to the same secret`() {
        val raw = "JBSWY3DPEHPK3PXP".base32ToBytes()
        val preview = TotpImport.preview(googlePayload(raw, "eren", "GitHub"))

        assertNotNull(preview)
        assertEquals(TotpImportFormat.GoogleAuthenticator, preview.format)
        assertEquals(1, preview.codes.size)

        val imported = preview.codes[0].secret
        assertEquals("JBSWY3DPEHPK3PXP", imported.secret)
        assertEquals("GitHub", imported.issuer)
        assertEquals("eren", imported.account)
        assertEquals(6, imported.digits)
        assertEquals(TotpAlgorithm.Sha1, imported.algorithm)
    }

    @Test
    fun `a google export carries sha256 and eight digits through`() {
        val raw = "JBSWY3DPEHPK3PXP".base32ToBytes()
        val preview = TotpImport.preview(
            googlePayload(raw, "a", "Svc", algorithm = 2, digits = 2)
        )
        assertNotNull(preview)
        val imported = preview.codes[0].secret
        assertEquals(TotpAlgorithm.Sha256, imported.algorithm)
        assertEquals(8, imported.digits)
    }

    @Test
    fun `a google export skips counter based entries`() {
        val raw = "JBSWY3DPEHPK3PXP".base32ToBytes()
        val preview = TotpImport.preview(googlePayload(raw, "a", "Svc", type = 1))
        assertNull(preview, "an HOTP-only export has nothing a TOTP vault can use")
    }

    @Test
    fun `an imported google secret still generates a working code`() {
        val raw = "JBSWY3DPEHPK3PXP".base32ToBytes()
        val preview = TotpImport.preview(googlePayload(raw, "eren", "GitHub"))
        assertNotNull(preview)

        val totp = Totp(net.wault.crypto.platformCrypto())
        val direct = totp.hotp(
            net.wault.item.TotpSecret(secret = "JBSWY3DPEHPK3PXP"),
            counter = 1
        )
        val roundTripped = totp.hotp(preview.codes[0].secret, counter = 1)

        assertEquals(
            direct,
            roundTripped,
            "a secret that survives export and import must produce the same code"
        )
    }

    @Test
    fun `an aegis backup imports`() {
        val payload = """
            {"version":1,"header":{},"db":{"version":2,"entries":[
              {"type":"totp","uuid":"x","name":"eren","issuer":"GitHub","info":{"secret":"JBSWY3DPEHPK3PXP","algo":"SHA1","digits":6,"period":30}}
            ]}}
        """.trimIndent()
        val preview = TotpImport.preview(payload)
        assertNotNull(preview)
        assertEquals(TotpImportFormat.Aegis, preview.format)
        assertEquals("JBSWY3DPEHPK3PXP", preview.codes[0].secret.secret)
        assertEquals("GitHub", preview.codes[0].secret.issuer)
    }

    @Test
    fun `a 2fas backup imports`() {
        val payload = """
            {"services":[{"name":"GitHub","secret":"JBSWY3DPEHPK3PXP","otp":{"issuer":"GitHub","digits":6,"period":30}}],"schemaVersion":4}
        """.trimIndent()
        val preview = TotpImport.preview(payload)
        assertNotNull(preview)
        assertEquals(TotpImportFormat.TwoFas, preview.format)
        assertEquals("JBSWY3DPEHPK3PXP", preview.codes[0].secret.secret)
    }

    @Test
    fun `secrets are normalised so spaced keys still work`() {
        val payload = """[{"secret":"jbsw y3dp ehpk 3pxp","issuer":"Svc","name":"a"}]"""
        val preview = TotpImport.preview(payload)
        assertNotNull(preview)
        assertEquals("JBSWY3DPEHPK3PXP", preview.codes[0].secret.secret)
    }

    @Test
    fun `labels fall back sensibly`() {
        val preview = TotpImport.preview("otpauth://totp/JustAnAccount?secret=JBSWY3DPEHPK3PXP")
        assertNotNull(preview)
        assertTrue(preview.codes[0].label.isNotBlank())
    }

    @Test
    fun `junk is refused rather than imported as an empty list`() {
        assertNull(TotpImport.preview(""))
        assertNull(TotpImport.preview("hello world"))
        assertNull(TotpImport.preview("https://example.com"))
        assertNull(TotpImport.preview("otpauth-migration://offline?data=not-base64!!"))
    }

    @Test
    fun `a truncated google payload is refused rather than crashing`() {
        val raw = "JBSWY3DPEHPK3PXP".base32ToBytes()
        val full = googlePayload(raw, "eren", "GitHub")
        val data = full.substringAfter("data=")
        val truncated = "otpauth-migration://offline?data=" + data.take(data.length / 2)
        TotpImport.preview(truncated)
    }
}
