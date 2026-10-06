package net.wault.security

object LockPolicy {

    const val NO_DEADLINE: Long = Long.MAX_VALUE

    fun locksOnBackground(timeoutSeconds: Int): Boolean = timeoutSeconds == AutoLock.IMMEDIATE

    fun deadlineFrom(nowMillis: Long, timeoutSeconds: Int): Long = when {
        timeoutSeconds == AutoLock.NEVER -> NO_DEADLINE
        timeoutSeconds <= 0 -> nowMillis
        else -> nowMillis + timeoutSeconds * 1000L
    }

    fun isExpired(nowMillis: Long, deadlineMillis: Long): Boolean =
        deadlineMillis != NO_DEADLINE && nowMillis >= deadlineMillis
}
