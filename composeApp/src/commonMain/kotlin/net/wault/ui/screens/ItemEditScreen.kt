package net.wault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.wault.AppContainer
import net.wault.item.ItemContent
import net.wault.item.ItemType
import net.wault.item.TotpSecret
import net.wault.ui.components.UriRuleEditor
import net.wault.strength.StrengthMeter
import net.wault.ui.components.PasswordField
import net.wault.ui.components.StrengthBar
import net.wault.ui.i18n.LocalStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditScreen(
    container: AppContainer,
    itemId: String?,
    type: ItemType,
    onDone: (String?) -> Unit
) {
    val strings = LocalStrings.current
    val existing = remember(itemId) { itemId?.let { container.items.byId(it) } }
    val existingContent = existing?.content

    var title by remember { mutableStateOf(existingContent?.title ?: "") }
    var username by remember { mutableStateOf((existingContent as? ItemContent.Login)?.username ?: "") }
    var password by remember { mutableStateOf((existingContent as? ItemContent.Login)?.password ?: "") }
    var uris by remember {
        mutableStateOf((existingContent as? ItemContent.Login)?.uris ?: emptyList())
    }
    var cardholder by remember { mutableStateOf((existingContent as? ItemContent.Card)?.cardholderName ?: "") }
    var cardNumber by remember { mutableStateOf((existingContent as? ItemContent.Card)?.number ?: "") }
    var securityCode by remember { mutableStateOf((existingContent as? ItemContent.Card)?.securityCode ?: "") }
    var body by remember { mutableStateOf((existingContent as? ItemContent.Note)?.body ?: "") }
    var notes by remember { mutableStateOf(existingContent?.notes ?: "") }
    var totpSecret by remember {
        mutableStateOf((existingContent as? ItemContent.Authenticator)?.secret?.secret ?: "")
    }
    var totpIssuer by remember {
        mutableStateOf((existingContent as? ItemContent.Authenticator)?.secret?.issuer ?: "")
    }

    val effectiveType = existing?.type ?: type
    val report = remember(password) { StrengthMeter.evaluate(password) }

    fun build(): ItemContent = when (effectiveType) {
        ItemType.Authenticator -> ItemContent.Authenticator(
            title = title,
            secret = (existingContent as? ItemContent.Authenticator)?.secret?.copy(
                secret = totpSecret.filterNot { it == ' ' }.uppercase(),
                issuer = totpIssuer
            ) ?: TotpSecret(
                secret = totpSecret.filterNot { it == ' ' }.uppercase(),
                issuer = totpIssuer,
                account = title
            ),
            notes = notes
        )
        ItemType.Login -> ItemContent.Login(
            title = title,
            username = username,
            password = password,
            uris = uris.filter { it.uri.isNotBlank() },
            totp = (existingContent as? ItemContent.Login)?.totp,
            passwordHistory = (existingContent as? ItemContent.Login)?.passwordHistory ?: emptyList(),
            passwordChangedAt = (existingContent as? ItemContent.Login)?.passwordChangedAt,
            notes = notes
        )
        ItemType.Card -> ItemContent.Card(
            title = title,
            cardholderName = cardholder,
            number = cardNumber,
            securityCode = securityCode,
            notes = notes
        )
        ItemType.Note -> ItemContent.Note(title = title, body = body)
        ItemType.Identity -> ItemContent.Identity(title = title, notes = notes)
        ItemType.SshKey -> ItemContent.SshKey(title = title, notes = notes)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) strings.addItem else strings.edit) },
                navigationIcon = {
                    IconButton(onClick = { onDone(itemId) }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val content = build()
                            val saved = if (existing == null) {
                                container.items.create(content).id
                            } else {
                                container.items.update(existing.id) { content }?.id
                            }
                            onDone(saved)
                        },
                        enabled = title.isNotBlank()
                    ) {
                        Text(strings.save)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(strings.fieldTitle) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            when (effectiveType) {
                ItemType.Authenticator -> {
                    OutlinedTextField(
                        value = totpIssuer,
                        onValueChange = { totpIssuer = it },
                        label = { Text(strings.authenticatorIssuer) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = totpSecret,
                        onValueChange = { totpSecret = it },
                        label = { Text(strings.authenticatorSecret) },
                        singleLine = true,
                        supportingText = { Text(strings.authenticatorSecretHint) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ItemType.Login -> {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(strings.fieldUsername) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PasswordField(
                            value = password,
                            onValueChange = { password = it },
                            label = strings.fieldPassword,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            password = container.generator.password(
                                net.wault.generator.PasswordRecipe()
                            )
                        }) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = strings.regenerate)
                        }
                    }
                    if (password.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        StrengthBar(level = report.level, entropyBits = report.entropyBits)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = strings.uriMatchTitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    UriRuleEditor(uris = uris, onChange = { uris = it })
                }

                ItemType.Card -> {
                    OutlinedTextField(
                        value = cardholder,
                        onValueChange = { cardholder = it },
                        label = { Text(strings.fieldCardholder) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = cardNumber,
                        onValueChange = { cardNumber = it },
                        label = { Text(strings.fieldCardNumber) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    PasswordField(
                        value = securityCode,
                        onValueChange = { securityCode = it },
                        label = strings.fieldSecurityCode,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                ItemType.Note -> {
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text(strings.fieldNotes) },
                        minLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                ItemType.Identity, ItemType.SshKey -> Unit
            }

            if (effectiveType != ItemType.Note) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(strings.fieldNotes) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
