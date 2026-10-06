package net.wault.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

fun Modifier.secondaryClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = rememberInteraction(),
        indication = null,
        enabled = enabled,
        onClick = onClick
    )
}

@Composable
private fun rememberInteraction(): MutableInteractionSource =
    androidx.compose.runtime.remember { MutableInteractionSource() }
