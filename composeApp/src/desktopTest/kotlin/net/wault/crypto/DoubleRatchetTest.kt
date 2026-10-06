package net.wault.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DoubleRatchetTest {

    private val crypto = platformCrypto()
    private val pq = platformPq()
    private val ratchet = DoubleRatchet(crypto, pq)

    private fun pair(): Pair<DoubleRatchet.State, DoubleRatchet.State> {
        val root = crypto.randomBytes(32)
        val alice = crypto.generateAgreementKeyPair()
        val bob = crypto.generateAgreementKeyPair()
        val aliceState = ratchet.initSymmetric(root, alice, bob.publicKey, isAlice = true)
        val bobState = ratchet.initSymmetric(root, bob, alice.publicKey, isAlice = false)
        return aliceState to bobState
    }

    @Test
    fun `a message round-trips`() {
        val (alice, bob) = pair()
        val plaintext = "vault delta".encodeToByteArray()

        val frame = ratchet.encrypt(alice, plaintext)
        assertContentEquals(plaintext, ratchet.decrypt(bob, frame))
    }

    @Test
    fun `the ciphertext does not contain the plaintext`() {
        val (alice, _) = pair()
        val frame = ratchet.encrypt(alice, "s3cr3t-value".encodeToByteArray())
        assertTrue(!frame.decodeToString().contains("s3cr3t-value"))
    }

    @Test
    fun `successive messages use different keys`() {
        val (alice, bob) = pair()
        val first = ratchet.encrypt(alice, "one".encodeToByteArray())
        val second = ratchet.encrypt(alice, "one".encodeToByteArray())

        assertTrue(!first.contentEquals(second))
        assertContentEquals("one".encodeToByteArray(), ratchet.decrypt(bob, first))
        assertContentEquals("one".encodeToByteArray(), ratchet.decrypt(bob, second))
    }

    @Test
    fun `out-of-order delivery still decrypts`() {
        val (alice, bob) = pair()
        val first = ratchet.encrypt(alice, "first".encodeToByteArray())
        val second = ratchet.encrypt(alice, "second".encodeToByteArray())
        val third = ratchet.encrypt(alice, "third".encodeToByteArray())

        assertContentEquals("third".encodeToByteArray(), ratchet.decrypt(bob, third))
        assertContentEquals("first".encodeToByteArray(), ratchet.decrypt(bob, first))
        assertContentEquals("second".encodeToByteArray(), ratchet.decrypt(bob, second))
    }

    @Test
    fun `a full back-and-forth conversation works`() {
        val (alice, bob) = pair()
        repeat(8) { round ->
            val fromAlice = ratchet.encrypt(alice, "a$round".encodeToByteArray())
            assertContentEquals("a$round".encodeToByteArray(), ratchet.decrypt(bob, fromAlice))

            val fromBob = ratchet.encrypt(bob, "b$round".encodeToByteArray())
            assertContentEquals("b$round".encodeToByteArray(), ratchet.decrypt(alice, fromBob))
        }
    }

    @Test
    fun `a replayed frame is rejected`() {
        val (alice, bob) = pair()
        val frame = ratchet.encrypt(alice, "once".encodeToByteArray())

        assertNotNull(ratchet.decrypt(bob, frame))
        assertNull(ratchet.decrypt(bob, frame))
    }

    @Test
    fun `a tampered frame is rejected and leaves state usable`() {
        val (alice, bob) = pair()
        val good = ratchet.encrypt(alice, "intact".encodeToByteArray())
        val tampered = good.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 0x01).toByte() }

        assertNull(ratchet.decrypt(bob, tampered))
        assertContentEquals("intact".encodeToByteArray(), ratchet.decrypt(bob, good))
    }

    @Test
    fun `a stranger with a different root cannot decrypt`() {
        val (alice, _) = pair()
        val (_, stranger) = pair()
        val frame = ratchet.encrypt(alice, "private".encodeToByteArray())

        assertNull(ratchet.decrypt(stranger, frame))
    }

    @Test
    fun `associated data must match`() {
        val (alice, bob) = pair()
        val frame = ratchet.encrypt(alice, "bound".encodeToByteArray(), "context-a".encodeToByteArray())

        assertNull(ratchet.decrypt(bob, frame, "context-b".encodeToByteArray()))
    }

    @Test
    fun `state survives a snapshot round-trip`() {
        val (alice, bob) = pair()
        ratchet.encrypt(alice, "warm up".encodeToByteArray()).let { ratchet.decrypt(bob, it) }

        val snapshot = RatchetSerializer.toSnapshot(bob)
        val restored = RatchetSerializer.fromSnapshot(snapshot)

        val frame = ratchet.encrypt(alice, "after restore".encodeToByteArray())
        assertContentEquals("after restore".encodeToByteArray(), ratchet.decrypt(restored, frame))
    }

    @Test
    fun `the post-quantum header carries ml-kem material`() {
        val (alice, _) = pair()
        val frame = ratchet.encrypt(alice, "pq".encodeToByteArray())
        val headerLength = ((frame[0].toInt() and 0xff) shl 24) or
            ((frame[1].toInt() and 0xff) shl 16) or
            ((frame[2].toInt() and 0xff) shl 8) or
            (frame[3].toInt() and 0xff)
        val header = DoubleRatchet.Header.decode(frame.copyOfRange(4, 4 + headerLength))

        assertNotNull(header)
        assertNotNull(header.mlkemPub)
        assertEquals(DoubleRatchet.MLKEM_PUB_LEN, header.mlkemPub.size)
    }
}
