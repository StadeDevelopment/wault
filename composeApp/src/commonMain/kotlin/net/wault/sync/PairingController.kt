package net.wault.sync

import net.wault.device.DeviceManager
import net.wault.security.Vault
import net.wault.transport.ConnectionRegistry
import net.wault.transport.TransportType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val ADDRESS_ATTEMPTS = 25
private const val ADDRESS_POLL_MS = 200L

sealed interface PairingState {
    data object Idle : PairingState
    data class Showing(val payload: String) : PairingState
    data object Connecting : PairingState
    data class Confirming(val shortAuthString: String) : PairingState
    data object Working : PairingState
    data class Done(val label: String) : PairingState
    data class Error(val reason: String) : PairingState
}

class PairingController(
    private val protocol: PairingProtocol,
    private val pairing: PairingService,
    private val devices: DeviceManager,
    private val vault: Vault,
    private val transports: ConnectionRegistry,
    private val sync: SyncService
) {
    private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
    val state: StateFlow<PairingState> = _state.asStateFlow()

    private var confirmation: CompletableDeferred<Boolean>? = null
    private var hostOffer: PairingOffer? = null

    fun currentPayload(): String? = (_state.value as? PairingState.Showing)?.payload

    suspend fun startHosting() {
        val vaultId = vault.vaultId()
        if (vaultId == null) {
            _state.value = PairingState.Error("this device has no vault yet")
            return
        }

        _state.value = PairingState.Connecting
        awaitReachableAddress()

        val onion = transports.get(TransportType.TOR)?.selfAddress()
        val lan = transports.get(TransportType.LAN)?.selfAddress()
        if (onion == null && lan == null) {
            _state.value = PairingState.Error("this device is not reachable yet, check Sync settings")
            return
        }

        val offer = pairing.createOffer(
            vaultId = vaultId,
            device = devices.localDevice(),
            onionAddress = onion,
            lanHint = lan
        )
        hostOffer = offer
        _state.value = PairingState.Showing(pairing.encodeOffer(offer))

        sync.pairingAcceptor = { connection ->
            _state.value = PairingState.Connecting
            val outcome = protocol.host(connection, offer) { sas ->
                awaitConfirmation(sas)
            }
            applyOutcome(outcome)
        }
    }

    suspend fun join(scanned: String, masterPassword: String) {
        val peerOffer = pairing.decodeOffer(scanned)
        if (peerOffer == null) {
            _state.value = PairingState.Error("that is not a Wault pairing code")
            return
        }

        _state.value = PairingState.Connecting
        awaitReachableAddress()

        val myOffer = pairing.createOffer(
            vaultId = peerOffer.vaultId,
            device = devices.localDevice(),
            onionAddress = transports.get(TransportType.TOR)?.selfAddress(),
            lanHint = transports.get(TransportType.LAN)?.selfAddress()
        )

        val addresses = listOfNotNull(peerOffer.lanHint, peerOffer.onionAddress)
        if (addresses.isEmpty()) {
            _state.value = PairingState.Error("that device did not advertise a reachable address")
            return
        }

        for (address in addresses) {
            val connection = sync.dialForPairing(address) ?: continue
            val outcome = runCatching {
                protocol.join(connection, peerOffer, myOffer, masterPassword) { sas ->
                    awaitConfirmation(sas)
                }
            }.getOrElse { PairingOutcome.Failed(it.message ?: "pairing failed") }
            runCatching { connection.close() }
            applyOutcome(outcome)
            if (outcome is PairingOutcome.Paired) return
            if (outcome is PairingOutcome.Declined || outcome is PairingOutcome.PeerDeclined) return
        }

        if (_state.value !is PairingState.Done) {
            _state.value = PairingState.Error("could not reach that device")
        }
    }

    fun confirm(accepted: Boolean) {
        confirmation?.complete(accepted)
    }

    fun cancel() {
        confirmation?.complete(false)
        confirmation = null
        hostOffer = null
        sync.pairingAcceptor = null
        _state.value = PairingState.Idle
    }

    private suspend fun awaitReachableAddress() {
        runCatching { sync.start() }
        repeat(ADDRESS_ATTEMPTS) {
            val ready = transports.get(TransportType.LAN)?.selfAddress() != null ||
                transports.get(TransportType.TOR)?.selfAddress() != null
            if (ready) return
            delay(ADDRESS_POLL_MS)
        }
    }

    private suspend fun awaitConfirmation(sas: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        confirmation = deferred
        _state.value = PairingState.Confirming(sas)
        val accepted = deferred.await()
        confirmation = null
        if (accepted) _state.value = PairingState.Working
        return accepted
    }

    private suspend fun applyOutcome(outcome: PairingOutcome) {
        _state.value = when (outcome) {
            is PairingOutcome.Paired -> {
                devices.load()
                sync.pairingAcceptor = null
                runCatching { sync.syncNow() }
                PairingState.Done(outcome.label)
            }

            PairingOutcome.Declined -> PairingState.Error("you declined the pairing")
            PairingOutcome.PeerDeclined -> PairingState.Error("the other device declined")
            PairingOutcome.TimedOut -> PairingState.Error("the other device stopped responding")
            is PairingOutcome.Failed -> PairingState.Error(outcome.reason)
        }
    }
}
