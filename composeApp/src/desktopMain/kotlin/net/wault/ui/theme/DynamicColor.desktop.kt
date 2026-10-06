package net.wault.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

actual val isDynamicColorSupported: Boolean = false

@Composable
actual fun platformDynamicColorScheme(dark: Boolean): ColorScheme? = null
