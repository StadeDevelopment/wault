package net.wault.ui.components

import androidx.compose.runtime.Composable

expect val isGalleryScanSupported: Boolean

@Composable
expect fun rememberQrGalleryPicker(
    onResult: (String?) -> Unit,
    onExternalFlow: (Boolean) -> Unit
): () -> Unit
