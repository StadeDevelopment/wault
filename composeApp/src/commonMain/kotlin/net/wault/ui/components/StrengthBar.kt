package net.wault.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import net.wault.strength.StrengthLevel
import net.wault.ui.i18n.LocalStrings
import net.wault.ui.theme.WaultColors

@Composable
fun StrengthBar(
    level: StrengthLevel,
    entropyBits: Double,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    val strings = LocalStrings.current
    val target = when (level) {
        StrengthLevel.Critical -> 0.15f
        StrengthLevel.Weak -> 0.35f
        StrengthLevel.Fair -> 0.6f
        StrengthLevel.Strong -> 0.82f
        StrengthLevel.Excellent -> 1f
    }
    val fraction by animateFloatAsState(target, label = "strength-fraction")
    val color by animateColorAsState(WaultColors.forStrength(level), label = "strength-color")

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }

        if (showLabel) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = labelFor(level),
                    style = MaterialTheme.typography.labelMedium,
                    color = color
                )
                Text(
                    text = strings.entropyBits(entropyBits.toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun labelFor(level: StrengthLevel): String {
    val strings = LocalStrings.current
    return when (level) {
        StrengthLevel.Critical -> strings.strengthCritical
        StrengthLevel.Weak -> strings.strengthWeak
        StrengthLevel.Fair -> strings.strengthFair
        StrengthLevel.Strong -> strings.strengthStrong
        StrengthLevel.Excellent -> strings.strengthExcellent
    }
}
