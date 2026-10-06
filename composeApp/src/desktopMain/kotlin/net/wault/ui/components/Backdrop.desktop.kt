package net.wault.ui.components

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

actual class BackdropState

actual val isBackdropBlurSupported: Boolean = false

@Composable
actual fun rememberBackdropState(): BackdropState = remember { BackdropState() }

actual fun Modifier.backdropSource(state: BackdropState, enabled: Boolean): Modifier = this

actual fun Modifier.backdropBlur(
    state: BackdropState,
    radius: Dp,
    shape: Shape,
    tint: Color,
    opaqueFallback: Color
): Modifier = this.background(opaqueFallback, shape)
