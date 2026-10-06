package net.wault.sync

import net.wault.crypto.zero
import net.wault.device.DeviceManager
import net.wault.security.Vault
import net.wault.transport.Connection
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val PAIRING_HELLO = "WAULT-PAIR/1"
private const val STEP_TIMEOUT_MS = 30_000L
private const val CONFIRM_TIMEOUT_MS = 180_000L

@Serializable
sealed interface PairingMessage {

    @Serializable
    @SerialName("offer")
    data class Offer(val offer: PairingOffer) : PairingMessage

    @Serializable
    @SerialName("confirm")
    data class Confirm(val accepted: Boolean) : PairingMessage

    @Serializable
    @SerialName("key")
    data class KeyTransfer(
        val sealedDataKey: ByteArray,
        val generation: Int
    ) : PairingMessage {
        override fun equals(other: Any?): Boolean =
            other is KeyTransfer && generation == other.generation &&
                sealedDataKey.contentEquals(other.sealedDataKey)

        override fun hashCode(): Int = generation * 31 + sealedDataKey.contentHashCode()
    }

    @Serializable
    @SerialName("complete")
    data class Complete(val accepted: Boolean) : PairingMessage
}

sealed interface PairingOutcome {
    data class Paired(val peerDeviceId: String, val label: String) : PairingOutcome
    data object Declined : PairingOutcome
    data object PeerDeclined : PairingOutcome
    data object TimedOut : PairingOutcome
    data class Failed(val reason: String) : PairingOutcome
}

class PairingProtocol(
    private val pairing: PairingService,
    private val devices: DeviceManager,
    private val vault: Vault,
    private val localItemCount: () -> Int = { 0 }
) {
    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "kind" }

    suspend fun host(
        connection: Connection,
        myOffer: PairingOffer,
        confirm: suspend (String) -> Boolean
    ): PairingOutcome {
        val peerOffer = (receive(connection, STEP_TIMEOUT_MS) as? PairingMessage.Offer)?.offer
            ?: return PairingOutcome.Failed("no pairing offer received")

        if (peerOffer.deviceId == myOffer.deviceId) {
            return PairingOutcome.Failed("that device is already this device")
        }

        val sessionKey = pairing.sessionKey(devices.localDevice(), peerOffer, myOffer.pairingNonce)
        try {
            val (initiator, responder) = ordered(myOffer, peerOffer)
            val sas = pairing.shortAuthString(sessionKey, initiator, responder)

            if (!confirm(sas)) {
                send(connection, PairingMessage.Confirm(false))
                return PairingOutcome.Declined
            }
            send(connection, PairingMessage.Confirm(true))

            val peerConfirm = receive(connection, CONFIRM_TIMEOUT_MS) as? PairingMessage.Confirm
                ?: return PairingOutcome.TimedOut
            if (!peerConfirm.accepted) return PairingOutcome.PeerDeclined

            val transcript = pairing.transcript(initiator, responder)
            val sealedDataKey = vault.withDataKey { dataKey ->
                pairing.sealDataKey(sessionKey, dataKey, transcript)
            }
            send(
                connection,
                PairingMessage.KeyTransfer(sealedDataKey, vault.keyGeneration())
            )

            val complete = receive(connection, STEP_TIMEOUT_MS) as? PairingMessage.Complete
                ?: return PairingOutcome.TimedOut
            if (!complete.accepted) return PairingOutcome.PeerDeclined

            remember(peerOffer, myOffer, sessionKey, transcript)
            return PairingOutcome.Paired(peerOffer.deviceId, peerOffer.label)
        } finally {
            sessionKey.zero()
        }
    }

    suspend fun join(
        connection: Connection,
        peerOffer: PairingOffer,
        myOffer: PairingOffer,
        masterPassword: String,
        confirm: suspend (String) -> Boolean
    ): PairingOutcome {
        if (peerOffer.deviceId == myOffer.deviceId) {
            return PairingOutcome.Failed("that code came from this device")
        }
        if (localItemCount() > 0) {
            return PairingOutcome.Failed("this device already holds items; joining would discard them")
        }

        send(connection, PairingMessage.Offer(myOffer))

        val sessionKey = pairing.sessionKey(devices.localDevice(), peerOffer, myOffer.pairingNonce)
        try {
            val (initiator, responder) = ordered(myOffer, peerOffer)
            val sas = pairing.shortAuthString(sessionKey, initiator, responder)

            val peerConfirm = receive(connection, CONFIRM_TIMEOUT_MS) as? PairingMessage.Confirm
                ?: return PairingOutcome.TimedOut
            if (!peerConfirm.accepted) return PairingOutcome.PeerDeclined

            if (!confirm(sas)) {
                send(connection, PairingMessage.Confirm(false))
                return PairingOutcome.Declined
            }
            send(connection, PairingMessage.Confirm(true))

            val transfer = receive(connection, STEP_TIMEOUT_MS) as? PairingMessage.KeyTransfer
                ?: return PairingOutcome.TimedOut

            val transcript = pairing.transcript(initiator, responder)
            val dataKey = pairing.openDataKey(sessionKey, transfer.sealedDataKey, transcript)
            if (dataKey == null) {
                send(connection, PairingMessage.Complete(false))
                return PairingOutcome.Failed("the vault key could not be opened")
            }

            val identity = devices.exportLocalIdentity()
            try {
                vault.adoptDataKey(dataKey, masterPassword, transfer.generation)
            } finally {
                dataKey.zero()
            }
            devices.restoreLocalIdentity(identity)

            remember(peerOffer, myOffer, sessionKey, transcript, transfer.generation)
            send(connection, PairingMessage.Complete(true))
            return PairingOutcome.Paired(peerOffer.deviceId, peerOffer.label)
        } finally {
            sessionKey.zero()
        }
    }

    private fun remember(
        peerOffer: PairingOffer,
        myOffer: PairingOffer,
        sessionKey: ByteArray,
        transcript: ByteArray,
        generation: Int = vault.keyGeneration()
    ) {
        val (initiator, _) = ordered(myOffer, peerOffer)
        val rootKey = pairing.rootKeyFor(sessionKey, transcript)
        try {
            devices.remember(
                id = peerOffer.deviceId,
                label = peerOffer.label,
                platform = peerOffer.platform,
                signingKey = peerOffer.signingKey,
                agreementKey = peerOffer.agreementKey,
                rootKey = rootKey,
                isInitiator = initiator.deviceId == myOffer.deviceId,
                onionAddress = peerOffer.onionAddress,
                lanAddress = peerOffer.lanHint,
                keyGeneration = generation
            )
        } finally {
            rootKey.zero()
        }
    }

    private fun ordered(a: PairingOffer, b: PairingOffer): Pair<PairingOffer, PairingOffer> =
        if (a.deviceId <= b.deviceId) a to b else b to a

    private suspend fun send(connection: Connection, message: PairingMessage) {
        connection.send(json.encodeToString(PairingMessage.serializer(), message).encodeToByteArray())
    }

    private suspend fun receive(connection: Connection, timeoutMs: Long): PairingMessage? {
        val frame = withTimeoutOrNull(timeoutMs) { connection.receive() } ?: return null
        return runCatching {
            json.decodeFromString(PairingMessage.serializer(), frame.decodeToString())
        }.getOrNull()
    }
}
