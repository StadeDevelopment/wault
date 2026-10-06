package net.wault.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.wault.APP_VERSION
import net.wault.ui.SettingsSection
import net.wault.ui.components.LocalHomeBarClearance
import net.wault.ui.components.ScreenHeader
import net.wault.ui.components.SettingsGroup
import net.wault.ui.components.SettingsRow
import net.wault.ui.i18n.LocalStrings
import net.wault.ui.theme.StadeMark
import net.wault.ui.theme.WaultMark
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onOpenSection: (SettingsSection) -> Unit,
    onLock: () -> Unit
) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = LocalHomeBarClearance.current + 24.dp)
    ) {
        ScreenHeader(title = strings.settingsTitle)

        IdentityCard()

        Spacer(Modifier.height(24.dp))

        SettingsGroup {
            row {
                SettingsRow(
                    title = strings.settingsAppearance,
                    subtitle = strings.settingsAppearanceBody,
                    icon = Icons.Default.Palette,
                    trailing = { ChevronEnd() },
                    onClick = { onOpenSection(SettingsSection.Appearance) }
                )
            }
            row {
                SettingsRow(
                    title = strings.settingsSecurity,
                    subtitle = strings.settingsSecurityBody,
                    icon = Icons.Default.Security,
                    trailing = { ChevronEnd() },
                    onClick = { onOpenSection(SettingsSection.Security) }
                )
            }
            row {
                SettingsRow(
                    title = strings.settingsAutofillSection,
                    subtitle = strings.settingsAutofillSectionBody,
                    icon = Icons.Default.Keyboard,
                    trailing = { ChevronEnd() },
                    onClick = { onOpenSection(SettingsSection.Autofill) }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        SettingsGroup {
            row {
                SettingsRow(
                    title = strings.settingsSyncSection,
                    subtitle = strings.settingsSyncSectionBody,
                    icon = Icons.Default.Devices,
                    trailing = { ChevronEnd() },
                    onClick = { onOpenSection(SettingsSection.Sync) }
                )
            }
            row {
                SettingsRow(
                    title = strings.settingsDataSection,
                    subtitle = strings.settingsDataSectionBody,
                    icon = Icons.Default.Storage,
                    trailing = { ChevronEnd() },
                    onClick = { onOpenSection(SettingsSection.Data) }
                )
            }
            row {
                SettingsRow(
                    title = strings.settingsLanguage,
                    subtitle = strings.settingsLanguageSectionBody,
                    icon = Icons.Default.Language,
                    trailing = { ChevronEnd() },
                    onClick = { onOpenSection(SettingsSection.Language) }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        SettingsGroup {
            row {
                SettingsRow(
                    title = strings.settingsAbout,
                    subtitle = strings.settingsAboutSectionBody,
                    icon = Icons.Default.Info,
                    trailing = { ChevronEnd() },
                    onClick = { onOpenSection(SettingsSection.About) }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        SettingsGroup {
            row {
                SettingsRow(
                    title = strings.lockNow,
                    icon = Icons.Default.Lock,
                    onClick = onLock
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun IdentityCard() {
    val strings = LocalStrings.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurface
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = WaultMark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Wault", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "v$APP_VERSION",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = StadeMark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        strings.byStade,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun ChevronEnd() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(22.dp)
    )
}
