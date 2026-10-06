package net.wault.security

sealed interface BiometricOutcome {
    data class Success(val masterPassword: String) : BiometricOutcome
    data object Unavailable : BiometricOutcome
    data object Cancelled : BiometricOutcome
    data object Invalidated : BiometricOutcome
    data class Failed(val message: String) : BiometricOutcome
}

sealed interface BiometricEnrollOutcome {
    data object Success : BiometricEnrollOutcome
    data object Unavailable : BiometricEnrollOutcome
    data object Cancelled : BiometricEnrollOutcome
    data class Failed(val message: String) : BiometricEnrollOutcome
}

expect fun isBiometricAvailable(): Boolean

expect fun isBiometricEnrolled(): Boolean

expect fun hasBiometricSecret(): Boolean

expect suspend fun enrollBiometricUnlock(
    title: String,
    subtitle: String,
    cancel: String,
    masterPassword: String
): BiometricEnrollOutcome

expect suspend fun requestBiometricUnlock(
    title: String,
    subtitle: String,
    cancel: String
): BiometricOutcome

expect fun clearBiometricUnlock()
