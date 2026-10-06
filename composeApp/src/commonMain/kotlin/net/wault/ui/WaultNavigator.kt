package net.wault.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class WaultNavigator(initial: Screen) {

    var current: Screen by mutableStateOf(initial)
        private set

    private val stack = mutableStateListOf<Screen>()

    val canGoBack: Boolean get() = stack.isNotEmpty()

    val depth: Int get() = stack.size

    fun push(target: Screen) {
        if (target == current) return
        stack.add(current)
        current = target
    }

    fun selectTab(target: Screen) {
        stack.clear()
        if (target != Screen.Vault) stack.add(Screen.Vault)
        current = target
    }

    fun resetTo(target: Screen) {
        stack.clear()
        current = target
    }

    fun back(): Boolean {
        val previous = stack.removeLastOrNull() ?: return false
        current = previous
        return true
    }

    fun backTo(target: Screen) {
        if (!back()) {
            resetTo(target)
            return
        }
        if (current != target) push(target)
    }
}
