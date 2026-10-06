package net.wault.sync

import net.wault.item.ItemContent
import net.wault.item.MatchableUri
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SyncServiceTest {

    private val time = AtomicLong(1_000L)
    private val opened = mutableListOf<SyncNode>()

    @AfterTest
    fun tearDown() {
        opened.forEach { it.close() }
        opened.clear()
    }

    private fun newNode(label: String, scope: kotlinx.coroutines.CoroutineScope): SyncNode {
        val node = SyncNode(label, scope, time)
        node.vault.setup("master password for $label")
        opened.add(node)
        return node
    }

    private fun linkVaults(source: SyncNode, target: SyncNode) {
        val dataKey = source.vault.withDataKey { it.copyOf() }
        target.vault.adoptDataKey(dataKey, "master password for ${target.label}")
    }

    private suspend fun runExchange(initiator: SyncNode, responder: SyncNode, scope: kotlinx.coroutines.CoroutineScope): Boolean {
        val (initiatorPipe, responderPipe) = connectedPipes()
        val results = listOf(
            scope.async { initiator.service.exchange(initiatorPipe, responder.deviceId, initiator = true) },
            scope.async { responder.service.exchange(responderPipe, initiator.deviceId, initiator = false) }
        ).awaitAll()
        return results.all { it }
    }

    @Test
    fun `an item created on one device arrives on the other`() = runTest {
        val alice = newNode("alice", this)
        val bob = newNode("bob", this)
        linkVaults(alice, bob)
        pair(alice, bob)

        alice.items.load()
        bob.items.load()

        alice.items.create(
            ItemContent.Login(
                title = "GitHub",
                username = "eren",
                password = "correct-horse-battery",
                uris = listOf(MatchableUri("https://github.com"))
            )
        )

        assertTrue(runExchange(alice, bob, this))

        val received = bob.items.items.value
        assertEquals(1, received.size)
        val login = assertIs<ItemContent.Login>(received.single().content)
        assertEquals("GitHub", login.title)
        assertEquals("eren", login.username)
        assertEquals("correct-horse-battery", login.password)
    }

    @Test
    fun `edits flow in both directions in one exchange`() = runTest {
        val alice = newNode("alice", this)
        val bob = newNode("bob", this)
        linkVaults(alice, bob)
        pair(alice, bob)
        alice.items.load()
        bob.items.load()

        alice.items.create(ItemContent.Note(title = "From Alice", body = "a"))
        bob.items.create(ItemContent.Note(title = "From Bob", body = "b"))

        assertTrue(runExchange(alice, bob, this))

        assertEquals(
            setOf("From Alice", "From Bob"),
            alice.items.items.value.map { it.title }.toSet()
        )
        assertEquals(
            setOf("From Alice", "From Bob"),
            bob.items.items.value.map { it.title }.toSet()
        )
    }

    @Test
    fun `a later edit wins and propagates`() = runTest {
        val alice = newNode("alice", this)
        val bob = newNode("bob", this)
        linkVaults(alice, bob)
        pair(alice, bob)
        alice.items.load()
        bob.items.load()

        val created = alice.items.create(ItemContent.Login(title = "Mail", password = "first"))
        assertTrue(runExchange(alice, bob, this))

        alice.items.changePassword(created.id, "second")
        assertTrue(runExchange(alice, bob, this))

        val onBob = bob.items.byId(created.id)
        assertNotNull(onBob)
        val login = assertIs<ItemContent.Login>(onBob.content)
        assertEquals("second", login.password)
        assertEquals("first", login.passwordHistory.single().password)
    }

    @Test
    fun `a deletion propagates as a tombstone`() = runTest {
        val alice = newNode("alice", this)
        val bob = newNode("bob", this)
        linkVaults(alice, bob)
        pair(alice, bob)
        alice.items.load()
        bob.items.load()

        val created = alice.items.create(ItemContent.Note(title = "Temporary", body = "x"))
        assertTrue(runExchange(alice, bob, this))
        assertEquals(1, bob.items.items.value.size)

        alice.items.delete(created.id)
        assertTrue(runExchange(alice, bob, this))

        assertTrue(bob.items.items.value.isEmpty())
    }

    @Test
    fun `an unpaired device is refused`() = runTest {
        val alice = newNode("alice", this)
        val stranger = newNode("stranger", this)
        linkVaults(alice, stranger)

        alice.items.load()
        stranger.items.load()
        alice.items.create(ItemContent.Note(title = "Private", body = "x"))

        val (alicePipe, strangerPipe) = connectedPipes()
        val results = listOf(
            async { alice.service.exchange(alicePipe, stranger.deviceId, initiator = true) },
            async { stranger.service.exchange(strangerPipe, alice.deviceId, initiator = false) }
        ).awaitAll()

        assertTrue(results.none { it })
        assertTrue(stranger.items.items.value.isEmpty())
    }

    @Test
    fun `a revoked device stops receiving`() = runTest {
        val alice = newNode("alice", this)
        val bob = newNode("bob", this)
        linkVaults(alice, bob)
        pair(alice, bob)
        alice.items.load()
        bob.items.load()

        alice.devices.revoke(bob.deviceId)
        alice.items.create(ItemContent.Note(title = "After revoke", body = "x"))

        val (alicePipe, bobPipe) = connectedPipes()
        val results = listOf(
            async { alice.service.exchange(alicePipe, bob.deviceId, initiator = true) },
            async { bob.service.exchange(bobPipe, alice.deviceId, initiator = false) }
        ).awaitAll()

        assertTrue(results.none { it })
        assertTrue(bob.items.items.value.isEmpty())
    }

    @Test
    fun `repeated exchanges are idempotent`() = runTest {
        val alice = newNode("alice", this)
        val bob = newNode("bob", this)
        linkVaults(alice, bob)
        pair(alice, bob)
        alice.items.load()
        bob.items.load()

        alice.items.create(ItemContent.Note(title = "Once", body = "x"))

        repeat(3) { assertTrue(runExchange(alice, bob, this)) }

        assertEquals(1, bob.items.items.value.size)
        assertEquals(1, alice.items.items.value.size)
    }

    @Test
    fun `the ratchet advances across exchanges`() = runTest {
        val alice = newNode("alice", this)
        val bob = newNode("bob", this)
        linkVaults(alice, bob)
        pair(alice, bob)
        alice.items.load()
        bob.items.load()

        alice.items.create(ItemContent.Note(title = "One", body = "1"))
        assertTrue(runExchange(alice, bob, this))

        val afterFirst = alice.db.waultDbQueries.selectRatchetSession(bob.deviceId).executeAsOneOrNull()
        assertNotNull(afterFirst)

        alice.items.create(ItemContent.Note(title = "Two", body = "2"))
        assertTrue(runExchange(alice, bob, this))

        val afterSecond = alice.db.waultDbQueries.selectRatchetSession(bob.deviceId).executeAsOneOrNull()
        assertNotNull(afterSecond)
        assertTrue(!afterFirst.state.contentEquals(afterSecond.state))
        assertEquals(2, bob.items.items.value.size)
    }
}
