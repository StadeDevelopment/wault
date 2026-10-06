package net.wault.autofill

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import net.wault.AppContainer
import net.wault.WaultSession
import net.wault.item.ItemContent
import net.wault.item.ItemSearch
import net.wault.item.VaultItem
import net.wault.security.UnlockOutcome
import net.wault.ui.UiSettings
import net.wault.ui.components.GroupPosition
import net.wault.ui.components.LocalGroupPosition
import net.wault.ui.components.ScreenHeader
import net.wault.ui.components.SettingsRow
import net.wault.ui.i18n.LocalStrings
import net.wault.ui.i18n.localeToLayoutDirection
import net.wault.ui.i18n.localeToStrings
import net.wault.ui.screens.LockScreen
import net.wault.ui.theme.WaultTheme

class AccessibilityFillActivity : FragmentActivity() {

    private var container: AppContainer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        net.wault.MainActivity.currentActivity = this

        val targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE)

        val resolved = runCatching { WaultSession.require() }.getOrNull()

        if (resolved == null) {
            finish()
            return
        }
        container = resolved

        setContent {
            remember(resolved) { UiSettings.bind(resolved.preferences) }
            val locale by UiSettings.locale
            var unlocked by remember { mutableStateOf(resolved.isSessionValid()) }

            WaultTheme {
                CompositionLocalProvider(
                    LocalStrings provides localeToStrings(locale),
                    LocalLayoutDirection provides localeToLayoutDirection(locale)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize().statusBarsPadding(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        if (!unlocked) {
                            LockScreen(
                                container = resolved,
                                onUnlocked = { outcome ->
                                    if (outcome is UnlockOutcome.Success) {
                                        resolved.onUnlockedByUser()
                                        resolved.items.load()
                                        unlocked = true
                                    }
                                },
                                onWiped = { finish() }
                            )
                        } else {
                            CredentialPicker(
                                container = resolved,
                                targetPackage = targetPackage,
                                onPick = { item -> deliver(item) },
                                onCancel = { finish() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun deliver(item: VaultItem) {
        val login = item.content as? ItemContent.Login ?: return
        container?.touchSession()
        AccessibilityFillBridge.deliver(login.username, login.password)
        finish()
    }

    override fun onDestroy() {
        if (net.wault.MainActivity.currentActivity === this) {
            net.wault.MainActivity.currentActivity = null
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "net.wault.extra.TARGET_PACKAGE"
    }
}

@Composable
private fun CredentialPicker(
    container: AppContainer,
    targetPackage: String?,
    onPick: (VaultItem) -> Unit,
    onCancel: () -> Unit
) {
    val strings = LocalStrings.current
    val items by container.items.items.collectAsState()
    var query by remember { mutableStateOf("") }

    val logins = remember(items) { items.filter { it.content is ItemContent.Login } }

    val suggested = remember(logins, targetPackage) {
        if (targetPackage == null) {
            emptyList()
        } else {
            AutofillMatcher.candidates(items = logins, webDomain = null, packageName = targetPackage)
        }
    }

    val rest = remember(logins, suggested) {
        val suggestedIds = suggested.map { it.id }.toSet()
        logins.filterNot { it.id in suggestedIds }
    }

    val visible = remember(suggested, rest, query) {
        if (query.isBlank()) suggested + rest else ItemSearch.filter(logins, query)
    }

    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        ScreenHeader(title = strings.autofillPickItem, onBack = onCancel)

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(strings.search) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(16.dp))

        if (visible.isEmpty()) {
            Text(
                text = strings.autofillNoMatches,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp)
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(visible, key = { _, item -> item.id }) { index, item ->
                val login = item.content as? ItemContent.Login
                val position = when {
                    visible.size <= 1 -> GroupPosition.Single
                    index == 0 -> GroupPosition.Top
                    index == visible.lastIndex -> GroupPosition.Bottom
                    else -> GroupPosition.Middle
                }
                CompositionLocalProvider(LocalGroupPosition provides position) {
                    SettingsRow(
                        title = item.title.ifBlank { item.id.take(8) },
                        subtitle = login?.username?.ifBlank { null },
                        icon = Icons.Default.Key,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        onClick = { onPick(item) }
                    )
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}
