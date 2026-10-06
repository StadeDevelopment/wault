package net.wault.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

expect val isDynamicColorSupported: Boolean

@Composable
expect fun platformDynamicColorScheme(dark: Boolean): ColorScheme?
