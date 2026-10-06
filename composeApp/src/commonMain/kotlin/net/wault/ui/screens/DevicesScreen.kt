package net.wault.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.wault.AppContainer
import net.wault.currentTimeMillis
import net.wault.sync.PeerSyncStatus
import net.wault.sync.SyncOutcome
import net.wault.ui.RelativeTime
import net.wault.ui.i18n.LocalStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onPair: () -> Unit
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val devices by container.devices.paired.collectAsState()
    val syncStatus by container.sync.peerStatus.collectAsState()
    var revoking by remember { mutableStateOf<String?>(null) }

    var nowMillis by remember { mutableStateOf(currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = currentTimeMillis()
            delay(30_000)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(strings.devicesTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                actions = {
                    if (devices.isNotEmpty()) {
                        TextButton(onClick = {
                            scope.launch { runCatching { container.sync.syncNow() } }
                        }) {
                            Text(strings.syncRetryNow)
                        }
                    }
                    TextButton(onClick = onPair) { Text(strings.pairDevice) }
                }
            )
        }
    ) { padding ->
        if (devices.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    strings.devicesEmpty,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(devices, key = { it.id }) { device ->
                val status = syncStatus[device.id] ?: PeerSyncStatus()
                val problem = when (status.outcome) {
                    SyncOutcome.Syncing -> strings.syncStatusSyncing
                    SyncOutcome.NoKnownAddress -> strings.syncStatusNoAddress
                    SyncOutcome.Unreachable -> strings.syncStatusUnreachable
                    SyncOutcome.Rejected -> strings.syncStatusRejected
                    SyncOutcome.Failed -> strings.syncStatusFailed
                    SyncOutcome.Never, SyncOutcome.Succeeded -> null
                }

                ListItem(
                    headlineContent = { Text(device.label) },
                    supportingContent = {
                        Column {
                            Text(
                                if (device.lastSyncedAt == 0L) {
                                    strings.neverSynced
                                } else {
                                    strings.lastSynced(
                                        RelativeTime.format(strings, device.lastSyncedAt, nowMillis)
                                    )
                                }
                            )
                            problem?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (status.outcome == SyncOutcome.Syncing) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    }
                                )
                            }
                            if (status.detail.isNotBlank() && status.outcome != SyncOutcome.Succeeded) {
                                Text(
                                    text = strings.syncStatusDetail(status.detail),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    trailingContent = {
                        if (!device.revoked) {
                            TextButton(onClick = { revoking = device.id }) {
                                Text(strings.revokeDevice, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    revoking?.let { deviceId ->
        AlertDialog(
            onDismissRequest = { revoking = null },
            title = { Text(strings.revokeDevice) },
            text = { Text(strings.revokeDeviceBody) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { container.revokeDevice(deviceId) }
                    revoking = null
                }) {
                    Text(strings.confirm, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { revoking = null }) { Text(strings.cancel) }
            }
        )
    }
}
