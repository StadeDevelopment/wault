package net.wault.autofill

import android.text.InputType
import android.view.accessibility.AccessibilityNodeInfo

internal data class ScannedField(
    val node: AccessibilityNodeInfo,
    val kind: FillFieldKind
)

internal data class ScannedForm(
    val username: ScannedField?,
    val password: ScannedField?,
    val packageName: String?,
    val webDomain: String?
) {
    val isUsable: Boolean get() = username != null || password != null
}

private const val MAX_NODES = 600

internal object AccessibilityFormScanner {

    fun signalsOf(node: AccessibilityNodeInfo): FieldSignals {
        val inputType = node.inputType
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val isTextClass = (inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT

        val passwordVariation = isTextClass && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )
        val emailVariation = isTextClass && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        )

        return FieldSignals(
            passwordFlagged = node.isPassword,
            inputTypePassword = passwordVariation,
            inputTypeEmail = emailVariation,
            hint = node.hintText?.toString(),
            idEntry = node.viewIdResourceName?.substringAfterLast('/'),
            contentDescription = node.contentDescription?.toString()
        )
    }

    fun classify(node: AccessibilityNodeInfo): FillFieldKind? {
        if (!node.isEditable) return null
        if (!node.isVisibleToUser) return null
        return FieldHeuristics.classify(signalsOf(node))
    }

    fun scan(root: AccessibilityNodeInfo?, focused: AccessibilityNodeInfo?): ScannedForm? {
        if (root == null) return null

        val editable = ArrayList<AccessibilityNodeInfo>()
        collectEditable(root, editable, intArrayOf(0))
        if (editable.isEmpty()) return null

        val classified = editable.mapNotNull { node ->
            classify(node)?.let { ScannedField(node, it) }
        }
        if (classified.isEmpty()) return null

        val password = classified.firstOrNull { it.kind == FillFieldKind.Password }
        val username = classified.firstOrNull { it.kind == FillFieldKind.Username }
            ?: fallbackUsername(editable, password?.node)

        val focusedIsKnown = focused != null && classified.any { sameNode(it.node, focused) }
        val focusedIsEditable = focused?.isEditable == true
        if (focused != null && focusedIsEditable && !focusedIsKnown && password == null) return null

        return ScannedForm(
            username = username,
            password = password,
            packageName = root.packageName?.toString(),
            webDomain = null
        )
    }

    private fun fallbackUsername(
        editable: List<AccessibilityNodeInfo>,
        password: AccessibilityNodeInfo?
    ): ScannedField? {
        if (password == null) return null
        val index = editable.indexOfFirst { sameNode(it, password) }
        if (index <= 0) return null
        val candidate = editable[index - 1]
        if (candidate.isPassword) return null
        return ScannedField(candidate, FillFieldKind.Username)
    }

    private fun sameNode(a: AccessibilityNodeInfo, b: AccessibilityNodeInfo): Boolean =
        runCatching { a == b }.getOrDefault(false)

    private fun collectEditable(
        node: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>,
        budget: IntArray
    ) {
        if (budget[0] >= MAX_NODES) return
        budget[0] += 1

        if (node.isEditable && node.isVisibleToUser) out.add(node)

        for (index in 0 until node.childCount) {
            val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
            collectEditable(child, out, budget)
        }
    }
}
