package net.wault.sync

import net.wault.crypto.Base64Bytes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RecordKind {
    @SerialName("item") Item,
    @SerialName("folder") Folder
}

@Serializable
data class RecordDelta(
    val kind: RecordKind,
    val id: String,
    val payload: ByteArray,
    val revision: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val updatedBy: String,
    val deleted: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RecordDelta) return false
        return kind == other.kind && id == other.id && revision == other.revision &&
            updatedAt == other.updatedAt && updatedBy == other.updatedBy &&
            deleted == other.deleted && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = kind.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + revision.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + updatedBy.hashCode()
        result = 31 * result + deleted.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

@Serializable
sealed interface SyncMessage {

    @Serializable
    @SerialName("offer")
    data class Offer(
        val envelopeId: String,
        val sinceUpdatedAt: Long,
        val sentAt: Long
    ) : SyncMessage

    @Serializable
    @SerialName("push")
    data class Push(
        val envelopeId: String,
        val deltas: List<RecordDelta>,
        val highWaterMark: Long,
        val sentAt: Long,
        val generation: Int = 0
    ) : SyncMessage

    @Serializable
    @SerialName("rekey")
    data class Rekey(
        val envelopeId: String,
        val generation: Int,
        val dataKey: ByteArray
    ) : SyncMessage {
        override fun equals(other: Any?): Boolean =
            other is Rekey && envelopeId == other.envelopeId &&
                generation == other.generation && dataKey.contentEquals(other.dataKey)

        override fun hashCode(): Int =
            (envelopeId.hashCode() * 31 + generation) * 31 + dataKey.contentHashCode()
    }

    @Serializable
    @SerialName("rekeyAck")
    data class RekeyAck(val generation: Int) : SyncMessage

    @Serializable
    @SerialName("rekeyRequest")
    data class RekeyRequest(val currentGeneration: Int) : SyncMessage

    @Serializable
    @SerialName("ack")
    data class Ack(
        val envelopeId: String,
        val acknowledgedUpTo: Long
    ) : SyncMessage

    @Serializable
    @SerialName("revoke")
    data class Revoke(
        val envelopeId: String,
        val revokedDeviceId: String,
        val rotatedAt: Long
    ) : SyncMessage
}

@Serializable
data class PairingOffer(
    @SerialName("v") val vaultId: String,
    @SerialName("d") val deviceId: String,
    @SerialName("l") val label: String,
    @SerialName("p") val platform: String,
    @SerialName("s") @Serializable(with = Base64Bytes::class) val signingKey: ByteArray,
    @SerialName("a") @Serializable(with = Base64Bytes::class) val agreementKey: ByteArray,
    @SerialName("o") val onionAddress: String?,
    @SerialName("h") val lanHint: String?,
    @SerialName("n") @Serializable(with = Base64Bytes::class) val pairingNonce: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PairingOffer) return false
        return vaultId == other.vaultId && deviceId == other.deviceId &&
            signingKey.contentEquals(other.signingKey) &&
            agreementKey.contentEquals(other.agreementKey) &&
            pairingNonce.contentEquals(other.pairingNonce)
    }

    override fun hashCode(): Int {
        var result = vaultId.hashCode()
        result = 31 * result + deviceId.hashCode()
        result = 31 * result + signingKey.contentHashCode()
        result = 31 * result + agreementKey.contentHashCode()
        result = 31 * result + pairingNonce.contentHashCode()
        return result
    }
}

@Serializable
data class PairedDevice(
    val id: String,
    val label: String,
    val platform: String,
    val onionAddress: String?,
    val pairedAt: Long,
    val lastSyncedAt: Long,
    val revoked: Boolean,
    val keyGeneration: Int = 0
)
