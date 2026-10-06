package net.wault.sync

import net.wault.crypto.platformCrypto
import net.wault.device.LocalDevice
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PairingServiceTest {

    private val crypto = platformCrypto()
    private val pairing = PairingService(crypto)

    private fun device(id: String) = LocalDevice(
        id = id,
        label = "Device $id",
        platform = "desktop",
        signing = crypto.generateSigningKeyPair(),
        agreement = crypto.generateAgreementKeyPair()
    )

    @Test
    fun `both sides derive the same session key`() {
        val alice = device("alice")
        val bob = device("bob")

        val alicesOffer = pairing.createOffer("vault-1", alice, null, null)
        val bobsOffer = pairing.createOffer("vault-1", bob, null, null)

        val onAlice = pairing.sessionKey(alice, bobsOffer, alicesOffer.pairingNonce)
        val onBob = pairing.sessionKey(bob, alicesOffer, bobsOffer.pairingNonce)

        assertContentEquals(onAlice, onBob)
    }

    @Test
    fun `both sides show the same short auth string`() {
        val alice = device("alice")
        val bob = device("bob")
        val alicesOffer = pairing.createOffer("vault-1", alice, null, null)
        val bobsOffer = pairing.createOffer("vault-1", bob, null, null)

        val sessionKey = pairing.sessionKey(alice, bobsOffer, alicesOffer.pairingNonce)

        val onAlice = pairing.shortAuthString(sessionKey, alicesOffer, bobsOffer)
        val onBob = pairing.shortAuthString(sessionKey, bobsOffer, alicesOffer)

        assertEquals(onAlice, onBob)
        assertEquals(6, onAlice.length)
        assertTrue(onAlice.all { it.isDigit() })
    }

    @Test
    fun `an interposed device produces a different short auth string`() {
        val alice = device("alice")
        val bob = device("bob")
        val mallory = device("mallory")

        val alicesOffer = pairing.createOffer("vault-1", alice, null, null)
        val bobsOffer = pairing.createOffer("vault-1", bob, null, null)
        val mallorysOffer = pairing.createOffer("vault-1", mallory, null, null)

        val honest = pairing.sessionKey(alice, bobsOffer, alicesOffer.pairingNonce)
        val attacked = pairing.sessionKey(alice, mallorysOffer, alicesOffer.pairingNonce)

        assertTrue(
            pairing.shortAuthString(honest, alicesOffer, bobsOffer) !=
                pairing.shortAuthString(attacked, alicesOffer, mallorysOffer)
        )
    }

    @Test
    fun `the data key travels sealed and opens on the far side`() {
        val alice = device("alice")
        val bob = device("bob")
        val alicesOffer = pairing.createOffer("vault-1", alice, null, null)
        val bobsOffer = pairing.createOffer("vault-1", bob, null, null)

        val sessionKey = pairing.sessionKey(alice, bobsOffer, alicesOffer.pairingNonce)
        val transcript = pairing.transcript(alicesOffer, bobsOffer)
        val dataKey = crypto.randomBytes(32)

        val sealed = pairing.sealDataKey(sessionKey, dataKey, transcript)
        assertTrue(sealed.size > dataKey.size)

        val bobsSessionKey = pairing.sessionKey(bob, alicesOffer, bobsOffer.pairingNonce)
        val opened = pairing.openDataKey(bobsSessionKey, sealed, pairing.transcript(bobsOffer, alicesOffer))

        assertNotNull(opened)
        assertContentEquals(dataKey, opened)
    }

    @Test
    fun `a wrong transcript cannot open the data key`() {
        val alice = device("alice")
        val bob = device("bob")
        val alicesOffer = pairing.createOffer("vault-1", alice, null, null)
        val bobsOffer = pairing.createOffer("vault-1", bob, null, null)

        val sessionKey = pairing.sessionKey(alice, bobsOffer, alicesOffer.pairingNonce)
        val sealed = pairing.sealDataKey(sessionKey, crypto.randomBytes(32), pairing.transcript(alicesOffer, bobsOffer))

        assertNull(pairing.openDataKey(sessionKey, sealed, "tampered".encodeToByteArray()))
    }

    @Test
    fun `an offer round-trips through its qr encoding`() {
        val alice = device("alice")
        val offer = pairing.createOffer("vault-1", alice, "abc.onion", "192.168.1.5:7700")

        val decoded = pairing.decodeOffer(pairing.encodeOffer(offer))

        assertNotNull(decoded)
        assertEquals(offer.vaultId, decoded.vaultId)
        assertEquals(offer.deviceId, decoded.deviceId)
        assertEquals("abc.onion", decoded.onionAddress)
        assertContentEquals(offer.agreementKey, decoded.agreementKey)
        assertContentEquals(offer.pairingNonce, decoded.pairingNonce)
    }

    @Test
    fun `garbage does not decode into an offer`() {
        assertNull(pairing.decodeOffer("https://example.com"))
    }
}
