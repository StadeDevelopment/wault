package net.wault

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

val backgroundFailureMessage = MutableStateFlow<String?>(null)

val backgroundFailures = CoroutineExceptionHandler { _, throwable ->
    if (throwable is CancellationException) return@CoroutineExceptionHandler
    backgroundFailureMessage.value = throwable.message ?: throwable::class.simpleName
}
