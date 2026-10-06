package net.wault.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LockPolicyTest {

    @Test
    fun `only immediate locks the moment the app leaves the foreground`() {
        assertTrue(LockPolicy.locksOnBackground(AutoLock.IMMEDIATE))
        assertFalse(LockPolicy.locksOnBackground(AutoLock.NEVER))
        assertFalse(LockPolicy.locksOnBackground(30))
        assertFalse(LockPolicy.locksOnBackground(60 * 60))
    }

    @Test
    fun `never produces a deadline that can never pass`() {
        val deadline = LockPolicy.deadlineFrom(1_000, AutoLock.NEVER)
        assertEquals(LockPolicy.NO_DEADLINE, deadline)
        assertFalse(LockPolicy.isExpired(Long.MAX_VALUE - 1, deadline))
    }

    @Test
    fun `a timeout becomes an absolute deadline`() {
        assertEquals(1_000 + 60_000, LockPolicy.deadlineFrom(1_000, 60))
        assertEquals(1_000 + 3_600_000, LockPolicy.deadlineFrom(1_000, 60 * 60))
    }

    @Test
    fun `the deadline is reached exactly at the limit`() {
        val deadline = LockPolicy.deadlineFrom(0, 60)
        assertFalse(LockPolicy.isExpired(59_999, deadline))
        assertTrue(LockPolicy.isExpired(60_000, deadline))
        assertTrue(LockPolicy.isExpired(60_001, deadline))
    }

    @Test
    fun `an absolute deadline does not need anything to be running`() {
        val deadline = LockPolicy.deadlineFrom(1_000, 300)
        assertTrue(
            LockPolicy.isExpired(1_000 + 300_000, deadline),
            "expiry must be decidable from the clock alone, so a caller that was never " +
                "resumed still sees the session as over"
        )
    }
}
