package net.wault.sync

import net.wault.transport.BaseTransport
import net.wault.transport.Connection
import net.wault.transport.TransportType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeLanTransport(private val advertised: String) :
    BaseTransport(TransportType.LAN, "Fake LAN") {
    override suspend fun start(handler: suspend (Connection) -> Unit) = Unit
    override suspend fun stop() = Unit
    override suspend fun connect(address: String): Connection? = null
    override fun selfAddress(): String? = advertised
}

@OptIn(ExperimentalCoroutinesApi::class)
class SyncAddressRefreshTest {

    private val time = AtomicLong(1_000L)
    private val opened = mutableListOf<SyncNode>()

    @AfterTest
    fun tearDown() {
        opened.forEach { it.close() }
        opened.clear()
    }

    private fun newNode(label: String, scope: CoroutineScope, advertise: String?): SyncNode {
        val node = SyncNode(label, scope, time)
        node.vault.setup("master password for $label")
        advertise?.let { node.transports.register(FakeLanTransport(it)) }
        opened.add(node)
        return node
    }

    private fun linkVaults(source: SyncNode, target: SyncNode) {
        val dataKey = source.vault.withDataKey { it.copyOf() }
        target.vault.adoptDataKey(dataKey, "master password for ${target.label}")
    }

    private suspend fun runExchange(
        initiator: SyncNode,
        responder: SyncNode,
        scope: CoroutineScope
    ): Boolean {
        val (initiatorPipe, responderPipe) = connectedPipes()
        return listOf(
            scope.async { initiator.service.exchange(initiatorPipe, responder.deviceId, initiator = true) },
            scope.async { responder.service.exchange(responderPipe, initiator.deviceId, initiator = false) }
        ).awaitAll().all { it }
    }

    @Test
    fun `peers learn each other's current address from a sync`() = runTest {
        val alice = newNode("alice", this, "lan://192.168.1.10:7901")
        val bob = newNode("bob", this, "lan://192.168.1.22:7901")
        linkVaults(alice, bob)
        pair(alice, bob)

        assertNull(
            alice.devices.peer(bob.deviceId)?.lanAddress,
            "pairing in this harness starts with no address, which is what goes stale in the field"
        )

        assertTrue(runExchange(alice, bob, this))

        assertEquals(
            "lan://192.168.1.22:7901",
            alice.devices.peer(bob.deviceId)?.lanAddress,
            "alice must record where bob can now be reached"
        )
        assertEquals(
            "lan://192.168.1.10:7901",
            bob.devices.peer(alice.deviceId)?.lanAddress,
            "the responder must learn the initiator's address too"
        )
    }

    @Test
    fun `a changed address overwrites the stale one`() = runTest {
        val alice = newNode("alice", this, "lan://192.168.1.10:7901")
        val bob = newNode("bob", this, "lan://192.168.1.22:7901")
        linkVaults(alice, bob)
        pair(alice, bob)

        assertTrue(runExchange(alice, bob, this))
        assertEquals("lan://192.168.1.22:7901", alice.devices.peer(bob.deviceId)?.lanAddress)

        bob.transports.register(FakeLanTransport("lan://10.0.0.5:7901"))
        assertTrue(runExchange(alice, bob, this))

        assertEquals(
            "lan://10.0.0.5:7901",
            alice.devices.peer(bob.deviceId)?.lanAddress,
            "after bob moves networks alice must follow him, not keep dialling the old IP"
        )
    }

    @Test
    fun `a peer with no address is reported instead of failing silently`() = runTest {
        val alice = newNode("alice", this, null)
        val bob = newNode("bob", this, null)
        linkVaults(alice, bob)
        pair(alice, bob)

        alice.service.syncNow()
        testScheduler.advanceUntilIdle()

        assertEquals(
            SyncOutcome.NoKnownAddress,
            alice.service.statusOf(bob.deviceId).outcome,
            "a peer we have no address for must surface a reason, not silently do nothing"
        )
    }

    @Test
    fun `a successful exchange is reported as healthy`() = runTest {
        val alice = newNode("alice", this, "lan://192.168.1.10:7901")
        val bob = newNode("bob", this, "lan://192.168.1.22:7901")
        linkVaults(alice, bob)
        pair(alice, bob)

        assertTrue(runExchange(alice, bob, this))

        assertTrue(
            bob.service.statusOf(alice.deviceId).isHealthy,
            "an inbound sync that completed must show as succeeded"
        )
        assertTrue(bob.service.statusOf(alice.deviceId).hasSynced)
    }
}
