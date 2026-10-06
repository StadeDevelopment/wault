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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RekeySyncTest {

    private val time = AtomicLong(1_000L)
    private val opened = mutableListOf<SyncNode>()

    @AfterTest
    fun tearDown() {
        opened.forEach { it.close() }
        opened.clear()
    }

    private fun newNode(label: String, scope: CoroutineScope): SyncNode {
        val node = SyncNode(label, scope, time)
        node.vault.setup("master password for $label")
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

    private fun setupPair(scope: CoroutineScope): Pair<SyncNode, SyncNode> {
        val alice = newNode("alice", scope)
        val bob = newNode("bob", scope)
        linkVaults(alice, bob)
        pair(alice, bob)
        alice.items.load()
        bob.items.load()
        return alice to bob
    }

    @Test
    fun `a rotated key reaches the other device and sync continues`() = runTest {
        val (alice, bob) = setupPair(this)

        alice.items.create(ItemContent.Note(title = "Before rotation", body = "x"))
        assertTrue(runExchange(alice, bob, this))
        assertEquals(1, bob.items.items.value.size)

        assertTrue(alice.rotation.rotate()!!.succeeded)
        assertEquals(1, alice.vault.keyGeneration())
        assertEquals(0, bob.vault.keyGeneration())

        alice.items.load()
        alice.items.create(ItemContent.Note(title = "After rotation", body = "y"))

        assertTrue(runExchange(alice, bob, this))

        assertEquals(1, bob.vault.keyGeneration())
        assertEquals(
            setOf("Before rotation", "After rotation"),
            bob.items.items.value.map { it.title }.toSet()
        )
        assertTrue(bob.items.undecodable.value.isEmpty())
    }

    @Test
    fun `the receiving device re-encrypts its own records to the new generation`() = runTest {
        val (alice, bob) = setupPair(this)

        bob.items.create(ItemContent.Login(title = "Bob's own", password = "secret"))
        assertTrue(runExchange(alice, bob, this))

        assertTrue(alice.rotation.rotate()!!.succeeded)
        alice.items.load()
        assertTrue(runExchange(alice, bob, this))

        val generations = bob.db.waultDbQueries.selectAllItemsIncludingDeleted().executeAsList()
            .mapNotNull { net.wault.crypto.Envelope.generationOf(it.payload) }
            .toSet()

        assertEquals(setOf(1), generations)
        assertFalse(bob.vault.isRotationPending())
        val login = bob.items.items.value.first { it.title == "Bob's own" }.content
        assertEquals("secret", assertIs<ItemContent.Login>(login).password)
    }

    @Test
    fun `both devices hold the same data key after rekey`() = runTest {
        val (alice, bob) = setupPair(this)

        assertTrue(alice.rotation.rotate()!!.succeeded)
        alice.items.load()
        assertTrue(runExchange(alice, bob, this))

        val aliceKey = alice.vault.withDataKey { it.copyOf() }
        val bobKey = bob.vault.withDataKey { it.copyOf() }
        assertTrue(aliceKey.contentEquals(bobKey))
    }

    @Test
    fun `a stale generation record self-heals on the next exchange`() = runTest {
        val (alice, bob) = setupPair(this)

        assertTrue(alice.rotation.rotate()!!.succeeded)
        alice.items.load()

        alice.devices.markKeyGeneration(bob.deviceId, 1)

        assertFalse(runExchange(alice, bob, this))
        assertEquals(0, alice.devices.peer(bob.deviceId)?.keyGeneration)

        alice.items.create(ItemContent.Note(title = "Healed", body = "z"))
        assertTrue(runExchange(alice, bob, this))

        assertEquals("Healed", bob.items.items.value.single().title)
        assertEquals(1, bob.vault.keyGeneration())
    }

    @Test
    fun `rotation after revoking keeps the remaining device in sync`() = runTest {
        val alice = newNode("alice", this)
        val bob = newNode("bob", this)
        val laptop = newNode("laptop", this)
        linkVaults(alice, bob)
        linkVaults(alice, laptop)
        pair(alice, bob)
        pair(alice, laptop)
        alice.items.load()
        bob.items.load()
        laptop.items.load()

        alice.items.create(ItemContent.Note(title = "Shared", body = "x"))
        assertTrue(runExchange(alice, bob, this))
        assertTrue(runExchange(alice, laptop, this))

        alice.devices.revoke(bob.deviceId)
        alice.sessions.forget(bob.deviceId)
        assertTrue(alice.rotation.rotate()!!.succeeded)
        alice.items.load()

        assertTrue(runExchange(alice, laptop, this))
        assertEquals(1, laptop.vault.keyGeneration())
        assertEquals("Shared", laptop.items.items.value.single().title)

        assertFalse(runExchange(alice, bob, this))
        assertEquals(0, bob.vault.keyGeneration())
    }

    @Test
    fun `the ratchet session survives rotation on both sides`() = runTest {
        val (alice, bob) = setupPair(this)

        alice.items.create(ItemContent.Note(title = "One", body = "1"))
        assertTrue(runExchange(alice, bob, this))

        assertTrue(alice.rotation.rotate()!!.succeeded)
        alice.items.load()
        assertTrue(runExchange(alice, bob, this))

        alice.items.create(ItemContent.Note(title = "Two", body = "2"))
        assertTrue(runExchange(alice, bob, this))

        assertEquals(2, bob.items.items.value.size)
        assertNotNull(bob.db.waultDbQueries.selectRatchetSession(alice.deviceId).executeAsOneOrNull())
    }
}
