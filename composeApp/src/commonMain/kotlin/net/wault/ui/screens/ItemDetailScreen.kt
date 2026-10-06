package net.wault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import net.wault.AppContainer
import net.wault.item.ItemContent
import net.wault.security.copyToClipboard
import net.wault.strength.StrengthMeter
import net.wault.ui.components.StrengthBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.text.style.TextOverflow
import net.wault.ui.components.CircleBackButton
import net.wault.ui.components.CircleIconButton
import net.wault.ui.theme.WaultColors
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import net.wault.ui.i18n.LocalStrings
import kotlinx.coroutines.delay

private val ACTION_HEIGHT = 52.dp
private val FIELD_RADIUS = 18.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    container: AppContainer,
    itemId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit
) {
    val strings = LocalStrings.current
    val items by container.items.items.collectAsState()
    val item = remember(items, itemId) { items.firstOrNull { it.id == itemId } }

    var revealed by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var folderMenuOpen by remember { mutableStateOf(false) }
    val folders by container.folders.folders.collectAsState()

    if (item == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircleBackButton(onClick = onBack)

            Text(
                text = item.title.ifBlank { item.id.take(8) },
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )

            CircleIconButton(
                icon = if (item.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = if (item.favorite) {
                    strings.removeFromFavorites
                } else {
                    strings.addToFavorites
                },
                tint = if (item.favorite) WaultColors.favorite else null,
                onClick = { container.items.setFavorite(item.id, !item.favorite) }
            )

            Box {
                CircleIconButton(
                    icon = Icons.Filled.Folder,
                    contentDescription = strings.folders,
                    onClick = { folderMenuOpen = true }
                )
                DropdownMenu(
                    expanded = folderMenuOpen,
                    onDismissRequest = { folderMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(strings.noFolder) },
                        trailingIcon = {
                            if (item.folderId == null) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            container.items.moveToFolder(item.id, null)
                            folderMenuOpen = false
                        }
                    )
                    folders.forEach { folder ->
                        DropdownMenuItem(
                            text = { Text(folder.name) },
                            trailingIcon = {
                                if (item.folderId == folder.id) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                container.items.moveToFolder(item.id, folder.id)
                                folderMenuOpen = false
                            }
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            when (val content = item.content) {
                is ItemContent.Login -> {
                    CopyableField(strings.fieldUsername, content.username)
                    SecretField(
                        label = strings.fieldPassword,
                        value = content.password,
                        revealed = revealed,
                        onToggleReveal = { revealed = !revealed }
                    )
                    if (content.password.isNotBlank()) {
                        val report = remember(content.password) { StrengthMeter.evaluate(content.password) }
                        Spacer(Modifier.height(8.dp))
                        StrengthBar(level = report.level, entropyBits = report.entropyBits)
                    }
                    content.totp?.let { secret ->
                        Spacer(Modifier.height(16.dp))
                        TotpField(container, secret)
                    }
                    content.uris.forEach { uri ->
                        CopyableField(strings.fieldWebsite, uri.uri)
                    }
                }

                is ItemContent.Card -> {
                    CopyableField(strings.fieldCardholder, content.cardholderName)
                    SecretField(strings.fieldCardNumber, content.number, revealed) { revealed = !revealed }
                    CopyableField(
                        strings.fieldExpiry,
                        listOfNotNull(content.expiryMonth, content.expiryYear).joinToString("/")
                    )
                    SecretField(strings.fieldSecurityCode, content.securityCode, revealed) { revealed = !revealed }
                }

                is ItemContent.Identity -> {
                    CopyableField(strings.fieldTitle, "${content.firstName} ${content.lastName}".trim())
                    CopyableField("Email", content.email)
                    CopyableField("Phone", content.phone)
                }

                is ItemContent.SshKey -> {
                    CopyableField("Fingerprint", content.fingerprint)
                    SecretField("Private key", content.privateKey, revealed) { revealed = !revealed }
                }

                is ItemContent.Authenticator -> {
                    TotpField(container, content.secret)
                    if (content.secret.issuer.isNotBlank()) {
                        CopyableField(strings.authenticatorIssuer, content.secret.issuer)
                    }
                }

                is ItemContent.Note -> Unit
            }

            if (item.content.notes.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(strings.fieldNotes, style = MaterialTheme.typography.labelMedium)
                Text(item.content.notes, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.weight(1f).height(ACTION_HEIGHT)
                ) {
                    Text(strings.delete)
                }
                Button(
                    onClick = { onEdit(item.id) },
                    modifier = Modifier.weight(1f).height(ACTION_HEIGHT)
                ) {
                    Text(strings.edit)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(strings.deleteItemTitle) },
            text = { Text(strings.deleteItemBody) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    container.items.delete(item.id)
                    onBack()
                }) {
                    Text(strings.delete, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(strings.cancel) }
            }
        )
    }
}

@Composable
private fun FieldShell(label: String, content: @Composable RowScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(FIELD_RADIUS),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

@Composable
private fun CopyableField(label: String, value: String) {
    if (value.isBlank()) return
    val strings = LocalStrings.current
    FieldShell(label) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(vertical = 10.dp)
        )
        IconButton(onClick = { copyToClipboard(value, sensitive = false) }) {
            Icon(Icons.Filled.ContentCopy, contentDescription = strings.copy)
        }
    }
}

@Composable
private fun SecretField(
    label: String,
    value: String,
    revealed: Boolean,
    onToggleReveal: () -> Unit
) {
    if (value.isBlank()) return
    val strings = LocalStrings.current
    FieldShell(label) {
        Text(
            text = if (revealed) value else "•".repeat(value.length.coerceAtMost(24)),
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.weight(1f).padding(vertical = 10.dp)
        )
        IconButton(onClick = onToggleReveal) {
            Icon(
                imageVector = if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = if (revealed) strings.hidePassword else strings.revealPassword
            )
        }
        IconButton(onClick = { copyToClipboard(value, sensitive = true) }) {
            Icon(Icons.Filled.ContentCopy, contentDescription = strings.copy)
        }
    }
}

@Composable
private fun TotpField(container: AppContainer, secret: net.wault.item.TotpSecret) {
    val strings = LocalStrings.current
    var code by remember { mutableStateOf(container.totp.generate(secret, net.wault.currentTimeMillis())) }

    LaunchedEffect(secret) {
        while (true) {
            code = container.totp.generate(secret, net.wault.currentTimeMillis())
            delay(1000)
        }
    }

    FieldShell(strings.fieldTotp) {
        Text(
            text = code.code.chunked(3).joinToString(" "),
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.weight(1f).padding(vertical = 10.dp)
        )
        Text(
            text = "${code.secondsRemaining}s",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = { copyToClipboard(code.code, sensitive = true) }) {
            Icon(Icons.Filled.ContentCopy, contentDescription = strings.copy)
        }
    }
}
