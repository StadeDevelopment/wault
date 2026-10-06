package net.wault.sync

import net.wault.item.ItemContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PairingProtocolTest {

    private val time = AtomicLong(1_000L)
    private val opened = mutableListOf<SyncNode>()

    @AfterTest
    fun tearDown() {
        opened.forEach { it.close() }
        opened.clear()
    }

    private fun newNode(label: String, scope: CoroutineScope, withVault: Boolean = true): SyncNode {
        val node = SyncNode(label, scope, time)
        node.vault.setup("master password for $label")
        opened.add(node)
        return node
    }

    private fun protocolFor(node: SyncNode) =
        PairingProtocol(node.pairing, node.devices, node.vault)

    private fun offerFor(node: SyncNode, vaultId: String) =
        node.pairing.createOffer(vaultId, node.devices.localDevice(), null, "lan://127.0.0.1:1")

    @Test
    fun `two devices pair and end up holding the same data key`() = runTest {
        val host = newNode("host", this)
        val joiner = newNode("joiner", this, withVault = false)

        host.items.load()
        host.items.create(ItemContent.Login(title = "GitHub", username = "eren", password = "hunter-two"))

        val vaultId = host.vault.vaultId()!!
        val hostOffer = offerFor(host, vaultId)
        val joinerOffer = offerFor(joiner, vaultId)

        val (hostPipe, joinerPipe) = connectedPipes()
        val outcomes = listOf(
            async { protocolFor(host).host(hostPipe, hostOffer) { true } },
            async {
                protocolFor(joiner).join(joinerPipe, hostOffer, joinerOffer, "master password for joiner") { true }
            }
        ).awaitAll()

        assertTrue(outcomes.all { it is PairingOutcome.Paired }, "outcomes were $outcomes")

        assertContentEquals(
            host.vault.withDataKey { it.copyOf() },
            joiner.vault.withDataKey { it.copyOf() }
        )
    }

    @Test
    fun `both sides see the same short authentication string`() = runTest {
        val host = newNode("host", this)
        val joiner = newNode("joiner", this, withVault = false)
        val vaultId = host.vault.vaultId()!!
        val hostOffer = offerFor(host, vaultId)
        val joinerOffer = offerFor(joiner, vaultId)

        var hostSas: String? = null
        var joinerSas: String? = null

        val (hostPipe, joinerPipe) = connectedPipes()
        listOf(
            async { protocolFor(host).host(hostPipe, hostOffer) { hostSas = it; true } },
            async {
                protocolFor(joiner).join(joinerPipe, hostOffer, joinerOffer, "master password for joiner") {
                    joinerSas = it; true
                }
            }
        ).awaitAll()

        assertNotNull(hostSas)
        assertEquals(hostSas, joinerSas)
        assertEquals(6, hostSas.length)
    }

    @Test
    fun `after pairing the two devices can sync`() = runTest {
        val host = newNode("host", this)
        val joiner = newNode("joiner", this, withVault = false)

        host.items.load()
        host.items.create(ItemContent.Note(title = "Recovery codes", body = "abc-def"))

        val vaultId = host.vault.vaultId()!!
        val hostOffer = offerFor(host, vaultId)
        val joinerOffer = offerFor(joiner, vaultId)

        val (hostPipe, joinerPipe) = connectedPipes()
        listOf(
            async { protocolFor(host).host(hostPipe, hostOffer) { true } },
            async {
                protocolFor(joiner).join(joinerPipe, hostOffer, joinerOffer, "master password for joiner") { true }
            }
        ).awaitAll()

        joiner.items.load()

        val (a, b) = connectedPipes()
        val synced = listOf(
            async { host.service.exchange(a, joiner.deviceId, initiator = true) },
            async { joiner.service.exchange(b, host.deviceId, initiator = false) }
        ).awaitAll()

        assertTrue(synced.all { it }, "sync exchange failed after pairing")
        assertEquals("Recovery codes", joiner.items.items.value.single().title)
    }

    @Test
    fun `declining on the host aborts and pairs nothing`() = runTest {
        val host = newNode("host", this)
        val joiner = newNode("joiner", this, withVault = false)
        val vaultId = host.vault.vaultId()!!
        val hostOffer = offerFor(host, vaultId)
        val joinerOffer = offerFor(joiner, vaultId)

        val (hostPipe, joinerPipe) = connectedPipes()
        val outcomes = listOf(
            async { protocolFor(host).host(hostPipe, hostOffer) { false } },
            async {
                protocolFor(joiner).join(joinerPipe, hostOffer, joinerOffer, "master password for joiner") { true }
            }
        ).awaitAll()

        assertIs<PairingOutcome.Declined>(outcomes[0])
        assertIs<PairingOutcome.PeerDeclined>(outcomes[1])
        assertTrue(host.devices.peers().isEmpty())
        assertTrue(joiner.devices.peers().isEmpty())
    }

    @Test
    fun `declining on the joiner aborts and pairs nothing`() = runTest {
        val host = newNode("host", this)
        val joiner = newNode("joiner", this, withVault = false)
        val vaultId = host.vault.vaultId()!!
        val hostOffer = offerFor(host, vaultId)
        val joinerOffer = offerFor(joiner, vaultId)

        val (hostPipe, joinerPipe) = connectedPipes()
        val outcomes = listOf(
            async { protocolFor(host).host(hostPipe, hostOffer) { true } },
            async {
                protocolFor(joiner).join(joinerPipe, hostOffer, joinerOffer, "master password for joiner") { false }
            }
        ).awaitAll()

        assertIs<PairingOutcome.PeerDeclined>(outcomes[0])
        assertIs<PairingOutcome.Declined>(outcomes[1])
        assertTrue(host.devices.peers().isEmpty())
        assertTrue(joiner.devices.peers().isEmpty())
    }

    @Test
    fun `an impostor offering different keys produces a different short auth string`() = runTest {
        val host = newNode("host", this)
        val joiner = newNode("joiner", this, withVault = false)
        val impostor = newNode("impostor", this, withVault = false)
        val vaultId = host.vault.vaultId()!!

        val hostOffer = offerFor(host, vaultId)
        val impostorOffer = offerFor(impostor, vaultId)
        val joinerOffer = offerFor(joiner, vaultId)

        var honestSas: String? = null
        var attackedSas: String? = null

        val (hostPipe, joinerPipe) = connectedPipes()
        listOf(
            async { protocolFor(host).host(hostPipe, hostOffer) { honestSas = it; true } },
            async {
                protocolFor(joiner).join(joinerPipe, hostOffer, joinerOffer, "master password for joiner") { true }
            }
        ).awaitAll()

        val (impostorPipe, victimPipe) = connectedPipes()
        val victim = newNode("victim", this, withVault = false)
        val victimOffer = offerFor(victim, vaultId)
        listOf(
            async { protocolFor(impostor).host(impostorPipe, impostorOffer) { true } },
            async {
                protocolFor(victim).join(victimPipe, impostorOffer, victimOffer, "master password for victim") {
                    attackedSas = it; true
                }
            }
        ).awaitAll()

        assertNotNull(honestSas)
        assertNotNull(attackedSas)
        assertTrue(honestSas != attackedSas, "a swapped peer must change the number the user compares")
    }

    @Test
    fun `pairing a device with itself is refused`() = runTest {
        val node = newNode("solo", this)
        val offer = offerFor(node, node.vault.vaultId()!!)

        val (a, b) = connectedPipes()
        val outcomes = listOf(
            async { protocolFor(node).host(a, offer) { true } },
            async { protocolFor(node).join(b, offer, offer, "master password for solo") { true } }
        ).awaitAll()

        assertTrue(outcomes.any { it is PairingOutcome.Failed })
        assertTrue(node.devices.peers().isEmpty())
    }

    @Test
    fun `the pairing code round-trips through a qr payload`() = runTest {
        val host = newNode("host", this)
        val offer = offerFor(host, host.vault.vaultId()!!)

        val payload = host.pairing.encodeOffer(offer)
        val matrix = net.wault.qr.encodeQr(payload)
        assertNotNull(matrix, "the pairing payload must fit in a QR code")
        assertTrue(matrix.size > 0)

        val decoded = host.pairing.decodeOffer(payload)
        assertNotNull(decoded)
        assertEquals(offer.deviceId, decoded.deviceId)
        assertContentEquals(offer.pairingNonce, decoded.pairingNonce)
    }

    @Test
    fun `garbage is not accepted as a pairing code`() = runTest {
        val host = newNode("host", this)
        assertNull(host.pairing.decodeOffer("https://example.com/not-a-pairing-code"))
    }
}
