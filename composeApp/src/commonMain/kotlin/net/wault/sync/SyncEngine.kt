package net.wault.sync

import net.wault.crypto.CryptoApi
import net.wault.crypto.toHex
import net.wault.db.WaultDb
import net.wault.item.ItemManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

data class SyncStatus(
    val syncing: Boolean = false,
    val lastSyncedAt: Long = 0L,
    val lastError: String? = null,
    val pendingDeltas: Int = 0
)

class SyncEngine(
    private val db: WaultDb,
    private val crypto: CryptoApi,
    private val items: ItemManager,
    private val clock: () -> Long
) {
    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "kind" }

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    fun localDeltasSince(sinceUpdatedAt: Long): List<RecordDelta> =
        db.waultDbQueries.selectItemsChangedSince(sinceUpdatedAt).executeAsList().map { row ->
            RecordDelta(
                kind = RecordKind.Item,
                id = row.id,
                payload = row.payload,
                revision = row.revision,
                createdAt = row.createdAt,
                updatedAt = row.updatedAt,
                updatedBy = row.updatedBy,
                deleted = row.deleted != 0L
            )
        }

    fun localSnapshot(): Map<String, RecordDelta> =
        db.waultDbQueries.selectAllItemsIncludingDeleted().executeAsList().associate { row ->
            val delta = RecordDelta(
                kind = RecordKind.Item,
                id = row.id,
                payload = row.payload,
                revision = row.revision,
                createdAt = row.createdAt,
                updatedAt = row.updatedAt,
                updatedBy = row.updatedBy,
                deleted = row.deleted != 0L
            )
            MergeEngine.key(delta) to delta
        }

    fun applyIncoming(deltas: List<RecordDelta>): MergeOutcome {
        val outcome = MergeEngine.merge(localSnapshot(), deltas)
        if (outcome.applied.isEmpty()) return outcome

        db.waultDbQueries.transaction {
            for (delta in outcome.applied) {
                when (delta.kind) {
                    RecordKind.Item -> db.waultDbQueries.upsertItem(
                        delta.id,
                        delta.payload,
                        delta.revision,
                        delta.createdAt,
                        delta.updatedAt,
                        delta.updatedBy,
                        if (delta.deleted) 1L else 0L
                    )
                    RecordKind.Folder -> db.waultDbQueries.upsertFolder(
                        delta.id,
                        delta.payload,
                        delta.revision,
                        delta.createdAt,
                        delta.updatedAt,
                        delta.updatedBy,
                        if (delta.deleted) 1L else 0L
                    )
                }
            }
        }
        items.load()
        return outcome
    }

    fun buildPush(peerDeviceId: String, generation: Int): SyncMessage.Push {
        val cursor = db.waultDbQueries.selectSyncCursor(peerDeviceId).executeAsOneOrNull()
        val since = cursor?.lastPushedRevision ?: 0L
        val deltas = localDeltasSince(since)
        val highWater = deltas.maxOfOrNull { it.updatedAt } ?: since
        return SyncMessage.Push(
            envelopeId = newEnvelopeId(),
            deltas = deltas,
            highWaterMark = highWater,
            sentAt = clock(),
            generation = generation
        )
    }

    fun localHighWaterMark(): Long =
        db.waultDbQueries.selectAllItemsIncludingDeleted().executeAsList().maxOfOrNull { it.updatedAt } ?: 0L

    fun recordAck(peerDeviceId: String, acknowledgedUpTo: Long) {
        val cursor = db.waultDbQueries.selectSyncCursor(peerDeviceId).executeAsOneOrNull()
        db.waultDbQueries.upsertSyncCursor(
            peerDeviceId,
            cursor?.lastPulledRevision ?: 0L,
            acknowledgedUpTo
        )
        db.waultDbQueries.markDeviceSynced(clock(), peerDeviceId)
        _status.value = _status.value.copy(lastSyncedAt = clock(), lastError = null)
    }

    fun recordPulled(peerDeviceId: String, pulledUpTo: Long) {
        val cursor = db.waultDbQueries.selectSyncCursor(peerDeviceId).executeAsOneOrNull()
        db.waultDbQueries.upsertSyncCursor(
            peerDeviceId,
            pulledUpTo,
            cursor?.lastPushedRevision ?: 0L
        )
    }

    fun isDuplicate(envelopeId: String): Boolean =
        db.waultDbQueries.isEnvelopeProcessed(envelopeId).executeAsOne() > 0L

    fun markProcessed(envelopeId: String, peerDeviceId: String) {
        db.waultDbQueries.markEnvelopeProcessed(envelopeId, peerDeviceId, clock())
    }

    fun encode(message: SyncMessage): ByteArray =
        json.encodeToString(SyncMessage.serializer(), message).encodeToByteArray()

    fun decode(frame: ByteArray): SyncMessage =
        json.decodeFromString(SyncMessage.serializer(), frame.decodeToString())

    fun reportError(message: String) {
        _status.value = _status.value.copy(syncing = false, lastError = message)
    }

    fun newEnvelopeId(): String = crypto.randomBytes(16).toHex()
}
