package net.wault.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val ITEM_ENTER_MS = 300
private const val ITEM_EXIT_MS = 170
private const val ITEM_ENTER_STAGGER_MS = 45
private const val ITEM_EXIT_STAGGER_MS = 28
private const val SCRIM_MS = 220

private val EnterOvershoot = CubicBezierEasing(0.2f, 1.4f, 0.4f, 1f)
private val ExitEasing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

private val ITEM_RISE = 28.dp
private val ITEM_SPACING = 10.dp

data class CreateAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun CreateFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    actions: List<CreateAction>,
    fabLabel: String,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 16.dp,
    endPadding: Dp = 20.dp
) {
    val density = LocalDensity.current
    val transition = updateTransition(targetState = expanded, label = "createMenu")
    val keepComposed = expanded || transition.currentState

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(SCRIM_MS)),
            exit = fadeOut(tween(SCRIM_MS)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onExpandedChange(false) }
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = endPadding, bottom = bottomPadding),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(ITEM_SPACING)
        ) {
            if (keepComposed) {
                actions.forEachIndexed { index, action ->
                    val fromBottom = actions.lastIndex - index

                    val progress by transition.animateFloat(
                        transitionSpec = {
                            if (targetState) {
                                tween(
                                    durationMillis = ITEM_ENTER_MS,
                                    delayMillis = fromBottom * ITEM_ENTER_STAGGER_MS,
                                    easing = EnterOvershoot
                                )
                            } else {
                                tween(
                                    durationMillis = ITEM_EXIT_MS,
                                    delayMillis = index * ITEM_EXIT_STAGGER_MS,
                                    easing = ExitEasing
                                )
                            }
                        },
                        label = "createMenuItem$index"
                    ) { open -> if (open) 1f else 0f }

                    CreateMenuItem(
                        action = action,
                        progress = progress,
                        risePx = with(density) { ITEM_RISE.toPx() },
                        onInvoke = {
                            onExpandedChange(false)
                            action.onClick()
                        }
                    )
                }

                Spacer(Modifier.height(6.dp))
            }

            CreateButton(
                label = fabLabel,
                expanded = expanded,
                onClick = { onExpandedChange(!expanded) }
            )
        }
    }
}

@Composable
private fun CreateMenuItem(
    action: CreateAction,
    progress: Float,
    risePx: Float,
    onInvoke: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "createMenuItemPress"
    )

    Surface(
        modifier = Modifier
            .graphicsLayer {
                val eased = progress.coerceIn(0f, 1f)
                alpha = eased
                translationY = (1f - progress) * risePx
                scaleX = (0.82f + 0.18f * progress) * press
                scaleY = (0.82f + 0.18f * progress) * press
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.85f, 0.5f)
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onInvoke
            ),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = action.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
