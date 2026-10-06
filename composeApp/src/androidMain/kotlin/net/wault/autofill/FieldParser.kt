package net.wault.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId

internal enum class FieldKind { Username, Password }

internal data class ParsedField(val id: AutofillId, val kind: FieldKind)

internal data class ParsedForm(
    val fields: List<ParsedField>,
    val webDomain: String?,
    val packageName: String?
) {
    val usernameId: AutofillId? get() = fields.firstOrNull { it.kind == FieldKind.Username }?.id
    val passwordId: AutofillId? get() = fields.firstOrNull { it.kind == FieldKind.Password }?.id
    val isUsable: Boolean get() = passwordId != null || usernameId != null
    fun ids(): Array<AutofillId> = fields.map { it.id }.toTypedArray()
}

internal object FieldParser {

    fun parse(structure: AssistStructure): ParsedForm {
        val fields = ArrayList<ParsedField>()
        var domain: String? = null

        for (index in 0 until structure.windowNodeCount) {
            val found = walk(structure.getWindowNodeAt(index).rootViewNode, fields)
            if (domain == null) domain = found
        }

        return ParsedForm(
            fields = dedupe(fields),
            webDomain = domain,
            packageName = structure.activityComponent?.packageName
        )
    }

    private fun walk(node: AssistStructure.ViewNode, out: MutableList<ParsedField>): String? {
        var domain = node.webDomain?.takeIf { it.isNotBlank() }

        classify(node)?.let { kind ->
            node.autofillId?.let { out.add(ParsedField(it, kind)) }
        }

        for (index in 0 until node.childCount) {
            val child = walk(node.getChildAt(index), out)
            if (domain == null) domain = child
        }
        return domain
    }

    private fun classify(node: AssistStructure.ViewNode): FieldKind? {
        if (node.autofillId == null) return null
        if (node.autofillType != View.AUTOFILL_TYPE_TEXT) return null

        val inputType = node.inputType
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val isText = (inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT

        val signals = FieldSignals(
            autofillHints = node.autofillHints?.toList().orEmpty(),
            inputTypePassword = isText && variation in setOf(
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            ),
            inputTypeEmail = isText && variation in setOf(
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
            ),
            hint = node.hint,
            idEntry = node.idEntry,
            contentDescription = node.contentDescription?.toString()
        )

        return when (FieldHeuristics.classify(signals)) {
            FillFieldKind.Password -> FieldKind.Password
            FillFieldKind.Username -> FieldKind.Username
            null -> null
        }
    }

    private fun dedupe(fields: List<ParsedField>): List<ParsedField> {
        val username = fields.firstOrNull { it.kind == FieldKind.Username }
        val password = fields.firstOrNull { it.kind == FieldKind.Password }
        return listOfNotNull(username, password)
    }
}
