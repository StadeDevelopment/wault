package net.wault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.wault.AppContainer
import net.wault.sync.PairingState
import net.wault.ui.components.QrCode
import net.wault.ui.components.QrScanner
import net.wault.ui.components.isCameraScanSupported
import net.wault.ui.i18n.LocalStrings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairDeviceScreen(
    container: AppContainer,
    masterPassword: String?,
    onBack: () -> Unit,
    onPaired: () -> Unit
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val controller = container.pairingController
    val state by controller.state.collectAsState()

    var scanning by remember { mutableStateOf(masterPassword != null && isCameraScanSupported) }
    var pasted by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (masterPassword == null) controller.startHosting()
    }

    DisposableEffect(Unit) {
        onDispose { controller.cancel() }
    }

    LaunchedEffect(state) {
        if (state is PairingState.Done) onPaired()
    }

    fun submit(code: String) {
        val password = masterPassword ?: return
        scope.launch { controller.join(code.trim(), password) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(strings.pairTitle) },
                navigationIcon = {
                    IconButton(onClick = { controller.cancel(); onBack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = strings.back)
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
        val qrSide = minOf(maxWidth * 0.85f, maxHeight * 0.6f)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val current = state) {
                is PairingState.Confirming -> {
                    Text(strings.pairVerifyTitle, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = current.shortAuthString.chunked(3).joinToString(" "),
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        strings.pairVerifyBody(current.shortAuthString),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { controller.confirm(false) },
                            modifier = Modifier.weight(1f)
                        ) { Text(strings.pairMismatch) }
                        Button(
                            onClick = { controller.confirm(true) },
                            modifier = Modifier.weight(1f)
                        ) { Text(strings.pairConfirmMatch) }
                    }
                }

                PairingState.Connecting, PairingState.Working -> {
                    Spacer(Modifier.height(48.dp))
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (current == PairingState.Working) strings.pairWorking else strings.pairConnecting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is PairingState.Done -> {
                    Spacer(Modifier.height(48.dp))
                    Text(strings.pairDone(current.label), style = MaterialTheme.typography.titleMedium)
                }

                is PairingState.Error -> {
                    Spacer(Modifier.height(32.dp))
                    Text(
                        current.reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {
                        controller.cancel()
                        scope.launch { if (masterPassword == null) controller.startHosting() }
                    }) {
                        Text(strings.pairRetry)
                    }
                }

                is PairingState.Showing -> {
                    Text(
                        strings.pairShowThisCode,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    QrCode(payload = current.payload, modifier = Modifier.size(qrSide))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        strings.pairWaiting,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                PairingState.Idle -> {
                    if (masterPassword == null) {
                        Spacer(Modifier.height(48.dp))
                        CircularProgressIndicator()
                        return@Column
                    }

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = scanning,
                            onClick = { scanning = true },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            enabled = isCameraScanSupported
                        ) { Text(strings.pairScanTab) }
                        SegmentedButton(
                            selected = !scanning,
                            onClick = { scanning = false },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text(strings.pairPasteInstead) }
                    }

                    Spacer(Modifier.height(16.dp))

                    if (scanning && isCameraScanSupported) {
                        Text(
                            strings.pairScanInstruction,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                        ) {
                            QrScanner(
                                onScanned = { submit(it) },
                                onPermissionFlow = { active ->
                                    if (active) container.beginExternalFlow() else container.endExternalFlow()
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = pasted,
                            onValueChange = { pasted = it },
                            label = { Text(strings.pairPastePlaceholder) },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { submit(pasted) },
                            enabled = pasted.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(strings.pairJoinAction) }
                    }

                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { controller.cancel(); onBack() }) { Text(strings.cancel) }
                }
            }
        }
        }
    }
}
