package net.wault.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.wault.item.ItemSearch
import net.wault.item.MatchableUri
import net.wault.item.UriMatch
import net.wault.ui.i18n.AppStrings
import net.wault.ui.i18n.LocalStrings

fun uriMatchLabel(strings: AppStrings, match: UriMatch): String = when (match) {
    UriMatch.Domain -> strings.uriMatchDomain
    UriMatch.Host -> strings.uriMatchHost
    UriMatch.StartsWith -> strings.uriMatchStartsWith
    UriMatch.Exact -> strings.uriMatchExact
    UriMatch.Regex -> strings.uriMatchRegex
    UriMatch.Never -> strings.uriMatchNever
}

@Composable
fun UriRuleEditor(
    uris: List<MatchableUri>,
    onChange: (List<MatchableUri>) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    Column(modifier = modifier.fillMaxWidth()) {
        uris.forEachIndexed { index, entry ->
            UriRuleRow(
                entry = entry,
                onEntryChange = { updated ->
                    onChange(uris.toMutableList().also { it[index] = updated })
                },
                onRemove = {
                    onChange(uris.toMutableList().also { it.removeAt(index) })
                }
            )
            Spacer(Modifier.height(8.dp))
        }

        TextButton(onClick = { onChange(uris + MatchableUri("", UriMatch.Domain)) }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.height(0.dp))
            Text(strings.uriAdd)
        }
    }
}

@Composable
private fun UriRuleRow(
    entry: MatchableUri,
    onEntryChange: (MatchableUri) -> Unit,
    onRemove: () -> Unit
) {
    val strings = LocalStrings.current
    var menuOpen by remember { mutableStateOf(false) }

    val badPattern = entry.match == UriMatch.Regex &&
        entry.uri.isNotBlank() &&
        !ItemSearch.isValidRegex(entry.uri)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedTextField(
                value = entry.uri,
                onValueChange = { onEntryChange(entry.copy(uri = it)) },
                label = { Text(strings.fieldWebsite) },
                singleLine = true,
                isError = badPattern,
                supportingText = if (badPattern) {
                    { Text(strings.uriMatchRegexInvalid, color = MaterialTheme.colorScheme.error) }
                } else {
                    null
                },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = strings.uriRemove)
            }
        }

        Box {
            TextButton(onClick = { menuOpen = true }) {
                Text(uriMatchLabel(strings, entry.match))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                UriMatch.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(uriMatchLabel(strings, option)) },
                        onClick = {
                            onEntryChange(entry.copy(match = option))
                            menuOpen = false
                        }
                    )
                }
            }
        }
    }
}
