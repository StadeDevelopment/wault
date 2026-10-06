package net.wault.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.wault.AppContainer
import net.wault.currentTimeMillis
import net.wault.item.ItemContent
import net.wault.item.TotpSecret
import net.wault.item.VaultItem
import net.wault.security.copyToClipboard
import net.wault.ui.components.CircleIconButton
import net.wault.ui.components.GroupPosition
import net.wault.ui.components.LocalGroupPosition
import net.wault.ui.components.LocalHomeBarClearance
import net.wault.ui.components.SettingsRow
import net.wault.ui.components.groupPositions
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import net.wault.totp.TotpImport
import net.wault.totp.TotpImportPreview
import net.wault.ui.components.QrScanner
import net.wault.ui.components.isCameraScanSupported
import net.wault.ui.i18n.LocalStrings
import net.wault.ui.theme.WaultColors
import kotlinx.coroutines.delay

private val RING_SIZE = 34.dp
private const val RING_STROKE = 3f

private data class CodeEntry(
    val itemId: String,
    val label: String,
    val secret: TotpSecret
)

@Composable
fun AuthenticatorScreen(
    container: AppContainer,
    onOpenItem: (String) -> Unit,
    onAdd: () -> Unit
) {
    val strings = LocalStrings.current
    val items by container.items.items.collectAsState()
    var importOpen by remember { mutableStateOf(false) }

    val entries = remember(items) { collectCodes(items) }

    var nowMillis by remember { mutableStateOf(currentTimeMillis()) }
    LaunchedEffect(entries.isEmpty()) {
        while (true) {
            nowMillis = currentTimeMillis()
            delay(1_000)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = strings.tabAuthenticator,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
            CircleIconButton(
                icon = Icons.Default.QrCodeScanner,
                contentDescription = strings.authenticatorImport,
                onClick = { importOpen = true }
            )
            CircleIconButton(
                icon = Icons.Default.Add,
                contentDescription = strings.newAuthenticator,
                onClick = onAdd
            )
        }

        if (entries.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(strings.authenticatorEmptyTitle, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    strings.authenticatorEmptyBody,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = { importOpen = true }) {
                    Text(strings.authenticatorImport)
                }
            }

            return@Column
        }

        val positions = groupPositions(entries.size)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = LocalHomeBarClearance.current + 24.dp)
        ) {
            itemsIndexed(entries, key = { _, entry -> entry.itemId }) { index, entry ->
                CompositionLocalProvider(LocalGroupPosition provides positions[index]) {
                    CodeRow(
                        container = container,
                        entry = entry,
                        nowMillis = nowMillis,
                        onOpen = { onOpenItem(entry.itemId) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }

    if (importOpen) {
        TotpImportDialog(container = container, onDismiss = { importOpen = false })
    }
}

@Composable
private fun TotpImportDialog(container: AppContainer, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    var pasted by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(isCameraScanSupported) }
    var preview by remember { mutableStateOf<TotpImportPreview?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun consider(payload: String) {
        val parsed = TotpImport.preview(payload)
        if (parsed == null) {
            message = strings.authenticatorImportUnrecognised
        } else {
            message = null
            preview = parsed
        }
    }

    val ready = preview

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.authenticatorImport) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (ready != null) {
                    Text(
                        text = strings.importPreviewBody(ready.codes.size, ready.format.displayName),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (ready.skipped > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            strings.importSkipped(ready.skipped),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = ready.codes.take(6).joinToString("\n") { it.label },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    return@Column
                }

                Text(strings.authenticatorImportBody, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))

                if (scanning) {
                    Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                        QrScanner(
                            onScanned = { consider(it) },
                            onPermissionFlow = { active ->
                                if (active) container.beginExternalFlow() else container.endExternalFlow()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { scanning = false }) {
                        Text(strings.authenticatorPaste)
                    }
                } else {
                    OutlinedTextField(
                        value = pasted,
                        onValueChange = { pasted = it; message = null },
                        label = { Text(strings.authenticatorPastePlaceholder) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isCameraScanSupported) {
                        TextButton(onClick = { scanning = true }) {
                            Text(strings.authenticatorScanQr)
                        }
                    }
                }

                message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            if (ready != null) {
                TextButton(onClick = {
                    container.importTotpCodes(ready.codes)
                    onDismiss()
                }) {
                    Text(strings.importAction)
                }
            } else {
                TextButton(
                    onClick = { consider(pasted) },
                    enabled = pasted.isNotBlank()
                ) {
                    Text(strings.confirm)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
private fun CodeRow(
    container: AppContainer,
    entry: CodeEntry,
    nowMillis: Long,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val code = remember(entry.secret, nowMillis / 1000L) {
        container.totp.generate(entry.secret, nowMillis)
    }

    val progress = code.secondsRemaining.toFloat() / entry.secret.periodSeconds.coerceAtLeast(1)
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(400),
        label = "totpRing"
    )

    val urgent = code.secondsRemaining <= 5
    val ringColor = if (urgent) WaultColors.critical else MaterialTheme.colorScheme.primary

    SettingsRow(
        title = code.code.chunked(3).joinToString(" "),
        subtitle = entry.label,
        modifier = modifier,
        onClick = onOpen,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(RING_SIZE)) {
                        drawArc(
                            color = ringColor.copy(alpha = 0.2f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = RING_STROKE * density, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = ringColor,
                            startAngle = -90f,
                            sweepAngle = 360f * animated,
                            useCenter = false,
                            style = Stroke(width = RING_STROKE * density, cap = StrokeCap.Round)
                        )
                    }
                    Text(
                        text = code.secondsRemaining.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = ringColor
                    )
                }
                IconButton(onClick = { copyToClipboard(code.code, sensitive = true) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = strings.copy)
                }
            }
        }
    )
}

private fun collectCodes(items: List<VaultItem>): List<CodeEntry> {
    val out = ArrayList<CodeEntry>()
    for (item in items) {
        when (val content = item.content) {
            is ItemContent.Authenticator -> out.add(
                CodeEntry(item.id, item.title.ifBlank { content.secret.issuer }, content.secret)
            )
            is ItemContent.Login -> content.totp?.let { secret ->
                out.add(CodeEntry(item.id, item.title.ifBlank { secret.issuer }, secret))
            }
            else -> Unit
        }
    }
    return out.sortedBy { it.label.lowercase() }
}
