package net.wault.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import net.wault.AppContainer
import net.wault.security.BiometricOutcome
import net.wault.security.UnlockOutcome
import net.wault.security.hasBiometricSecret
import net.wault.security.isBiometricEnrolled
import net.wault.security.requestBiometricUnlock
import net.wault.ui.LocalAppReady
import net.wault.ui.isKeyboardVisible
import net.wault.ui.components.PasswordField
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.FilledIconButton
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import net.wault.ui.theme.WaultMark
import net.wault.ui.i18n.LocalStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val MARK_SIZE = 104.dp
private val MARK_SIZE_COMPACT = 56.dp
private val TOP_SPACE = 72.dp
private val TOP_SPACE_COMPACT = 16.dp
private val SUBMIT_SIZE = 56.dp
private const val GLOW_SCALE = 2.1f
private const val MARK_RESIZE_MS = 220

private const val AUTO_PROMPT_DELAY_MS = 350L
private const val AUTO_PROMPT_RETRY_MS = 600L
private const val AUTO_PROMPT_ATTEMPTS = 3

@Composable
fun LockScreen(
    container: AppContainer,
    onUnlocked: (UnlockOutcome) -> Unit,
    onWiped: () -> Unit
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()

    var password by remember { mutableStateOf("") }
    var recoveryMode by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var lockoutUntil by remember { mutableStateOf(container.vault.lockoutUntilMillis()) }
    var biometricOffered by remember {
        mutableStateOf(
            container.secrets.isBiometricEnabled() && isBiometricEnrolled() && hasBiometricSecret()
        )
    }
    var now by remember { mutableStateOf(container.vault.nowMillis()) }
    var promptArmed by remember { mutableStateOf(true) }

    val inForeground by container.isAppInForeground.collectAsState()
    val appReady = LocalAppReady.current
    val lockedOut = lockoutUntil > now

    LaunchedEffect(lockoutUntil) {
        while (container.vault.lockoutUntilMillis() > container.vault.nowMillis()) {
            now = container.vault.nowMillis()
            delay(500)
        }
        now = container.vault.nowMillis()
    }

    fun submit() {
        val outcome = if (recoveryMode) {
            container.vault.unlockWithRecoveryKey(password)
        } else {
            container.vault.unlock(password)
        }
        when (outcome) {
            is UnlockOutcome.Success -> {
                password = ""
                message = null
                onUnlocked(outcome)
            }
            is UnlockOutcome.Wrong -> {
                password = ""
                message = if (outcome.remainingBeforeLockout > 0) {
                    strings.wrongPasswordRemaining(outcome.remainingBeforeLockout)
                } else {
                    strings.wrongPassword
                }
            }
            is UnlockOutcome.LockedOut -> {
                password = ""
                lockoutUntil = outcome.untilMillis
                message = null
            }
            is UnlockOutcome.Duress -> {
                password = ""
                scope.launch {
                    container.wipeAllData()
                    onWiped()
                }
            }
            UnlockOutcome.NotInitialized -> message = strings.vaultNotInitialized
            is UnlockOutcome.Error -> message = outcome.message
        }
    }

    suspend fun promptBiometric(reportFailure: Boolean = true): Boolean {
        val outcome = runCatching {
            requestBiometricUnlock(
                strings.unlockTitle,
                strings.biometricPromptSubtitle,
                strings.cancel
            )
        }.getOrElse { return false }

        return when (outcome) {
            is BiometricOutcome.Success -> {
                password = outcome.masterPassword
                submit()
                true
            }
            BiometricOutcome.Invalidated -> {
                biometricOffered = false
                container.secrets.setBiometricEnabled(false)
                message = strings.biometricInvalidated
                true
            }
            is BiometricOutcome.Failed -> {
                if (reportFailure) message = outcome.message
                false
            }
            BiometricOutcome.Cancelled -> true
            BiometricOutcome.Unavailable -> false
        }
    }

    LaunchedEffect(inForeground) {
        if (!inForeground) promptArmed = true
    }

    LaunchedEffect(inForeground, biometricOffered, lockedOut, appReady) {
        if (!appReady || !inForeground || !biometricOffered || lockedOut) return@LaunchedEffect
        if (!promptArmed) return@LaunchedEffect
        promptArmed = false
        delay(AUTO_PROMPT_DELAY_MS)
        repeat(AUTO_PROMPT_ATTEMPTS) { attempt ->
            val last = attempt == AUTO_PROMPT_ATTEMPTS - 1
            if (promptBiometric(reportFailure = last)) return@LaunchedEffect
            delay(AUTO_PROMPT_RETRY_MS)
        }
    }

    val keyboardOpen = isKeyboardVisible()

    val markSize by animateDpAsState(
        targetValue = if (keyboardOpen) MARK_SIZE_COMPACT else MARK_SIZE,
        animationSpec = tween(MARK_RESIZE_MS),
        label = "lockMarkSize"
    )
    val topSpace by animateDpAsState(
        targetValue = if (keyboardOpen) TOP_SPACE_COMPACT else TOP_SPACE,
        animationSpec = tween(MARK_RESIZE_MS),
        label = "lockTopSpace"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(topSpace))

        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(markSize * GLOW_SCALE)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
            Icon(
                imageVector = WaultMark,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(markSize)
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(strings.unlockTitle, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            if (recoveryMode) strings.useRecoveryKey else strings.unlockSubtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (recoveryMode) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; message = null },
                    label = { Text(strings.recoveryKeyPlaceholder) },
                    singleLine = true,
                    enabled = !lockedOut,
                    shape = CircleShape,
                    modifier = Modifier.weight(1f)
                )
            } else {
                PasswordField(
                    value = password,
                    onValueChange = { password = it; message = null },
                    label = strings.masterPasswordLabel,
                    enabled = !lockedOut,
                    imeAction = ImeAction.Go,
                    onImeAction = { if (!lockedOut && password.isNotEmpty()) submit() },
                    shape = CircleShape,
                    modifier = Modifier.weight(1f)
                )
            }

            FilledIconButton(
                onClick = { submit() },
                enabled = !lockedOut && password.isNotEmpty(),
                modifier = Modifier.size(SUBMIT_SIZE)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = strings.unlock
                )
            }
        }

        if (lockedOut) {
            Spacer(Modifier.height(12.dp))
            Text(
                strings.retryIn(strings.formatRemainingTime((lockoutUntil - now) / 1000)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        message?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(20.dp))

        if (!recoveryMode && biometricOffered) {
            Button(
                onClick = { scope.launch { promptBiometric() } },
                enabled = !lockedOut,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(SUBMIT_SIZE)
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(strings.unlockWithBiometrics)
            }
        }

        Spacer(Modifier.height(4.dp))

        TextButton(onClick = { recoveryMode = !recoveryMode; password = ""; message = null }) {
            Text(if (recoveryMode) strings.back else strings.useRecoveryKey)
        }

        Spacer(Modifier.height(48.dp))
    }
}
