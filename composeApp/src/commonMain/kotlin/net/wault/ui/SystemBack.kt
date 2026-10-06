package net.wault.ui

import androidx.compose.runtime.Composable

@Composable
expect fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit)
