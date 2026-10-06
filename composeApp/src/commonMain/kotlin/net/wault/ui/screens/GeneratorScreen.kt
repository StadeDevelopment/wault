package net.wault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import net.wault.AppContainer
import net.wault.generator.PassphraseRecipe
import net.wault.generator.PasswordRecipe
import net.wault.security.copyToClipboard
import net.wault.strength.StrengthMeter
import net.wault.ui.components.LocalHomeBarClearance
import net.wault.ui.components.StrengthBar
import net.wault.ui.i18n.LocalStrings

private val GENERATOR_ACTION_SIZE = 52.dp

@Composable
fun GeneratorScreen(container: AppContainer) {
    val strings = LocalStrings.current

    var passphraseMode by remember { mutableStateOf(false) }
    var passwordRecipe by remember { mutableStateOf(PasswordRecipe()) }
    var passphraseRecipe by remember { mutableStateOf(PassphraseRecipe()) }
    var nonce by remember { mutableStateOf(0) }

    val recipeUsable = passphraseMode || passwordRecipe.alphabet().isNotEmpty()

    val generated = remember(passphraseMode, passwordRecipe, passphraseRecipe, nonce) {
        when {
            passphraseMode -> container.generator.passphrase(passphraseRecipe)
            passwordRecipe.alphabet().isEmpty() -> ""
            else -> container.generator.password(passwordRecipe)
        }
    }

    val report = remember(generated) { StrengthMeter.evaluate(generated) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = LocalHomeBarClearance.current + 24.dp)
    ) {
        Text(strings.generatorTitle, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !passphraseMode,
                onClick = { passphraseMode = false },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text(strings.generatorPassword) }
            SegmentedButton(
                selected = passphraseMode,
                onClick = { passphraseMode = true },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text(strings.generatorPassphrase) }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = if (recipeUsable) generated else strings.generatorNoCharacterSets,
            style = if (recipeUsable) {
                MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = if (recipeUsable) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(18.dp)
        )

        Spacer(Modifier.height(12.dp))
        StrengthBar(level = report.level, entropyBits = report.entropyBits)

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { copyToClipboard(generated, sensitive = true) },
                enabled = recipeUsable,
                modifier = Modifier.weight(1f).height(GENERATOR_ACTION_SIZE)
            ) {
                Text(strings.copy)
            }
            FilledTonalIconButton(
                onClick = { nonce++ },
                enabled = recipeUsable,
                modifier = Modifier.size(GENERATOR_ACTION_SIZE)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = strings.regenerate)
            }
        }

        Spacer(Modifier.height(24.dp))

        if (passphraseMode) {
            LabelledSlider(
                label = "${strings.generatorWords}: ${passphraseRecipe.words}",
                value = passphraseRecipe.words.toFloat(),
                range = 3f..12f,
                steps = 8,
                onValueChange = { passphraseRecipe = passphraseRecipe.copy(words = it.toInt()) }
            )
            ToggleRow(strings.generatorCapitalize, passphraseRecipe.capitalize) {
                passphraseRecipe = passphraseRecipe.copy(capitalize = it)
            }
            ToggleRow(strings.generatorIncludeNumber, passphraseRecipe.includeNumber) {
                passphraseRecipe = passphraseRecipe.copy(includeNumber = it)
            }
        } else {
            LabelledSlider(
                label = "${strings.generatorLength}: ${passwordRecipe.length}",
                value = passwordRecipe.length.toFloat(),
                range = 8f..64f,
                steps = 55,
                onValueChange = { passwordRecipe = passwordRecipe.copy(length = it.toInt()) }
            )
            ToggleRow(strings.generatorLowercase, passwordRecipe.lowercase) {
                passwordRecipe = passwordRecipe.copy(lowercase = it)
            }
            ToggleRow(strings.generatorUppercase, passwordRecipe.uppercase) {
                passwordRecipe = passwordRecipe.copy(uppercase = it)
            }
            ToggleRow(strings.generatorDigits, passwordRecipe.digits) {
                passwordRecipe = passwordRecipe.copy(digits = it)
            }
            ToggleRow(strings.generatorSymbols, passwordRecipe.symbols) {
                passwordRecipe = passwordRecipe.copy(symbols = it)
            }
            ToggleRow(strings.generatorExcludeAmbiguous, passwordRecipe.excludeAmbiguous) {
                passwordRecipe = passwordRecipe.copy(excludeAmbiguous = it)
            }
        }
    }
}

@Composable
private fun LabelledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Slider(value = value, onValueChange = onValueChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
