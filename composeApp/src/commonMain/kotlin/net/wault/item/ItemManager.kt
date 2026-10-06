package net.wault.item

import net.wault.crypto.CryptoApi
import net.wault.crypto.toHex
import net.wault.db.WaultDb
import net.wault.security.Vault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ItemManager(
    private val db: WaultDb,
    private val crypto: CryptoApi,
    private val vault: Vault,
    private val codec: ItemCodec,
    private val deviceId: () -> String,
    private val clock: () -> Long
) {
    private val _items = MutableStateFlow<List<VaultItem>>(emptyList())
    val items: StateFlow<List<VaultItem>> = _items.asStateFlow()

    private val _undecodable = MutableStateFlow<List<String>>(emptyList())
    val undecodable: StateFlow<List<String>> = _undecodable.asStateFlow()

    fun load() {
        val rows = db.waultDbQueries.selectAllItems().executeAsList()
        val failures = ArrayList<String>()
        val decoded = rows.mapNotNull { row ->
            val record = SealedRecord(row.id, row.revision, row.payload)
            val generation = codec.generationOf(row.payload)
            val body = if (generation == null) {
                null
            } else {
                vault.withDataKeyFor(generation) { dataKey ->
                    runCatching { codec.openItem(dataKey, record) }.getOrNull()
                }
            }
            if (body == null) {
                failures.add(row.id)
                null
            } else {
                VaultItem(
                    id = row.id,
                    content = body.content,
                    folderId = body.folderId,
                    favorite = body.favorite,
                    createdAt = row.createdAt,
                    updatedAt = row.updatedAt,
                    revision = row.revision,
                    updatedBy = row.updatedBy,
                    deleted = row.deleted != 0L
                )
            }
        }
        _items.value = decoded.sortedBy { it.title.lowercase() }
        _undecodable.value = failures
    }

    fun create(content: ItemContent, folderId: String? = null, favorite: Boolean = false): VaultItem {
        val now = clock()
        val item = VaultItem(
            id = newId(),
            content = content,
            folderId = folderId,
            favorite = favorite,
            createdAt = now,
            updatedAt = now,
            revision = 1L,
            updatedBy = deviceId()
        )
        persist(item)
        _items.value = (_items.value + item).sortedBy { it.title.lowercase() }
        return item
    }

    fun update(id: String, transform: (ItemContent) -> ItemContent): VaultItem? {
        val existing = _items.value.firstOrNull { it.id == id } ?: return null
        val updated = existing.copy(
            content = transform(existing.content),
            updatedAt = clock(),
            revision = existing.revision + 1,
            updatedBy = deviceId()
        )
        persist(updated)
        _items.value = _items.value.map { if (it.id == id) updated else it }.sortedBy { it.title.lowercase() }
        return updated
    }

    fun changePassword(id: String, newPassword: String): VaultItem? = update(id) { content ->
        if (content !is ItemContent.Login) return@update content
        val now = clock()
        val history = if (content.password.isEmpty()) {
            content.passwordHistory
        } else {
            (listOf(PasswordHistoryEntry(content.password, now)) + content.passwordHistory).take(MAX_HISTORY)
        }
        content.copy(password = newPassword, passwordHistory = history, passwordChangedAt = now)
    }

    fun delete(id: String) {
        val existing = _items.value.firstOrNull { it.id == id } ?: return
        val now = clock()
        val revision = existing.revision + 1
        val generation = vault.keyGeneration()
        val tombstone = vault.withDataKey { dataKey ->
            codec.sealItem(
                dataKey,
                generation,
                existing.copy(
                    content = ItemContent.Note(title = "", body = ""),
                    revision = revision,
                    updatedAt = now,
                    updatedBy = deviceId(),
                    deleted = true
                )
            )
        }
        db.waultDbQueries.markItemDeleted(tombstone, revision, now, deviceId(), id)
        _items.value = _items.value.filterNot { it.id == id }
    }

    fun moveToFolder(id: String, folderId: String?): VaultItem? = mutate(id) { it.copy(folderId = folderId) }

    fun setFavorite(id: String, favorite: Boolean): VaultItem? = mutate(id) { it.copy(favorite = favorite) }

    fun clearFolder(folderId: String) {
        _items.value.filter { it.folderId == folderId }.forEach { moveToFolder(it.id, null) }
    }

    private fun mutate(id: String, transform: (VaultItem) -> VaultItem): VaultItem? {
        val existing = _items.value.firstOrNull { it.id == id } ?: return null
        val updated = transform(existing).copy(
            updatedAt = clock(),
            revision = existing.revision + 1,
            updatedBy = deviceId()
        )
        persist(updated)
        _items.value = _items.value.map { if (it.id == id) updated else it }
            .sortedBy { it.title.lowercase() }
        return updated
    }

    fun byId(id: String): VaultItem? = _items.value.firstOrNull { it.id == id }

    fun purgeTombstonesOlderThan(cutoffMillis: Long) {
        db.waultDbQueries.purgeTombstonesOlderThan(cutoffMillis)
    }

    private fun persist(item: VaultItem) {
        val generation = vault.keyGeneration()
        val payload = vault.withDataKey { dataKey -> codec.sealItem(dataKey, generation, item) }
        db.waultDbQueries.upsertItem(
            item.id,
            payload,
            item.revision,
            item.createdAt,
            item.updatedAt,
            item.updatedBy,
            if (item.deleted) 1L else 0L
        )
    }

    private fun newId(): String = crypto.randomBytes(16).toHex()

    companion object {
        private const val MAX_HISTORY = 20
    }
}
