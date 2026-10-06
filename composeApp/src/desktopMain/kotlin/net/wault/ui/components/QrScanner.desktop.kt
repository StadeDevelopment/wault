package net.wault.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.wault.ui.i18n.LocalStrings

actual val isCameraScanSupported: Boolean = false

@Composable
actual fun QrScanner(
    onScanned: (String) -> Unit,
    onPermissionFlow: (Boolean) -> Unit,
    modifier: Modifier
) {
    val strings = LocalStrings.current
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = strings.pairNoCameraHere,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
