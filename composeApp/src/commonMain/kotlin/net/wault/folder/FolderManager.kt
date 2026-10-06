package net.wault.folder

import net.wault.crypto.CryptoApi
import net.wault.crypto.toHex
import net.wault.db.WaultDb
import net.wault.item.Folder
import net.wault.item.ItemCodec
import net.wault.item.SealedRecord
import net.wault.security.Vault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FolderManager(
    private val db: WaultDb,
    private val crypto: CryptoApi,
    private val vault: Vault,
    private val codec: ItemCodec,
    private val deviceId: () -> String,
    private val clock: () -> Long
) {
    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

    fun load() {
        val decoded = db.waultDbQueries.selectAllFolders().executeAsList().mapNotNull { row ->
            val generation = codec.generationOf(row.payload) ?: return@mapNotNull null
            val raw = vault.withDataKeyFor(generation) { dataKey ->
                runCatching {
                    codec.openFolderName(dataKey, SealedRecord(row.id, row.revision, row.payload))
                }.getOrNull()
            } ?: return@mapNotNull null
            Folder(
                id = row.id,
                name = FolderPayload.decodeName(raw),
                icon = FolderPayload.decodeIcon(raw),
                createdAt = row.createdAt,
                updatedAt = row.updatedAt,
                revision = row.revision,
                updatedBy = row.updatedBy,
                deleted = row.deleted != 0L
            )
        }
        _folders.value = decoded.sortedBy { it.name.lowercase() }
    }

    fun create(name: String, icon: String = ""): Folder {
        val now = clock()
        val folder = Folder(
            id = crypto.randomBytes(16).toHex(),
            name = name.trim(),
            icon = icon.trim(),
            createdAt = now,
            updatedAt = now,
            revision = 1L,
            updatedBy = deviceId()
        )
        persist(folder)
        _folders.value = (_folders.value + folder).sortedBy { it.name.lowercase() }
        return folder
    }

    fun rename(id: String, name: String): Folder? = update(id, name, null)

    fun update(id: String, name: String, icon: String?): Folder? {
        val existing = _folders.value.firstOrNull { it.id == id } ?: return null
        val updated = existing.copy(
            name = name.trim(),
            icon = (icon ?: existing.icon).trim(),
            updatedAt = clock(),
            revision = existing.revision + 1,
            updatedBy = deviceId()
        )
        persist(updated)
        _folders.value = _folders.value.map { if (it.id == id) updated else it }
            .sortedBy { it.name.lowercase() }
        return updated
    }

    fun delete(id: String) {
        val existing = _folders.value.firstOrNull { it.id == id } ?: return
        val now = clock()
        val revision = existing.revision + 1
        val generation = vault.keyGeneration()
        val payload = vault.withDataKey { dataKey ->
            codec.sealFolderName(dataKey, generation, existing.id, revision, "")
        }
        db.waultDbQueries.upsertFolder(
            existing.id, payload, revision, existing.createdAt, now, deviceId(), 1L
        )
        _folders.value = _folders.value.filterNot { it.id == id }
    }

    fun byId(id: String?): Folder? = id?.let { wanted -> _folders.value.firstOrNull { it.id == wanted } }

    private fun persist(folder: Folder) {
        val generation = vault.keyGeneration()
        val payload = vault.withDataKey { dataKey ->
            codec.sealFolderName(
                dataKey,
                generation,
                folder.id,
                folder.revision,
                FolderPayload.encode(folder.name, folder.icon)
            )
        }
        db.waultDbQueries.upsertFolder(
            folder.id,
            payload,
            folder.revision,
            folder.createdAt,
            folder.updatedAt,
            folder.updatedBy,
            if (folder.deleted) 1L else 0L
        )
    }
}
