package net.wault.sync

enum class MergeDecision { TakeIncoming, KeepLocal, Identical }

data class MergeOutcome(
    val applied: List<RecordDelta>,
    val rejected: List<RecordDelta>,
    val conflictsResolved: Int
)

object MergeEngine {

    fun decide(local: RecordDelta?, incoming: RecordDelta): MergeDecision {
        if (local == null) return MergeDecision.TakeIncoming
        if (local == incoming) return MergeDecision.Identical

        if (incoming.revision != local.revision) {
            return if (incoming.revision > local.revision) MergeDecision.TakeIncoming else MergeDecision.KeepLocal
        }
        if (incoming.updatedAt != local.updatedAt) {
            return if (incoming.updatedAt > local.updatedAt) MergeDecision.TakeIncoming else MergeDecision.KeepLocal
        }
        if (incoming.deleted != local.deleted) {
            return if (incoming.deleted) MergeDecision.TakeIncoming else MergeDecision.KeepLocal
        }
        val comparison = incoming.updatedBy.compareTo(local.updatedBy)
        return when {
            comparison > 0 -> MergeDecision.TakeIncoming
            comparison < 0 -> MergeDecision.KeepLocal
            else -> MergeDecision.Identical
        }
    }

    fun merge(localById: Map<String, RecordDelta>, incoming: List<RecordDelta>): MergeOutcome {
        val applied = ArrayList<RecordDelta>()
        val rejected = ArrayList<RecordDelta>()
        var conflicts = 0

        for (delta in incoming) {
            val local = localById[key(delta)]
            if (local != null) conflicts++
            when (decide(local, delta)) {
                MergeDecision.TakeIncoming -> applied.add(delta)
                MergeDecision.KeepLocal -> rejected.add(delta)
                MergeDecision.Identical -> Unit
            }
        }

        return MergeOutcome(applied, rejected, conflicts)
    }

    fun key(delta: RecordDelta): String = "${delta.kind.name}:${delta.id}"
}
