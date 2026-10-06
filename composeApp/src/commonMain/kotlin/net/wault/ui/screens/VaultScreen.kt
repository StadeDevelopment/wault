package net.wault.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.wault.AppContainer
import net.wault.item.ItemContent
import net.wault.item.ItemSearch
import net.wault.item.ItemType
import net.wault.item.VaultItem
import net.wault.ui.SystemBackHandler
import net.wault.ui.components.CircleBackButton
import net.wault.folder.FolderIcons
import net.wault.ui.components.CircleIconButton
import net.wault.ui.components.FolderDialog
import net.wault.ui.components.CreateAction
import net.wault.ui.components.CreateFabMenu
import net.wault.ui.components.GroupPosition
import net.wault.ui.components.LocalGroupPosition
import net.wault.ui.components.LocalHomeBarClearance
import net.wault.ui.components.SettingsRow
import net.wault.ui.components.VaultFilter
import net.wault.ui.components.groupPositions
import net.wault.ui.i18n.LocalStrings
import net.wault.ui.theme.WaultColors
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun VaultScreen(
    container: AppContainer,
    onOpenItem: (String) -> Unit,
    onCreate: (ItemType) -> Unit,
    onLock: () -> Unit
) {
    val strings = LocalStrings.current
    val items by container.items.items.collectAsState()
    val folders by container.folders.folders.collectAsState()

    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf<VaultFilter>(VaultFilter.All) }
    var newFolderOpen by remember { mutableStateOf(false) }
    var editingFolder by remember { mutableStateOf<net.wault.item.Folder?>(null) }

    LaunchedEffect(folders) {
        val current = filter
        if (current is VaultFilter.InFolder && folders.none { it.id == current.folderId }) {
            filter = VaultFilter.All
        }
    }

    val searching = searchOpen && query.isNotBlank()
    val browsing = filter == VaultFilter.All && !searching

    val unfiled = remember(items) { items.filter { it.folderId == null } }
    val favorites = remember(items) { items.filter { it.favorite } }

    val scoped = remember(items, filter) {
        when (val current = filter) {
            VaultFilter.All -> items
            VaultFilter.Favorites -> items.filter { it.favorite }
            is VaultFilter.InFolder -> items.filter { it.folderId == current.folderId }
        }
    }
    val searchResults = remember(items, query) { ItemSearch.filter(items, query) }

    val openFolder = (filter as? VaultFilter.InFolder)?.let { inFolder ->
        folders.firstOrNull { it.id == inFolder.folderId }
    }
    val openFolderName = openFolder?.name
    val title = when {
        searching -> strings.search
        filter == VaultFilter.Favorites -> strings.favorites
        openFolderName != null -> openFolderName
        else -> strings.tabVault
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!browsing) {
                    CircleBackButton(onClick = {
                        filter = VaultFilter.All
                        searchOpen = false
                        query = ""
                    })
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )

                openFolder?.let { folder ->
                    CircleIconButton(
                        icon = Icons.Default.Edit,
                        contentDescription = strings.editFolder,
                        onClick = { editingFolder = folder }
                    )
                }

                CircleIconButton(
                    icon = Icons.Default.Search,
                    contentDescription = strings.search,
                    onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) query = ""
                    }
                )
                CircleIconButton(
                    icon = Icons.Default.Lock,
                    contentDescription = strings.lockNow,
                    onClick = onLock
                )
            }

            AnimatedVisibility(visible = searchOpen) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(strings.search) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = LocalHomeBarClearance.current + 24.dp)
            ) {
                if (searching) {
                    itemRows(searchResults, strings.search, onOpenItem)
                    if (searchResults.isEmpty()) emptyRow(strings.vaultEmptyTitle, strings.vaultEmptyBody)
                    return@LazyColumn
                }

                if (!browsing) {
                    itemRows(scoped, null, onOpenItem)
                    if (scoped.isEmpty()) emptyRow(strings.vaultEmptyTitle, strings.vaultEmptyBody)
                    return@LazyColumn
                }

                val folderRows = buildList {
                    if (favorites.isNotEmpty()) add(null)
                    addAll(folders.map { it })
                }

                if (folderRows.isNotEmpty()) {
                    item(key = "folders-header") { SectionLabel(strings.folders) }

                    val positions = groupPositions(folderRows.size)
                    folderRows.forEachIndexed { index, folder ->
                        item(key = "folder-${folder?.id ?: "favorites"}") {
                            val count = if (folder == null) {
                                favorites.size
                            } else {
                                items.count { it.folderId == folder.id }
                            }
                            CompositionLocalProvider(LocalGroupPosition provides positions[index]) {
                                SettingsRow(
                                    title = folder?.name ?: strings.favorites,
                                    subtitle = strings.itemCount(count),
                                    icon = if (folder == null) {
                                        Icons.Default.Star
                                    } else {
                                        FolderIcons.imageFor(folder.icon)
                                    },
                                    iconTint = if (folder == null) WaultColors.favorite else null,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    onClick = {
                                        filter = if (folder == null) {
                                            VaultFilter.Favorites
                                        } else {
                                            VaultFilter.InFolder(folder.id)
                                        }
                                    }
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                        }
                    }

                    item(key = "folders-gap") { Spacer(Modifier.height(16.dp)) }
                }

                if (unfiled.isNotEmpty()) {
                    itemRows(unfiled, strings.vaultUnfiled, onOpenItem)
                } else if (folderRows.isEmpty()) {
                    emptyRow(strings.vaultEmptyTitle, strings.vaultEmptyBody)
                }
            }
        }

        CreateFabMenu(
            expanded = menuOpen,
            onExpandedChange = { menuOpen = it },
            fabLabel = strings.addItem,
            bottomPadding = LocalHomeBarClearance.current + 16.dp,
            actions = listOf(
                CreateAction(strings.newLogin, Icons.Default.Key) { onCreate(ItemType.Login) },
                CreateAction(strings.newCard, Icons.Default.CreditCard) { onCreate(ItemType.Card) },
                CreateAction(strings.newNote, Icons.AutoMirrored.Filled.Notes) { onCreate(ItemType.Note) },
                CreateAction(strings.newIdentity, Icons.Default.Badge) { onCreate(ItemType.Identity) },
                CreateAction(strings.newSshKey, Icons.Default.Terminal) { onCreate(ItemType.SshKey) },
                CreateAction(strings.newFolder, Icons.Default.CreateNewFolder) { newFolderOpen = true }
            )
        )
    }

    SystemBackHandler(enabled = menuOpen) { menuOpen = false }
    SystemBackHandler(enabled = !menuOpen && !browsing) {
        filter = VaultFilter.All
        searchOpen = false
        query = ""
    }

    if (newFolderOpen) {
        FolderDialog(
            title = strings.newFolder,
            initialName = "",
            initialIcon = FolderIcons.DEFAULT_ID,
            onDismiss = { newFolderOpen = false },
            onConfirm = { name, icon ->
                val created = container.folders.create(name, icon)
                filter = VaultFilter.InFolder(created.id)
                newFolderOpen = false
            }
        )
    }

    editingFolder?.let { folder ->
        FolderDialog(
            title = strings.editFolder,
            initialName = folder.name,
            initialIcon = folder.icon,
            onDismiss = { editingFolder = null },
            onConfirm = { name, icon ->
                container.folders.update(folder.id, name, icon)
                editingFolder = null
            }
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemRows(
    entries: List<VaultItem>,
    header: String?,
    onOpenItem: (String) -> Unit
) {
    if (entries.isEmpty()) return
    if (header != null) {
        item(key = "header-$header") { SectionLabel(header) }
    }
    val positions = groupPositions(entries.size)
    entries.forEachIndexed { index, entry ->
        item(key = "item-${entry.id}") {
            CompositionLocalProvider(LocalGroupPosition provides positions[index]) {
                SettingsRow(
                    title = entry.title.ifBlank { entry.id.take(8) },
                    subtitle = subtitleOf(entry),
                    icon = iconOf(entry),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onClick = { onOpenItem(entry.id) }
                )
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.emptyRow(title: String, body: String) {
    item(key = "empty") {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun subtitleOf(item: VaultItem): String? = when (val content = item.content) {
    is ItemContent.Login -> content.username.ifBlank { null }
    is ItemContent.Card -> content.brand.ifBlank { null }
    is ItemContent.Identity -> content.email.ifBlank { null }
    is ItemContent.SshKey -> content.fingerprint.ifBlank { null }
    is ItemContent.Note -> null
    is ItemContent.Authenticator -> content.secret.issuer.ifBlank { null }
}

private fun iconOf(item: VaultItem) = when (item.content) {
    is ItemContent.Login -> Icons.Default.Key
    is ItemContent.Card -> Icons.Default.CreditCard
    is ItemContent.Identity -> Icons.Default.Badge
    is ItemContent.SshKey -> Icons.Default.Terminal
    is ItemContent.Note -> Icons.AutoMirrored.Filled.Notes
    is ItemContent.Authenticator -> Icons.Default.Shield
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, top = 8.dp, bottom = 8.dp)
    )
}
