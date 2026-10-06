package net.wault.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import net.wault.AppContainer
import net.wault.security.BiometricEnrollOutcome
import net.wault.security.enrollBiometricUnlock
import net.wault.ui.components.PasswordField
import net.wault.ui.i18n.LocalStrings
import kotlinx.coroutines.launch

private const val MIN_MASTER_PASSWORD_LENGTH = 12

@Composable
internal fun BiometricEnrollDialog(
    container: AppContainer,
    onEnrolled: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.settingsBiometrics) },
        text = {
            Column {
                Text(strings.biometricNeedsPassword, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                PasswordField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = strings.masterPasswordLabel,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!container.secrets.verifyMasterPassword(password)) {
                        error = strings.wrongCurrentPassword
                        return@TextButton
                    }
                    busy = true
                    scope.launch {
                        val outcome = enrollBiometricUnlock(
                            strings.biometricEnrollTitle,
                            strings.biometricEnrollSubtitle,
                            strings.cancel,
                            password
                        )
                        busy = false
                        when (outcome) {
                            BiometricEnrollOutcome.Success -> {
                                container.secrets.setBiometricEnabled(true)
                                onEnrolled()
                            }
                            BiometricEnrollOutcome.Cancelled -> onDismiss()
                            BiometricEnrollOutcome.Unavailable -> error = strings.biometricInvalidated
                            is BiometricEnrollOutcome.Failed -> error = outcome.message
                        }
                    }
                },
                enabled = password.isNotBlank() && !busy
            ) {
                Text(strings.confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
internal fun ChangeMasterPasswordDialog(
    container: AppContainer,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun save() {
        when {
            next.length < MIN_MASTER_PASSWORD_LENGTH -> error = strings.masterPasswordTooWeak
            next != confirmation -> error = strings.masterPasswordMismatch
            !container.secrets.changeMasterPassword(current, next) -> error = strings.wrongCurrentPassword
            else -> onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.settingsChangeMasterPassword) },
        text = {
            Column {
                PasswordField(
                    value = current,
                    onValueChange = { current = it; error = null },
                    label = strings.currentPasswordLabel,
                    imeAction = ImeAction.Next,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                PasswordField(
                    value = next,
                    onValueChange = { next = it; error = null },
                    label = strings.newPasswordLabel,
                    imeAction = ImeAction.Next,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                PasswordField(
                    value = confirmation,
                    onValueChange = { confirmation = it; error = null },
                    label = strings.masterPasswordConfirmLabel,
                    onImeAction = { save() },
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { save() }) { Text(strings.save) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
internal fun DuressPasswordDialog(
    container: AppContainer,
    hasDuress: Boolean,
    onChanged: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.settingsDuressPassword) },
        text = {
            Column {
                Text(strings.settingsDuressPasswordBody, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                PasswordField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = strings.duressPasswordLabel,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    strings.duressPasswordWarning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (password.length < MIN_MASTER_PASSWORD_LENGTH) {
                        error = strings.masterPasswordTooWeak
                        return@TextButton
                    }
                    container.secrets.setDuressPassword(password)
                    onChanged()
                    onDismiss()
                },
                enabled = password.isNotBlank()
            ) {
                Text(strings.save)
            }
        },
        dismissButton = {
            if (hasDuress) {
                TextButton(onClick = {
                    container.secrets.clearDuressPassword()
                    onChanged()
                    onDismiss()
                }) {
                    Text(strings.settingsRemoveDuress, color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = onDismiss) { Text(strings.cancel) }
            }
        }
    )
}
