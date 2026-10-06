package net.wault.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

expect class BackdropState

expect val isBackdropBlurSupported: Boolean

@Composable
expect fun rememberBackdropState(): BackdropState

expect fun Modifier.backdropSource(state: BackdropState, enabled: Boolean): Modifier

expect fun Modifier.backdropBlur(
    state: BackdropState,
    radius: Dp,
    shape: Shape,
    tint: Color,
    opaqueFallback: Color
): Modifier
