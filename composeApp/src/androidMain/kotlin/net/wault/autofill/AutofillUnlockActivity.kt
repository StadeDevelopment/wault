package net.wault.autofill

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.fragment.app.FragmentActivity
import net.wault.AppContainer
import net.wault.R
import net.wault.WaultSession
import net.wault.item.ItemContent
import net.wault.item.VaultItem
import net.wault.security.UnlockOutcome
import net.wault.ui.UiSettings
import net.wault.ui.i18n.LocalStrings
import net.wault.ui.i18n.localeToLayoutDirection
import net.wault.ui.i18n.localeToStrings
import net.wault.ui.screens.LockScreen
import net.wault.ui.theme.WaultTheme

class AutofillUnlockActivity : FragmentActivity() {

    private var container: AppContainer? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        net.wault.MainActivity.currentActivity = this

        val created = runCatching { WaultSession.require() }.getOrNull()

        if (created == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        container = created

        if (created.isSessionValid()) {
            finishWith(created)
            return
        }

        setContent {
            remember(created) { UiSettings.bind(created.preferences) }
            val locale by UiSettings.locale
            WaultTheme {
                CompositionLocalProvider(
                    LocalStrings provides localeToStrings(locale),
                    LocalLayoutDirection provides localeToLayoutDirection(locale)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize().statusBarsPadding(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        LockScreen(
                            container = created,
                            onUnlocked = { outcome ->
                                if (outcome is UnlockOutcome.Success) {
                                    created.onUnlockedByUser()
                                    created.items.load()
                                    finishWith(created)
                                }
                            },
                            onWiped = {
                                setResult(Activity.RESULT_CANCELED)
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun finishWith(source: AppContainer) {
        source.touchSession()
        val structure = intent
            .getParcelableExtra<android.app.assist.AssistStructure>(AutofillManager.EXTRA_ASSIST_STRUCTURE)

        if (structure == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val form = FieldParser.parse(structure)
        val matches = AutofillMatcher.candidates(
            items = source.items.items.value,
            webDomain = form.webDomain,
            packageName = form.packageName
        )

        val response = FillResponse.Builder().apply {
            matches.take(MAX_SUGGESTIONS).forEach { item -> datasetFor(form, item)?.let { addDataset(it) } }
        }.build()

        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response)
        )
        finish()
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
        form.usernameId?.let { builder.setValue(it, AutofillValue.forText(login.username)); filled = true }
        form.passwordId?.let { builder.setValue(it, AutofillValue.forText(login.password)); filled = true }
        return if (filled) builder.build() else null
    }

    override fun onDestroy() {
        if (net.wault.MainActivity.currentActivity === this) {
            net.wault.MainActivity.currentActivity = null
        }
        super.onDestroy()
    }

    private companion object {
        const val MAX_SUGGESTIONS = 6
    }
}
