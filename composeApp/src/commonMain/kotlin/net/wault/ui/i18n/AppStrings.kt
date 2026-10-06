package net.wault.ui.i18n

import androidx.compose.runtime.compositionLocalOf

abstract class AppStrings {
    abstract val back: String
    abstract val cancel: String
    abstract val save: String
    abstract val delete: String
    abstract val edit: String
    abstract val copy: String
    abstract val copied: String
    abstract val close: String
    abstract val loading: String
    abstract val search: String
    abstract val confirm: String

    abstract val onboardingTitle: String
    abstract val onboardingBody: String
    abstract val createVault: String
    abstract val masterPasswordLabel: String
    abstract val masterPasswordConfirmLabel: String
    abstract val masterPasswordMismatch: String
    abstract val masterPasswordTooWeak: String
    abstract val recoveryKeyTitle: String
    abstract val recoveryKeyBody: String
    abstract val recoveryKeySaved: String

    abstract val unlockTitle: String
    abstract val unlockSubtitle: String
    abstract val unlock: String
    abstract val useRecoveryKey: String
    abstract val recoveryKeyPlaceholder: String
    abstract val wrongPassword: String
    abstract fun wrongPasswordRemaining(remaining: Int): String
    abstract fun retryIn(formattedTime: String): String
    abstract fun formatRemainingTime(seconds: Long): String
    abstract val unlockWithBiometrics: String
    abstract val vaultNotInitialized: String
    abstract val databaseSchemaFailedTitle: String
    abstract val databaseSchemaFailedBody: String

    abstract val tabVault: String
    abstract val tabGenerator: String
    abstract val tabHealth: String
    abstract val tabSettings: String

    abstract val vaultEmptyTitle: String
    abstract val vaultEmptyBody: String
    abstract val addItem: String
    abstract val newLogin: String
    abstract val newCard: String
    abstract val newNote: String
    abstract val newIdentity: String
    abstract val newSshKey: String
    abstract fun itemCount(count: Int): String

    abstract val fieldTitle: String
    abstract val fieldUsername: String
    abstract val fieldPassword: String
    abstract val fieldWebsite: String
    abstract val fieldNotes: String
    abstract val fieldTotp: String
    abstract val fieldCardholder: String
    abstract val fieldCardNumber: String
    abstract val fieldExpiry: String
    abstract val fieldSecurityCode: String
    abstract val revealPassword: String
    abstract val hidePassword: String
    abstract val passwordHistory: String
    abstract val deleteItemTitle: String
    abstract val deleteItemBody: String

    abstract val generatorTitle: String
    abstract val generatorPassword: String
    abstract val generatorPassphrase: String
    abstract val generatorLength: String
    abstract val generatorWords: String
    abstract val generatorUppercase: String
    abstract val generatorLowercase: String
    abstract val generatorDigits: String
    abstract val generatorSymbols: String
    abstract val generatorExcludeAmbiguous: String
    abstract val generatorCapitalize: String
    abstract val generatorIncludeNumber: String
    abstract val regenerate: String
    abstract fun entropyBits(bits: Int): String

    abstract val strengthCritical: String
    abstract val strengthWeak: String
    abstract val strengthFair: String
    abstract val strengthStrong: String
    abstract val strengthExcellent: String

    abstract val healthTitle: String
    abstract fun healthScore(score: Int): String
    abstract val healthAllClear: String
    abstract val healthReused: String
    abstract val healthWeak: String
    abstract val healthOld: String
    abstract val healthMissingTotp: String
    abstract val healthExpiringCard: String
    abstract fun healthReusedDetail(others: String): String
    abstract fun healthOldDetail(days: String): String

    abstract val settingsTitle: String
    abstract val settingsSecurity: String
    abstract val settingsAutoLock: String
    abstract val settingsBiometrics: String
    abstract val settingsClipboardClear: String
    abstract val settingsDuressPassword: String
    abstract val settingsChangeMasterPassword: String
    abstract val settingsDevices: String
    abstract val settingsLanguage: String
    abstract val settingsAbout: String
    abstract val settingsWipeVault: String
    abstract val settingsWipeVaultBody: String
    abstract val lockNow: String

    abstract val autoLockImmediate: String
    abstract val autoLockNever: String
    abstract fun autoLockSeconds(seconds: Int): String
    abstract fun autoLockMinutes(minutes: Int): String
    abstract fun autoLockHours(hours: Int): String

    abstract val devicesTitle: String
    abstract val devicesEmpty: String
    abstract val pairDevice: String
    abstract val pairShowQr: String
    abstract val pairScanQr: String
    abstract val pairVerifyTitle: String
    abstract fun pairVerifyBody(code: String): String
    abstract val pairConfirmMatch: String
    abstract val pairMismatch: String
    abstract val revokeDevice: String
    abstract val revokeDeviceBody: String
    abstract fun lastSynced(formatted: String): String
    abstract val neverSynced: String
    abstract val justNow: String
    abstract fun minutesAgo(value: Int): String
    abstract fun hoursAgo(value: Int): String
    abstract fun daysAgo(value: Int): String
    abstract val syncStatusSyncing: String
    abstract val syncStatusNoAddress: String
    abstract val syncStatusUnreachable: String
    abstract val syncStatusRejected: String
    abstract val syncStatusFailed: String
    abstract fun syncStatusDetail(reason: String): String
    abstract val syncRetryNow: String

    abstract val pairTitle: String
    abstract val pairShowThisCode: String
    abstract val pairScanInstruction: String
    abstract val pairWaiting: String
    abstract val pairConnecting: String
    abstract val pairWorking: String
    abstract val pairScanTab: String
    abstract val pairShowTab: String
    abstract val pairPasteInstead: String
    abstract val pairPastePlaceholder: String
    abstract val pairGrantCamera: String
    abstract val pairNoCameraHere: String
    abstract val pairJoinTitle: String
    abstract val pairJoinBody: String
    abstract val pairJoinAction: String
    abstract fun pairDone(label: String): String
    abstract val pairRetry: String

    abstract val settingsChangeMasterPasswordBody: String
    abstract val settingsDuressPasswordBody: String
    abstract val settingsDuressPasswordSet: String
    abstract val settingsDuressPasswordNone: String
    abstract val settingsRemoveDuress: String
    abstract val settingsLanguageBody: String
    abstract val settingsAboutBody: String
    abstract val settingsSyncEnabled: String
    abstract val settingsSyncEnabledBody: String
    abstract val currentPasswordLabel: String
    abstract val newPasswordLabel: String
    abstract val duressPasswordLabel: String
    abstract val duressPasswordWarning: String
    abstract val wrongCurrentPassword: String
    abstract val changed: String
    abstract val settingsAutoLockBody: String

    abstract val folders: String
    abstract val allItems: String
    abstract val favorites: String
    abstract val noFolder: String
    abstract val newFolder: String
    abstract val folderName: String
    abstract val editFolder: String
    abstract val folderIcon: String
    abstract val renameFolder: String
    abstract val deleteFolder: String
    abstract val deleteFolderBody: String
    abstract val addToFavorites: String
    abstract val removeFromFavorites: String

    abstract val settingsImport: String
    abstract val settingsImportBody: String
    abstract val importChooseFile: String
    abstract fun importPreviewBody(count: Int, format: String): String
    abstract fun importSkipped(count: Int): String
    abstract val importAction: String
    abstract fun importDone(count: Int): String
    abstract val importUnrecognised: String

    abstract val settingsBiometricsBody: String
    abstract val biometricPromptSubtitle: String
    abstract val biometricEnrollTitle: String
    abstract val biometricEnrollSubtitle: String
    abstract val biometricNeedsPassword: String
    abstract val biometricInvalidated: String

    abstract val transportTor: String
    abstract val transportLan: String
    abstract val syncing: String
    abstract val syncIdle: String

    abstract val byStade: String
    abstract val settingsAppearance: String
    abstract val settingsAppearanceBody: String
    abstract val settingsSecurityBody: String
    abstract val settingsSyncSection: String
    abstract val settingsSyncSectionBody: String
    abstract val settingsDataSection: String
    abstract val settingsDataSectionBody: String
    abstract val settingsAutofillSection: String
    abstract val settingsAutofillSectionBody: String
    abstract val settingsAboutSectionBody: String
    abstract val settingsLanguageSectionBody: String

    abstract val settingsTheme: String
    abstract val settingsThemeBody: String
    abstract val themeSystem: String
    abstract val themeLight: String
    abstract val themeDark: String
    abstract val settingsDynamicColor: String
    abstract val settingsDynamicColorBody: String
    abstract val settingsDynamicColorUnavailable: String

    abstract val autofillServiceTitle: String
    abstract val autofillServiceBody: String
    abstract val autofillAccessibilityTitle: String
    abstract val autofillAccessibilityBody: String
    abstract val autofillAccessibilityHint: String
    abstract val autofillOverlayTitle: String
    abstract val autofillOverlayBody: String
    abstract val autofillEnabled: String
    abstract val autofillDisabled: String
    abstract val autofillOpenSettings: String
    abstract val autofillUnsupportedHere: String
    abstract val autofillRestrictedTitle: String
    abstract val autofillRestrictedBody: String
    abstract val autofillOpenAppInfo: String
    abstract val autofillTileLocked: String
    abstract fun autofillTileMatches(count: Int): String
    abstract val autofillNoMatches: String
    abstract val autofillPickItem: String

    abstract val aboutLicense: String
    abstract val aboutPrivacy: String
    abstract val aboutPrivacyBody: String
    abstract val aboutNoServers: String
    abstract val aboutNoServersBody: String

    abstract val createMenuTitle: String
    abstract val generatorNoCharacterSets: String
    abstract val vaultUnfiled: String
    abstract val tabAuthenticator: String
    abstract val newAuthenticator: String
    abstract val authenticatorIssuer: String
    abstract val authenticatorSecret: String
    abstract val authenticatorSecretHint: String
    abstract val authenticatorEmptyTitle: String
    abstract val authenticatorEmptyBody: String
    abstract val authenticatorImport: String
    abstract val authenticatorImportBody: String
    abstract val authenticatorScanQr: String
    abstract val authenticatorPaste: String
    abstract val authenticatorPastePlaceholder: String
    abstract fun authenticatorImported(count: Int): String
    abstract val authenticatorImportUnrecognised: String
    abstract fun healthReuseGroup(count: Int): String
    abstract val healthReuseGroupBody: String
    abstract val settingsAutoLockSessionBody: String
}

val LocalStrings = compositionLocalOf<AppStrings> { EnglishStrings }
