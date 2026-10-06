package net.wault.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize

actual class BackdropState internal constructor(internal val layer: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
}

actual val isBackdropBlurSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
actual fun rememberBackdropState(): BackdropState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { BackdropState(layer) }
}

actual fun Modifier.backdropSource(state: BackdropState, enabled: Boolean): Modifier {
    if (!isBackdropBlurSupported || !enabled) return this
    return this
        .onGloballyPositioned { state.origin = it.positionInRoot() }
        .drawWithContent {
            state.layer.record { this@drawWithContent.drawContent() }
            drawLayer(state.layer)
        }
}

actual fun Modifier.backdropBlur(
    state: BackdropState,
    radius: Dp,
    shape: Shape,
    tint: Color,
    opaqueFallback: Color
): Modifier {
    if (!isBackdropBlurSupported) return this.background(opaqueFallback, shape)

    return this.composed {
        val blurLayer = rememberGraphicsLayer()
        var position by remember { mutableStateOf(Offset.Zero) }

        Modifier
            .onGloballyPositioned { position = it.positionInRoot() }
            .clip(shape)
            .drawBehind {
                val blur = radius.toPx()
                val width = size.width.toInt()
                val height = size.height.toInt()
                if (width <= 0 || height <= 0) return@drawBehind

                blurLayer.renderEffect = BlurEffect(blur, blur, TileMode.Decal)
                blurLayer.record(size = IntSize(width, height)) {
                    translate(
                        left = state.origin.x - position.x,
                        top = state.origin.y - position.y
                    ) {
                        drawLayer(state.layer)
                    }
                }
                drawLayer(blurLayer)
                drawRect(tint)
            }
    }
}
