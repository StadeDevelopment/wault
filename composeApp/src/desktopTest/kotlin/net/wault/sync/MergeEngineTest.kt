package net.wault.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MergeEngineTest {

    private fun delta(
        id: String = "item-1",
        revision: Long = 1,
        updatedAt: Long = 1000,
        updatedBy: String = "device-a",
        deleted: Boolean = false,
        payload: ByteArray = byteArrayOf(1, 2, 3)
    ) = RecordDelta(
        kind = RecordKind.Item,
        id = id,
        payload = payload,
        revision = revision,
        createdAt = 0,
        updatedAt = updatedAt,
        updatedBy = updatedBy,
        deleted = deleted
    )

    @Test
    fun `an unseen record is taken`() {
        assertEquals(MergeDecision.TakeIncoming, MergeEngine.decide(null, delta()))
    }

    @Test
    fun `an identical record is a no-op`() {
        val local = delta()
        assertEquals(MergeDecision.Identical, MergeEngine.decide(local, local.copy()))
    }

    @Test
    fun `a higher revision wins`() {
        val local = delta(revision = 2)
        val incoming = delta(revision = 3, payload = byteArrayOf(9))
        assertEquals(MergeDecision.TakeIncoming, MergeEngine.decide(local, incoming))
    }

    @Test
    fun `a lower revision loses even with a newer timestamp`() {
        val local = delta(revision = 5, updatedAt = 1000)
        val incoming = delta(revision = 4, updatedAt = 9999, payload = byteArrayOf(9))
        assertEquals(MergeDecision.KeepLocal, MergeEngine.decide(local, incoming))
    }

    @Test
    fun `at equal revision the newer timestamp wins`() {
        val local = delta(revision = 3, updatedAt = 1000)
        val incoming = delta(revision = 3, updatedAt = 2000, payload = byteArrayOf(9))
        assertEquals(MergeDecision.TakeIncoming, MergeEngine.decide(local, incoming))
    }

    @Test
    fun `a concurrent delete beats a concurrent edit`() {
        val local = delta(revision = 3, updatedAt = 1000, deleted = false)
        val incoming = delta(revision = 3, updatedAt = 1000, deleted = true, updatedBy = "device-a")
        assertEquals(MergeDecision.TakeIncoming, MergeEngine.decide(local, incoming))
    }

    @Test
    fun `a full tie is broken deterministically by device id`() {
        val local = delta(updatedBy = "device-a")
        val incoming = delta(updatedBy = "device-b", payload = byteArrayOf(9))
        assertEquals(MergeDecision.TakeIncoming, MergeEngine.decide(local, incoming))

        val reversed = MergeEngine.decide(delta(updatedBy = "device-b"), delta(updatedBy = "device-a"))
        assertEquals(MergeDecision.KeepLocal, reversed)
    }

    @Test
    fun `merging is symmetric so both devices converge`() {
        val a = delta(id = "x", revision = 3, updatedAt = 500, updatedBy = "device-a")
        val b = delta(id = "x", revision = 3, updatedAt = 500, updatedBy = "device-b", payload = byteArrayOf(9))

        val onA = MergeEngine.decide(a, b)
        val onB = MergeEngine.decide(b, a)

        val winnerOnA = if (onA == MergeDecision.TakeIncoming) b else a
        val winnerOnB = if (onB == MergeDecision.TakeIncoming) a else b
        assertEquals(winnerOnA, winnerOnB)
    }

    @Test
    fun `merge partitions incoming deltas`() {
        val local = mapOf(
            "Item:a" to delta(id = "a", revision = 5),
            "Item:b" to delta(id = "b", revision = 1)
        )
        val incoming = listOf(
            delta(id = "a", revision = 2, payload = byteArrayOf(9)),
            delta(id = "b", revision = 7, payload = byteArrayOf(9)),
            delta(id = "c", revision = 1, payload = byteArrayOf(9))
        )

        val outcome = MergeEngine.merge(local, incoming)
        assertEquals(setOf("b", "c"), outcome.applied.map { it.id }.toSet())
        assertEquals(listOf("a"), outcome.rejected.map { it.id })
        assertTrue(outcome.conflictsResolved == 2)
    }
}
