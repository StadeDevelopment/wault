package net.wault.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import net.wault.R
import net.wault.item.ItemContent
import net.wault.item.VaultItem

class WaultAutofillService : AutofillService() {

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess(null)
            return
        }

        val form = FieldParser.parse(structure)
        if (!form.isUsable) {
            callback.onSuccess(null)
            return
        }

        val container = AutofillBridge.unlocked()
        val response = if (container == null) {
            lockedResponse(form)
        } else {
            val matches = AutofillMatcher.candidates(
                items = container.items.items.value,
                webDomain = form.webDomain,
                packageName = form.packageName
            )
            if (matches.isEmpty()) null else unlockedResponse(form, matches)
        }

        callback.onSuccess(response)
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess()
            return
        }

        val form = FieldParser.parse(structure)
        val container = AutofillBridge.unlocked()
        if (container == null) {
            callback.onFailure(getString(R.string.autofill_locked))
            return
        }

        val username = form.usernameId?.let { valueOf(structure, it) }.orEmpty()
        val password = form.passwordId?.let { valueOf(structure, it) }.orEmpty()
        if (password.isBlank()) {
            callback.onSuccess()
            return
        }

        val label = form.webDomain
            ?: form.packageName?.let { AutofillMatcher.domainFromPackage(it) ?: it }
            ?: getString(R.string.app_name)
        val uri = form.webDomain?.let { "https://$it" }
            ?: form.packageName?.let { AutofillMatcher.bindingUriFor(it) }

        runCatching {
            container.items.create(
                ItemContent.Login(
                    title = label,
                    username = username,
                    password = password,
                    uris = listOfNotNull(uri?.let { net.wault.item.MatchableUri(it) })
                )
            )
        }
        callback.onSuccess()
    }

    private fun lockedResponse(form: ParsedForm): FillResponse {
        val presentation = RemoteViews(packageName, R.layout.autofill_item).apply {
            setTextViewText(R.id.autofill_title, getString(R.string.autofill_unlock))
            setTextViewText(R.id.autofill_subtitle, getString(R.string.app_name))
        }

        val intent = Intent(this, AutofillUnlockActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            UNLOCK_REQUEST,
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        return FillResponse.Builder()
            .setAuthentication(form.ids(), pending.intentSender, presentation)
            .also { attachSaveInfo(it, form) }
            .build()
    }

    private fun unlockedResponse(form: ParsedForm, matches: List<VaultItem>): FillResponse {
        val builder = FillResponse.Builder()
        matches.take(MAX_SUGGESTIONS).forEach { item ->
            datasetFor(form, item)?.let { builder.addDataset(it) }
        }
        attachSaveInfo(builder, form)
        return builder.build()
    }

    private fun datasetFor(form: ParsedForm, item: VaultItem): Dataset? {
        val login = item.content as? ItemContent.Login ?: return null

        val presentation = RemoteViews(packageName, R.layout.autofill_item).apply {
            setTextViewText(R.id.autofill_title, item.title.ifBlank { getString(R.string.app_name) })
            setTextViewText(
                R.id.autofill_subtitle,
                login.username.ifBlank { getString(R.string.autofill_no_username) }
            )
        }

        val builder = Dataset.Builder(presentation)
        var filled = false
        form.usernameId?.let {
            builder.setValue(it, AutofillValue.forText(login.username))
            filled = true
        }
        form.passwordId?.let {
            builder.setValue(it, AutofillValue.forText(login.password))
            filled = true
        }
        return if (filled) builder.build() else null
    }

    private fun attachSaveInfo(builder: FillResponse.Builder, form: ParsedForm) {
        val required = listOfNotNull(form.passwordId).toTypedArray()
        if (required.isEmpty()) return

        val type = if (form.usernameId != null) {
            SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD
        } else {
            SaveInfo.SAVE_DATA_TYPE_PASSWORD
        }

        val saveInfo = SaveInfo.Builder(type, required)
            .also { info ->
                form.usernameId?.let { info.setOptionalIds(arrayOf(it)) }
            }
            .build()
        builder.setSaveInfo(saveInfo)
    }

    private fun valueOf(
        structure: android.app.assist.AssistStructure,
        id: android.view.autofill.AutofillId
    ): String? {
        for (index in 0 until structure.windowNodeCount) {
            findValue(structure.getWindowNodeAt(index).rootViewNode, id)?.let { return it }
        }
        return null
    }

    private fun findValue(
        node: android.app.assist.AssistStructure.ViewNode,
        id: android.view.autofill.AutofillId
    ): String? {
        if (node.autofillId == id) {
            val value = node.autofillValue
            if (value != null && value.isText) return value.textValue.toString()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                node.text?.let { return it.toString() }
            }
        }
        for (index in 0 until node.childCount) {
            findValue(node.getChildAt(index), id)?.let { return it }
        }
        return null
    }

    private companion object {
        const val MAX_SUGGESTIONS = 6
        const val UNLOCK_REQUEST = 4801
    }
}
