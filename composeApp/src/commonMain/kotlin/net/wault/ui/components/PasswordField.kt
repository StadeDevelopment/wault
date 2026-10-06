package net.wault.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import net.wault.ui.i18n.LocalStrings

private const val TOGGLE_ANIM_MS = 180

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    conceal: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    shape: androidx.compose.ui.graphics.Shape? = null
) {
    val strings = LocalStrings.current
    var revealed by remember { mutableStateOf(false) }
    val hidden = conceal && !revealed

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = isError,
        shape = shape ?: androidx.compose.material3.OutlinedTextFieldDefaults.shape,
        supportingText = supportingText,
        visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (hidden) KeyboardType.Password else KeyboardType.Text,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onDone = { onImeAction?.invoke() },
            onGo = { onImeAction?.invoke() },
            onNext = { onImeAction?.invoke() }
        ),
        trailingIcon = if (!conceal) null else {
            {
                IconButton(onClick = { revealed = !revealed }, enabled = enabled) {
                    AnimatedContent(
                        targetState = revealed,
                        transitionSpec = {
                            (fadeIn(tween(TOGGLE_ANIM_MS)) + scaleIn(tween(TOGGLE_ANIM_MS), initialScale = 0.7f))
                                .togetherWith(
                                    fadeOut(tween(TOGGLE_ANIM_MS)) +
                                        scaleOut(tween(TOGGLE_ANIM_MS), targetScale = 0.7f)
                                )
                        },
                        label = "passwordReveal"
                    ) { shown ->
                        Icon(
                            imageVector = if (shown) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (shown) strings.hidePassword else strings.revealPassword,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        },
        modifier = modifier
    )
}
