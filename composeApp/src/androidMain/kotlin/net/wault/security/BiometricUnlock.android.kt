package net.wault.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import net.wault.MainActivity
import net.wault.requireAppContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

private const val KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "wault.biometric.v1"
private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG
private const val GCM_TAG_BITS = 128
private const val NONCE_LEN = 12

private fun secretFile(): File = File(File(requireAppContext().filesDir, "vault"), "biometric.bin")

actual fun isBiometricAvailable(): Boolean =
    BiometricManager.from(requireAppContext()).canAuthenticate(AUTHENTICATORS) !=
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE

actual fun isBiometricEnrolled(): Boolean =
    BiometricManager.from(requireAppContext()).canAuthenticate(AUTHENTICATORS) ==
        BiometricManager.BIOMETRIC_SUCCESS

actual fun hasBiometricSecret(): Boolean = secretFile().exists() && secretFile().length() > NONCE_LEN

actual fun clearBiometricUnlock() {
    runCatching { secretFile().delete() }
    runCatching {
        KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
    }
}

private const val RESUME_TIMEOUT_MS = 4_000L
private const val RESUME_POLL_MS = 50L

private suspend fun awaitResumed(activity: FragmentActivity): Boolean {
    var waited = 0L
    while (waited < RESUME_TIMEOUT_MS) {
        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return true
        if (activity.isFinishing || activity.isDestroyed) return false
        delay(RESUME_POLL_MS)
        waited += RESUME_POLL_MS
    }
    return activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
}

actual suspend fun enrollBiometricUnlock(
    title: String,
    subtitle: String,
    cancel: String,
    masterPassword: String
): BiometricEnrollOutcome {
    val activity = MainActivity.currentActivity ?: return BiometricEnrollOutcome.Unavailable
    if (!isBiometricEnrolled()) return BiometricEnrollOutcome.Unavailable
    if (!awaitResumed(activity)) return BiometricEnrollOutcome.Unavailable

    clearBiometricUnlock()

    val cipher = runCatching {
        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, createKey()) }
    }.getOrElse { return BiometricEnrollOutcome.Failed(it.message ?: "key creation failed") }

    return when (val result = authenticate(activity, title, subtitle, cancel, cipher)) {
        is AuthResult.Success -> {
            val authenticated = result.cipher ?: return BiometricEnrollOutcome.Failed("no cipher returned")
            runCatching {
                val sealed = authenticated.doFinal(masterPassword.encodeToByteArray())
                val file = secretFile()
                file.parentFile?.mkdirs()
                file.writeBytes(authenticated.iv + sealed)
            }.fold(
                onSuccess = { BiometricEnrollOutcome.Success },
                onFailure = { BiometricEnrollOutcome.Failed(it.message ?: "could not store the secret") }
            )
        }

        AuthResult.Cancelled -> BiometricEnrollOutcome.Cancelled
        is AuthResult.Error -> BiometricEnrollOutcome.Failed(result.message)
        AuthResult.Invalidated -> BiometricEnrollOutcome.Unavailable
    }
}

actual suspend fun requestBiometricUnlock(
    title: String,
    subtitle: String,
    cancel: String
): BiometricOutcome {
    val activity = MainActivity.currentActivity ?: return BiometricOutcome.Unavailable
    if (!isBiometricEnrolled()) return BiometricOutcome.Unavailable
    if (!awaitResumed(activity)) return BiometricOutcome.Unavailable

    val stored = runCatching { secretFile().readBytes() }.getOrNull()
    if (stored == null || stored.size <= NONCE_LEN) return BiometricOutcome.Unavailable

    val key = runCatching { loadKey() }.getOrNull()
    if (key == null) {
        clearBiometricUnlock()
        return BiometricOutcome.Invalidated
    }

    val cipher = try {
        Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, stored, 0, NONCE_LEN))
        }
    } catch (_: KeyPermanentlyInvalidatedException) {
        clearBiometricUnlock()
        return BiometricOutcome.Invalidated
    } catch (error: Throwable) {
        return BiometricOutcome.Failed(error.message ?: "cipher setup failed")
    }

    return when (val result = authenticate(activity, title, subtitle, cancel, cipher)) {
        is AuthResult.Success -> {
            val authenticated = result.cipher ?: return BiometricOutcome.Failed("no cipher returned")
            runCatching {
                authenticated.doFinal(stored, NONCE_LEN, stored.size - NONCE_LEN).decodeToString()
            }.fold(
                onSuccess = { BiometricOutcome.Success(it) },
                onFailure = {
                    clearBiometricUnlock()
                    BiometricOutcome.Invalidated
                }
            )
        }

        AuthResult.Cancelled -> BiometricOutcome.Cancelled
        AuthResult.Invalidated -> {
            clearBiometricUnlock()
            BiometricOutcome.Invalidated
        }
        is AuthResult.Error -> BiometricOutcome.Failed(result.message)
    }
}

private const val TRANSFORMATION =
    "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}"

private fun createKey(): SecretKey {
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
    generator.init(
        KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .build()
    )
    return generator.generateKey()
}

private fun loadKey(): SecretKey? {
    val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
    return store.getKey(KEY_ALIAS, null) as? SecretKey
}

private sealed interface AuthResult {
    data class Success(val cipher: Cipher?) : AuthResult
    data object Cancelled : AuthResult
    data object Invalidated : AuthResult
    data class Error(val message: String) : AuthResult
}

private suspend fun authenticate(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    cancel: String,
    cipher: Cipher
): AuthResult = suspendCancellableCoroutine { continuation ->
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (continuation.isActive) {
                    continuation.resume(AuthResult.Success(result.cryptoObject?.cipher))
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (!continuation.isActive) return
                val outcome = when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED -> AuthResult.Cancelled

                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                    BiometricPrompt.ERROR_HW_NOT_PRESENT -> AuthResult.Invalidated

                    else -> AuthResult.Error(errString.toString())
                }
                continuation.resume(outcome)
            }
        }
    )

    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setNegativeButtonText(cancel)
        .setAllowedAuthenticators(AUTHENTICATORS)
        .build()

    val started = runCatching {
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
    if (started.isFailure) {
        if (continuation.isActive) {
            continuation.resume(AuthResult.Error(started.exceptionOrNull()?.message ?: "prompt failed"))
        }
        return@suspendCancellableCoroutine
    }
    continuation.invokeOnCancellation { runCatching { prompt.cancelAuthentication() } }
}
