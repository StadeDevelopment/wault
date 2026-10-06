package net.wault.sync

enum class SyncOutcome {
    Never,
    Syncing,
    Succeeded,
    NoKnownAddress,
    Unreachable,
    Rejected,
    Failed
}

data class PeerSyncStatus(
    val outcome: SyncOutcome = SyncOutcome.Never,
    val lastAttemptMillis: Long = 0L,
    val lastSuccessMillis: Long = 0L,
    val detail: String = ""
) {
    val isHealthy: Boolean get() = outcome == SyncOutcome.Succeeded
    val hasSynced: Boolean get() = lastSuccessMillis > 0L
}
