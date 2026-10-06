package net.wault.ui.screens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.wault.ui.i18n.LocalStrings
import net.wault.ui.theme.StadeMark
import net.wault.ui.theme.WaultMark
import kotlinx.coroutines.delay

private const val MARK_MS = 520
private const val WORD_DELAY_MS = 230
private const val WORD_MS = 460
private const val BYLINE_DELAY_MS = 760
private const val BYLINE_MS = 540
private const val HOLD_MS = 420L
private const val FADE_OUT_MS = 340

private val EaseOutQuint = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val strings = LocalStrings.current
    val density = LocalDensity.current

    var started by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        started = true
        delay(BYLINE_DELAY_MS.toLong() + BYLINE_MS + HOLD_MS)
        leaving = true
        delay(FADE_OUT_MS.toLong())
        onFinished()
    }

    val markScale by animateFloatAsState(
        targetValue = if (started) 1f else 0.55f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "splashMarkScale"
    )
    val markAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(MARK_MS, easing = LinearEasing),
        label = "splashMarkAlpha"
    )
    val markTilt by animateFloatAsState(
        targetValue = if (started) 0f else -14f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "splashMarkTilt"
    )

    val wordAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(WORD_MS, delayMillis = WORD_DELAY_MS, easing = LinearEasing),
        label = "splashWordAlpha"
    )
    val wordShift by animateFloatAsState(
        targetValue = if (started) 0f else -26f,
        animationSpec = tween(WORD_MS, delayMillis = WORD_DELAY_MS, easing = EaseOutQuint),
        label = "splashWordShift"
    )

    val bylineAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(BYLINE_MS, delayMillis = BYLINE_DELAY_MS, easing = LinearEasing),
        label = "splashBylineAlpha"
    )
    val bylineShift by animateFloatAsState(
        targetValue = if (started) 0f else 64f,
        animationSpec = tween(BYLINE_MS, delayMillis = BYLINE_DELAY_MS, easing = EaseOutQuint),
        label = "splashBylineShift"
    )

    val exitAlpha by animateFloatAsState(
        targetValue = if (leaving) 0f else 1f,
        animationSpec = tween(FADE_OUT_MS, easing = LinearEasing),
        label = "splashExitAlpha"
    )
    val exitScale by animateFloatAsState(
        targetValue = if (leaving) 1.06f else 1f,
        animationSpec = tween(FADE_OUT_MS, easing = EaseOutQuint),
        label = "splashExitScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = exitAlpha }
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                scaleX = exitScale
                scaleY = exitScale
            }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier
                        .size(64.dp)
                        .graphicsLayer {
                            scaleX = markScale
                            scaleY = markScale
                            alpha = markAlpha
                            rotationZ = markTilt
                        },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.onBackground
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = WaultMark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.background,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Spacer(Modifier.width(18.dp))

                Text(
                    text = "Wault",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-1).sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.graphicsLayer {
                        alpha = wordAlpha
                        translationX = with(density) { wordShift.dp.toPx() }
                    }
                )
            }

            Spacer(Modifier.height(18.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.graphicsLayer {
                    alpha = bylineAlpha
                    translationX = with(density) { bylineShift.dp.toPx() }
                }
            ) {
                Icon(
                    imageVector = StadeMark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = strings.byStade,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
