package net.wault.transport

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertTrue

class LanDiscoveryTest {

    private suspend fun awaitPeer(transport: LanTransport, port: Int): String? =
        withTimeoutOrNull(20_000) {
            while (true) {
                val hit = transport.discoveredPeers().firstOrNull { it.endsWith(":$port") }
                if (hit != null) return@withTimeoutOrNull hit
                delay(250)
            }
            @Suppress("UNREACHABLE_CODE") null
        }

    @Test
    fun `two instances on one host discover each other`() = runBlocking {
        val discoveryPort = 38902
        val alice = LanTransport("alice-node", tcpPort = 38911, discoveryPort = discoveryPort)
        val bob = LanTransport("bob-node", tcpPort = 38921, discoveryPort = discoveryPort)

        try {
            alice.start { }
            bob.start { }

            val aliceSawBob = awaitPeer(alice, 38921)
            val bobSawAlice = awaitPeer(bob, 38911)

            assertTrue(
                aliceSawBob != null,
                "alice never discovered bob; two vaults on one network cannot find each other"
            )
            assertTrue(
                bobSawAlice != null,
                "bob never discovered alice; discovery must work in both directions"
            )
        } finally {
            runCatching { alice.stop() }
            runCatching { bob.stop() }
        }
    }

    @Test
    fun `a peer never reports itself as discovered`() = runBlocking {
        val solo = LanTransport("solo-node", tcpPort = 38931, discoveryPort = 38903)
        try {
            solo.start { }
            delay(3_000)
            assertTrue(
                solo.discoveredPeers().none { it.endsWith(":38931") },
                "a node must not list its own announcement as a peer"
            )
        } finally {
            runCatching { solo.stop() }
        }
    }
}
