package net.wault.autofill

object AccessibilityFillBridge {

    @Volatile
    private var service: WaultAccessibilityService? = null

    fun attach(value: WaultAccessibilityService) {
        service = value
    }

    fun detach(value: WaultAccessibilityService) {
        if (service === value) service = null
    }

    fun isRunning(): Boolean = service != null

    fun deliver(username: String?, password: String?) {
        service?.deliver(username, password)
    }
}
