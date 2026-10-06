package net.wault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.wault.security.copyToClipboard
import net.wault.ui.i18n.LocalStrings

@Composable
fun RecoveryKeyScreen(
    recoveryKey: String,
    onAcknowledged: () -> Unit
) {
    val strings = LocalStrings.current
    var acknowledged by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text(strings.recoveryKeyTitle, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            strings.recoveryKeyBody,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = recoveryKey,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(20.dp)
        )

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = { copyToClipboard(recoveryKey, sensitive = true) }) {
            Text(strings.copy)
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { acknowledged = true; onAcknowledged() },
            enabled = !acknowledged,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(strings.recoveryKeySaved)
        }
    }
}
