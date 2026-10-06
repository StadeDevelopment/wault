package net.wault.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import net.wault.AppContainer
import net.wault.strength.StrengthMeter
import net.wault.ui.components.PasswordField
import net.wault.ui.components.StrengthBar
import net.wault.ui.i18n.LocalStrings

private const val MIN_MASTER_PASSWORD_LENGTH = 12

@Composable
fun OnboardingScreen(
    container: AppContainer,
    onVaultCreated: (String) -> Unit,
    onJoinVault: (String) -> Unit
) {
    val strings = LocalStrings.current
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val report = remember(password) { StrengthMeter.evaluate(password) }
    val canSubmit = password.length >= MIN_MASTER_PASSWORD_LENGTH && password == confirmation

    fun create() {
        when {
            password.length < MIN_MASTER_PASSWORD_LENGTH -> error = strings.masterPasswordTooWeak
            password != confirmation -> error = strings.masterPasswordMismatch
            else -> {
                val outcome = container.vault.setup(password)
                onVaultCreated(outcome.recoveryKey)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(Modifier.height(56.dp))

        Text(strings.onboardingTitle, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            strings.onboardingBody,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        PasswordField(
            value = password,
            onValueChange = { password = it; error = null },
            label = strings.masterPasswordLabel,
            imeAction = ImeAction.Next,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        StrengthBar(level = report.level, entropyBits = report.entropyBits)

        Spacer(Modifier.height(16.dp))

        PasswordField(
            value = confirmation,
            onValueChange = { confirmation = it; error = null },
            label = strings.masterPasswordConfirmLabel,
            imeAction = ImeAction.Done,
            onImeAction = { if (canSubmit) create() },
            modifier = Modifier.fillMaxWidth()
        )

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { create() },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(strings.createVault)
        }

        Spacer(Modifier.height(8.dp))

        TextButton(
            onClick = {
                when {
                    password.length < MIN_MASTER_PASSWORD_LENGTH -> error = strings.masterPasswordTooWeak
                    password != confirmation -> error = strings.masterPasswordMismatch
                    else -> {
                        container.vault.setup(password)
                        onJoinVault(password)
                    }
                }
            },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(strings.pairJoinAction)
        }

        Spacer(Modifier.height(8.dp))

        Text(
            strings.pairJoinBody,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(48.dp))
    }
}
