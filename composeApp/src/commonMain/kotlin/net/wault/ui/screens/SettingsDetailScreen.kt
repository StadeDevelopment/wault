package net.wault.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.wault.APP_VERSION
import net.wault.AppContainer
import net.wault.autofill.hasOverlayPermission
import net.wault.autofill.mayNeedRestrictedSettingUnlock
import net.wault.autofill.openAppInfoSettings
import net.wault.autofill.isAccessibilityFillEnabled
import net.wault.autofill.isAccessibilityFillSupported
import net.wault.autofill.isAutofillServiceEnabled
import net.wault.autofill.isAutofillServiceSupported
import net.wault.autofill.openAccessibilityFillSettings
import net.wault.autofill.openAutofillServiceSettings
import net.wault.autofill.openOverlaySettings
import net.wault.importer.ImportPreview
import net.wault.importer.VaultImporter
import net.wault.importer.pickTextFile
import net.wault.security.AutoLock
import net.wault.security.clearBiometricUnlock
import net.wault.security.hasBiometricSecret
import net.wault.security.isBiometricAvailable
import net.wault.security.isBiometricEnrolled
import net.wault.ui.SettingsSection
import net.wault.ui.ThemeMode
import net.wault.ui.UiSettings
import net.wault.ui.components.ScreenHeader
import net.wault.ui.components.SettingsGroup
import net.wault.ui.components.SettingsRow
import net.wault.ui.components.SettingsSectionLabel
import net.wault.ui.components.SettingsSwitchRow
import net.wault.ui.i18n.AppLocale
import net.wault.ui.i18n.LocalStrings
import net.wault.ui.i18n.localeDisplayName
import net.wault.ui.theme.isDynamicColorSupported
import kotlinx.coroutines.launch

@Composable
fun SettingsDetailScreen(
    container: AppContainer,
    section: SettingsSection,
    onBack: () -> Unit,
    onOpenDevices: () -> Unit,
    onWiped: () -> Unit
) {
    val strings = LocalStrings.current

    val title = when (section) {
        SettingsSection.Appearance -> strings.settingsAppearance
        SettingsSection.Security -> strings.settingsSecurity
        SettingsSection.Autofill -> strings.settingsAutofillSection
        SettingsSection.Sync -> strings.settingsSyncSection
        SettingsSection.Data -> strings.settingsDataSection
        SettingsSection.Language -> strings.settingsLanguage
        SettingsSection.About -> strings.settingsAbout
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        ScreenHeader(title = title, onBack = onBack)

        when (section) {
            SettingsSection.Appearance -> AppearanceSection()
            SettingsSection.Security -> SecuritySection(container)
            SettingsSection.Autofill -> AutofillSection(container)
            SettingsSection.Sync -> SyncSection(container, onOpenDevices)
            SettingsSection.Data -> DataSection(container, onWiped)
            SettingsSection.Language -> LanguageSection()
            SettingsSection.About -> AboutSection()
        }
    }
}

@Composable
private fun AppearanceSection() {
    val strings = LocalStrings.current
    val themeMode by UiSettings.themeMode
    val dynamic by UiSettings.dynamicColor

    SettingsSectionLabel(strings.settingsTheme)

    SettingsGroup {
        ThemeMode.entries.forEach { mode ->
            row {
                SettingsRow(
                    title = when (mode) {
                        ThemeMode.System -> strings.themeSystem
                        ThemeMode.Light -> strings.themeLight
                        ThemeMode.Dark -> strings.themeDark
                    },
                    icon = when (mode) {
                        ThemeMode.System -> Icons.Default.Layers
                        ThemeMode.Light -> Icons.Default.Palette
                        ThemeMode.Dark -> Icons.Default.DarkMode
                    },
                    trailing = { if (mode == themeMode) SelectedCheck() },
                    onClick = { UiSettings.setThemeMode(mode) }
                )
            }
        }
    }

    SettingsSectionLabel(strings.settingsDynamicColor)

    SettingsGroup {
        row {
            SettingsSwitchRow(
                title = strings.settingsDynamicColor,
                subtitle = if (isDynamicColorSupported) {
                    strings.settingsDynamicColorBody
                } else {
                    strings.settingsDynamicColorUnavailable
                },
                icon = Icons.Default.Palette,
                checked = dynamic && isDynamicColorSupported,
                enabled = isDynamicColorSupported,
                onCheckedChange = { UiSettings.setDynamicColor(it) }
            )
        }
    }
}

@Composable
private fun SecuritySection(container: AppContainer) {
    val strings = LocalStrings.current

    var autoLock by remember { mutableStateOf(container.secrets.autoLockSeconds()) }
    var autoLockOpen by remember { mutableStateOf(false) }
    var clipboardClear by remember { mutableStateOf(container.secrets.isClipboardClearEnabled()) }
    var hasDuress by remember { mutableStateOf(container.secrets.hasDuressPassword()) }
    var biometricOn by remember {
        mutableStateOf(container.secrets.isBiometricEnabled() && hasBiometricSecret())
    }
    var biometricEnrollOpen by remember { mutableStateOf(false) }
    var changePasswordOpen by remember { mutableStateOf(false) }
    var duressOpen by remember { mutableStateOf(false) }

    SettingsGroup {
        row {
            SettingsRow(
                title = strings.settingsAutoLock,
                subtitle = autoLockLabel(autoLock),
                icon = Icons.Default.Schedule,
                trailing = { ChevronEnd() },
                onClick = { autoLockOpen = true }
            )
        }

        if (isBiometricAvailable()) {
            row {
                SettingsSwitchRow(
                    title = strings.settingsBiometrics,
                    subtitle = strings.settingsBiometricsBody,
                    icon = Icons.Default.Fingerprint,
                    checked = biometricOn,
                    enabled = isBiometricEnrolled(),
                    onCheckedChange = { wanted ->
                        if (wanted) {
                            biometricEnrollOpen = true
                        } else {
                            clearBiometricUnlock()
                            container.secrets.setBiometricEnabled(false)
                            biometricOn = false
                        }
                    }
                )
            }
        }

        row {
            SettingsSwitchRow(
                title = strings.settingsClipboardClear,
                icon = Icons.Default.ContentPaste,
                checked = clipboardClear,
                onCheckedChange = {
                    clipboardClear = it
                    container.secrets.setClipboardClearEnabled(it)
                }
            )
        }
    }

    Spacer(Modifier.height(20.dp))

    SettingsGroup {
        row {
            SettingsRow(
                title = strings.settingsChangeMasterPassword,
                subtitle = strings.settingsChangeMasterPasswordBody,
                icon = Icons.Default.Key,
                trailing = { ChevronEnd() },
                onClick = { changePasswordOpen = true }
            )
        }
        row {
            SettingsRow(
                title = strings.settingsDuressPassword,
                subtitle = if (hasDuress) {
                    strings.settingsDuressPasswordSet
                } else {
                    strings.settingsDuressPasswordNone
                },
                icon = Icons.Default.Shield,
                trailing = { ChevronEnd() },
                onClick = { duressOpen = true }
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    Text(
        text = strings.settingsAutoLockSessionBody,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 28.dp)
    )

    if (autoLockOpen) {
        ChoiceDialog(
            title = strings.settingsAutoLock,
            options = AutoLock.OPTIONS,
            selected = autoLock,
            labelOf = { autoLockLabel(it) },
            onSelect = {
                autoLock = it
                container.secrets.setAutoLockSeconds(it)
                autoLockOpen = false
            },
            onDismiss = { autoLockOpen = false }
        )
    }

    if (biometricEnrollOpen) {
        BiometricEnrollDialog(
            container = container,
            onEnrolled = {
                biometricOn = true
                biometricEnrollOpen = false
            },
            onDismiss = { biometricEnrollOpen = false }
        )
    }

    if (changePasswordOpen) {
        ChangeMasterPasswordDialog(container) { changePasswordOpen = false }
    }

    if (duressOpen) {
        DuressPasswordDialog(
            container = container,
            hasDuress = hasDuress,
            onChanged = { hasDuress = container.secrets.hasDuressPassword() },
            onDismiss = { duressOpen = false }
        )
    }
}

@Composable
private fun AutofillSection(container: AppContainer) {
    val strings = LocalStrings.current
    val inForeground by container.isAppInForeground.collectAsState()

    var serviceOn by remember { mutableStateOf(isAutofillServiceEnabled()) }
    var accessibilityOn by remember { mutableStateOf(isAccessibilityFillEnabled()) }
    var overlayOn by remember { mutableStateOf(hasOverlayPermission()) }
    var restrictedLikely by remember { mutableStateOf(mayNeedRestrictedSettingUnlock()) }
    var awaitingReturn by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { if (awaitingReturn) container.endExternalFlow() }
    }

    LaunchedEffect(inForeground) {
        if (!inForeground) return@LaunchedEffect
        if (awaitingReturn) {
            awaitingReturn = false
            container.endExternalFlow()
        }
        serviceOn = isAutofillServiceEnabled()
        accessibilityOn = isAccessibilityFillEnabled()
        overlayOn = hasOverlayPermission()
        restrictedLikely = mayNeedRestrictedSettingUnlock()
    }

    fun leaveFor(open: () -> Unit) {
        if (!awaitingReturn) {
            awaitingReturn = true
            container.beginExternalFlow()
        }
        open()
    }

    if (!isAutofillServiceSupported() && !isAccessibilityFillSupported()) {
        SettingsGroup {
            row {
                SettingsRow(
                    title = strings.settingsAutofillSection,
                    subtitle = strings.autofillUnsupportedHere,
                    icon = Icons.Default.Keyboard,
                    enabled = false
                )
            }
        }
        return
    }

    SettingsGroup {
        if (isAutofillServiceSupported()) {
            row {
                SettingsRow(
                    title = strings.autofillServiceTitle,
                    subtitle = strings.autofillServiceBody,
                    icon = Icons.Default.Keyboard,
                    trailing = { StatusPill(serviceOn) },
                    onClick = { leaveFor { openAutofillServiceSettings() } }
                )
            }
        }

        if (isAccessibilityFillSupported()) {
            row {
                SettingsRow(
                    title = strings.autofillAccessibilityTitle,
                    subtitle = strings.autofillAccessibilityBody,
                    icon = Icons.Default.Accessibility,
                    trailing = { StatusPill(accessibilityOn) },
                    onClick = { leaveFor { openAccessibilityFillSettings() } }
                )
            }

            row {
                SettingsRow(
                    title = strings.autofillOverlayTitle,
                    subtitle = strings.autofillOverlayBody,
                    icon = Icons.Default.Layers,
                    trailing = { StatusPill(overlayOn) },
                    onClick = { leaveFor { openOverlaySettings() } }
                )
            }
        }
    }

    if (restrictedLikely) {
        Spacer(Modifier.height(20.dp))

        SettingsSectionLabel(strings.autofillRestrictedTitle)

        SettingsGroup {
            row {
                SettingsRow(
                    title = strings.autofillOpenAppInfo,
                    subtitle = strings.autofillRestrictedBody,
                    icon = Icons.Default.Warning,
                    trailing = { ChevronEnd() },
                    onClick = { leaveFor { openAppInfoSettings() } }
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    Text(
        text = strings.autofillAccessibilityHint,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 28.dp)
    )
}

@Composable
private fun SyncSection(container: AppContainer, onOpenDevices: () -> Unit) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var syncEnabled by remember { mutableStateOf(container.secrets.isSyncEnabled()) }

    SettingsGroup {
        row {
            SettingsSwitchRow(
                title = strings.settingsSyncEnabled,
                subtitle = strings.settingsSyncEnabledBody,
                icon = Icons.Default.Sync,
                checked = syncEnabled,
                onCheckedChange = {
                    syncEnabled = it
                    container.secrets.setSyncEnabled(it)
                    scope.launch {
                        if (it) runCatching { container.sync.start() } else runCatching { container.sync.stop() }
                    }
                }
            )
        }
        row {
            SettingsRow(
                title = strings.devicesTitle,
                icon = Icons.Default.Devices,
                trailing = { ChevronEnd() },
                onClick = onOpenDevices
            )
        }
    }
}

@Composable
private fun DataSection(container: AppContainer, onWiped: () -> Unit) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()

    var importPreview by remember { mutableStateOf<ImportPreview?>(null) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var importBusy by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }

    SettingsGroup {
        row {
            SettingsRow(
                title = strings.settingsImport,
                subtitle = importMessage ?: strings.settingsImportBody,
                icon = Icons.Default.Upload,
                enabled = !importBusy,
                trailing = { ChevronEnd() },
                onClick = {
                    importMessage = null
                    scope.launch {
                        importBusy = true
                        container.beginExternalFlow()
                        val picked = try {
                            runCatching { pickTextFile() }.getOrNull()
                        } finally {
                            container.endExternalFlow()
                        }
                        if (picked != null) {
                            val parsed = runCatching { VaultImporter.preview(picked.text) }
                            importPreview = parsed.getOrNull()
                            if (parsed.isFailure) importMessage = strings.importUnrecognised
                        }
                        importBusy = false
                    }
                }
            )
        }
    }

    Spacer(Modifier.height(20.dp))

    SettingsGroup {
        row {
            SettingsRow(
                title = strings.settingsWipeVault,
                subtitle = strings.settingsWipeVaultBody,
                icon = Icons.Default.DeleteForever,
                destructive = true,
                onClick = { confirmWipe = true }
            )
        }
    }

    importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { importPreview = null },
            title = { Text(strings.settingsImport) },
            text = {
                Column {
                    Text(strings.importPreviewBody(preview.items.size, preview.format.displayName))
                    if (preview.skipped > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            strings.importSkipped(preview.skipped),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (preview.folderNames.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            preview.folderNames.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val count = container.applyImport(preview)
                    importMessage = strings.importDone(count)
                    importPreview = null
                }) {
                    Text(strings.importAction)
                }
            },
            dismissButton = {
                TextButton(onClick = { importPreview = null }) { Text(strings.cancel) }
            }
        )
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text(strings.settingsWipeVault) },
            text = { Text(strings.settingsWipeVaultBody) },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    scope.launch {
                        container.wipeAllData()
                        onWiped()
                    }
                }) {
                    Text(strings.delete, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) { Text(strings.cancel) }
            }
        )
    }
}

@Composable
private fun LanguageSection() {
    val strings = LocalStrings.current
    val locale by UiSettings.locale

    SettingsGroup {
        AppLocale.entries.forEach { option ->
            row {
                SettingsRow(
                    title = localeDisplayName(option),
                    icon = Icons.Default.Language,
                    trailing = { if (option == locale) SelectedCheck() },
                    onClick = { UiSettings.setLocale(option) }
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    Text(
        text = strings.settingsLanguageBody,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 28.dp)
    )
}

@Composable
private fun AboutSection() {
    val strings = LocalStrings.current

    SettingsGroup {
        row {
            SettingsRow(
                title = "Wault $APP_VERSION",
                subtitle = strings.byStade
            )
        }
        row {
            SettingsRow(
                title = strings.aboutNoServers,
                subtitle = strings.aboutNoServersBody
            )
        }
        row {
            SettingsRow(
                title = strings.aboutPrivacy,
                subtitle = strings.aboutPrivacyBody
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    Text(
        text = strings.settingsAboutBody,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 28.dp)
    )
}

@Composable
private fun SelectedCheck() {
    Icon(
        imageVector = Icons.Default.Check,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(22.dp)
    )
}

@Composable
private fun StatusPill(on: Boolean) {
    val strings = LocalStrings.current
    Text(
        text = if (on) strings.autofillEnabled else strings.autofillDisabled,
        style = MaterialTheme.typography.labelMedium,
        color = if (on) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    labelOf: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                SettingsGroup(horizontalPadding = 0.dp) {
                    options.forEach { option ->
                        row {
                            SettingsRow(
                                title = labelOf(option),
                                trailing = { if (option == selected) SelectedCheck() },
                                onClick = { onSelect(option) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strings.close) }
        }
    )
}

@Composable
private fun autoLockLabel(value: Int): String {
    val strings = LocalStrings.current
    return when {
        value == AutoLock.IMMEDIATE -> strings.autoLockImmediate
        value == AutoLock.NEVER -> strings.autoLockNever
        value < 60 -> strings.autoLockSeconds(value)
        value < 3600 -> strings.autoLockMinutes(value / 60)
        else -> strings.autoLockHours(value / 3600)
    }
}
