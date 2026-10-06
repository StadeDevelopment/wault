package net.wault.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

expect val isCameraScanSupported: Boolean

@Composable
expect fun QrScanner(
    onScanned: (String) -> Unit,
    onPermissionFlow: (Boolean) -> Unit,
    modifier: Modifier
)
