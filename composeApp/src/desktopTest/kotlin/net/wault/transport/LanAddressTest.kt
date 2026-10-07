package net.wault.transport

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LanAddressTest {

    @Test
    fun `a transport that was never started advertises no address`() {
        val transport = LanTransport("node-a", tcpPort = 39901, discoveryPort = 39902)
        assertNull(
            transport.selfAddress(),
            "advertising an address while nothing is listening is what makes pairing fail with 'could not reach that device'"
        )
        assertTrue(transport.selfAddresses().isEmpty())
    }

    @Test
    fun `a running transport advertises an address that points at its real port`(): Unit = runBlocking {
        val transport = LanTransport("node-b", tcpPort = 39911, discoveryPort = 39912)
        try {
            transport.start { }

            val address = assertNotNull(transport.selfAddress(), "a listening transport must be reachable")
            assertTrue(address.startsWith("lan://"), "got $address")

            val port = address.substringAfterLast(':').toIntOrNull()
            assertNotNull(port)
            assertTrue(port in 39911..39920, "the advertised port must be the one actually bound, got $port")
        } finally {
            runCatching { transport.stop() }
        }
    }

    @Test
    fun `the address a device advertises can actually be dialled`(): Unit = runBlocking {
        val host = LanTransport("host", tcpPort = 39941, discoveryPort = 39942)
        val guest = LanTransport("guest", tcpPort = 39951, discoveryPort = 39952)
        try {
            host.start { }
            guest.start { }

            val advertised = assertNotNull(
                host.selfAddress(),
                "a hosting device must advertise where it can be reached"
            )

            val connection = guest.connect(advertised)
            assertNotNull(
                connection,
                "pairing fails with 'could not reach that device' when the advertised address refuses connections"
            )
            runCatching { connection.close() }
        } finally {
            runCatching { host.stop() }
            runCatching { guest.stop() }
        }
    }

    @Test
    fun `a stopped transport stops advertising`(): Unit = runBlocking {
        val transport = LanTransport("node-c", tcpPort = 39931, discoveryPort = 39932)
        transport.start { }
        assertNotNull(transport.selfAddress())

        transport.stop()

        assertNull(
            transport.selfAddress(),
            "after shutdown the device must not keep handing out an address nobody answers on"
        )
        assertTrue(transport.selfAddresses().isEmpty())
    }
}
