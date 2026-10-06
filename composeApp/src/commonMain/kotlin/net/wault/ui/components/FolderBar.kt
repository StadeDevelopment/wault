package net.wault.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.wault.item.Folder
import net.wault.ui.i18n.LocalStrings

sealed interface VaultFilter {
    data object All : VaultFilter
    data object Favorites : VaultFilter
    data class InFolder(val folderId: String) : VaultFilter
}

@Composable
fun FolderBar(
    folders: List<Folder>,
    selected: VaultFilter,
    onSelect: (VaultFilter) -> Unit,
    onCreateFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "all") {
            FilterChip(
                selected = selected == VaultFilter.All,
                onClick = { onSelect(VaultFilter.All) },
                label = { Text(strings.allItems) }
            )
        }
        item(key = "favorites") {
            FilterChip(
                selected = selected == VaultFilter.Favorites,
                onClick = { onSelect(VaultFilter.Favorites) },
                label = { Text(strings.favorites) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
        items(folders, key = { it.id }) { folder ->
            FilterChip(
                selected = selected == VaultFilter.InFolder(folder.id),
                onClick = { onSelect(VaultFilter.InFolder(folder.id)) },
                label = { Text(folder.name) }
            )
        }
        item(key = "new") {
            FilterChip(
                selected = false,
                onClick = onCreateFolder,
                label = { Text(strings.newFolder) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
    }
}
