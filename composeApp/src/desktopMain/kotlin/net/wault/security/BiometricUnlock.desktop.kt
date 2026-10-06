package net.wault.security

actual fun isBiometricAvailable(): Boolean = false

actual fun isBiometricEnrolled(): Boolean = false

actual fun hasBiometricSecret(): Boolean = false

actual suspend fun enrollBiometricUnlock(
    title: String,
    subtitle: String,
    cancel: String,
    masterPassword: String
): BiometricEnrollOutcome = BiometricEnrollOutcome.Unavailable

actual suspend fun requestBiometricUnlock(
    title: String,
    subtitle: String,
    cancel: String
): BiometricOutcome = BiometricOutcome.Unavailable

actual fun clearBiometricUnlock() = Unit
