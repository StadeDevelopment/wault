package net.wault.totp

import net.wault.crypto.platformCrypto
import net.wault.item.TotpAlgorithm
import net.wault.item.TotpSecret
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private const val RFC_SECRET_BASE32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

class TotpTest {

    private val totp = Totp(platformCrypto())

    private fun secret(digits: Int = 8, algorithm: TotpAlgorithm = TotpAlgorithm.Sha1) =
        TotpSecret(secret = RFC_SECRET_BASE32, digits = digits, periodSeconds = 30, algorithm = algorithm)

    @Test
    fun `rfc 6238 vector at t equals 59`() {
        assertEquals("94287082", totp.generate(secret(), nowMillis = 59_000L).code)
    }

    @Test
    fun `rfc 6238 vector at t equals 1111111109`() {
        assertEquals("07081804", totp.generate(secret(), nowMillis = 1_111_111_109_000L).code)
    }

    @Test
    fun `rfc 6238 vector at t equals 1234567890`() {
        assertEquals("89005924", totp.generate(secret(), nowMillis = 1_234_567_890_000L).code)
    }

    @Test
    fun `six digit codes are six characters`() {
        val code = totp.generate(secret(digits = 6), nowMillis = 59_000L)
        assertEquals(6, code.code.length)
    }

    @Test
    fun `seconds remaining counts down within the period`() {
        assertEquals(1, totp.generate(secret(), nowMillis = 59_000L).secondsRemaining)
        assertEquals(30, totp.generate(secret(), nowMillis = 60_000L).secondsRemaining)
    }

    @Test
    fun `otpauth uri round-trips`() {
        val original = TotpSecret(
            secret = RFC_SECRET_BASE32,
            issuer = "GitHub",
            account = "eren@stade.dev",
            digits = 6,
            periodSeconds = 30,
            algorithm = TotpAlgorithm.Sha256
        )
        val parsed = OtpAuthUri.parse(OtpAuthUri.format(original))

        assertNotNull(parsed)
        assertEquals(original.secret, parsed.secret)
        assertEquals(original.issuer, parsed.issuer)
        assertEquals(original.account, parsed.account)
        assertEquals(original.digits, parsed.digits)
        assertEquals(original.algorithm, parsed.algorithm)
    }

    @Test
    fun `a non-otpauth uri is rejected`() {
        assertNull(OtpAuthUri.parse("https://example.com"))
    }

    @Test
    fun `issuer is taken from the label when the parameter is absent`() {
        val parsed = OtpAuthUri.parse("otpauth://totp/GitHub:eren?secret=$RFC_SECRET_BASE32")
        assertNotNull(parsed)
        assertEquals("GitHub", parsed.issuer)
        assertEquals("eren", parsed.account)
    }
}
