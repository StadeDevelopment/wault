package net.wault.security

import net.wault.db.WaultDb
import net.wault.item.ItemCodec
import net.wault.item.SealedRecord

private val REWRAPPED_KV_KEYS = listOf("device.signingKeys", "device.agreementKeys")

data class RotationReport(
    val previousGeneration: Int,
    val newGeneration: Int,
    val itemsRewrapped: Int,
    val foldersRewrapped: Int,
    val devicesRewrapped: Int,
    val sessionsRewrapped: Int,
    val failures: List<String>
) {
    val succeeded: Boolean get() = failures.isEmpty()
}

class VaultRotation(
    private val db: WaultDb,
    private val vault: Vault,
    private val codec: ItemCodec,
    private val secretBox: SecretBox
) {
    fun rotate(): RotationReport? {
        val handle = vault.beginRotation() ?: return null
        return migrate(handle.previousGeneration, handle.newGeneration)
    }

    fun adopt(newDataKey: ByteArray, generation: Int): RotationReport? {
        val handle = vault.adoptRotatedKey(newDataKey, generation) ?: return null
        return migrate(handle.previousGeneration, handle.newGeneration)
    }

    fun resumeIfPending(): RotationReport? {
        if (!vault.isRotationPending()) return null
        val current = vault.keyGeneration()
        return migrate(current - 1, current)
    }

    private fun migrate(previousGeneration: Int, newGeneration: Int): RotationReport {
        val failures = mutableListOf<String>()
        var items = 0
        var folders = 0
        var devices = 0
        var sessions = 0

        db.waultDbQueries.transaction {
            db.waultDbQueries.selectAllItemsIncludingDeleted().executeAsList().forEach { row ->
                val generation = codec.generationOf(row.payload)
                if (generation == newGeneration) return@forEach
                if (generation == null) {
                    failures.add("item:${row.id}")
                    return@forEach
                }
                val body = vault.withDataKeyFor(generation) { key ->
                    runCatching {
                        codec.openItem(key, SealedRecord(row.id, row.revision, row.payload))
                    }.getOrNull()
                }
                if (body == null) {
                    failures.add("item:${row.id}")
                    return@forEach
                }
                val resealed = vault.withDataKey { key ->
                    codec.sealItem(
                        key,
                        newGeneration,
                        net.wault.item.VaultItem(
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
                    )
                }
                db.waultDbQueries.upsertItem(
                    row.id, resealed, row.revision, row.createdAt,
                    row.updatedAt, row.updatedBy, row.deleted
                )
                items += 1
            }

            db.waultDbQueries.selectAllFolders().executeAsList().forEach { row ->
                val generation = codec.generationOf(row.payload)
                if (generation == newGeneration) return@forEach
                if (generation == null) {
                    failures.add("folder:${row.id}")
                    return@forEach
                }
                val name = vault.withDataKeyFor(generation) { key ->
                    runCatching {
                        codec.openFolderName(key, SealedRecord(row.id, row.revision, row.payload))
                    }.getOrNull()
                }
                if (name == null) {
                    failures.add("folder:${row.id}")
                    return@forEach
                }
                val resealed = vault.withDataKey { key ->
                    codec.sealFolderName(key, newGeneration, row.id, row.revision, name)
                }
                db.waultDbQueries.upsertFolder(
                    row.id, resealed, row.revision, row.createdAt,
                    row.updatedAt, row.updatedBy, row.deleted
                )
                folders += 1
            }

            REWRAPPED_KV_KEYS.forEach { key ->
                val sealed = db.waultDbQueries.getKv(key).executeAsOneOrNull() ?: return@forEach
                val rewrapped = reseal(SecretBox.DEVICE_KEYS, key, sealed, newGeneration)
                if (rewrapped == null) {
                    failures.add("kv:$key")
                } else {
                    db.waultDbQueries.putKv(key, rewrapped)
                }
            }

            db.waultDbQueries.selectAllDevices().executeAsList().forEach { row ->
                val rewrapped = reseal(SecretBox.DEVICE_ROOT, row.id, row.sealedRootKey, newGeneration)
                if (rewrapped == null) {
                    failures.add("device:${row.id}")
                    return@forEach
                }
                db.waultDbQueries.upsertDevice(
                    row.id, row.label, row.platform, row.signingKey, row.agreementKey,
                    rewrapped, row.isInitiator, row.onionAddress, row.lanAddress,
                    row.pairedAt, row.lastSyncedAt, row.revoked, row.keyGeneration
                )
                devices += 1

                val session = db.waultDbQueries.selectRatchetSession(row.id).executeAsOneOrNull()
                if (session != null) {
                    val resealed = reseal(SecretBox.RATCHET_STATE, row.id, session.state, newGeneration)
                    if (resealed == null) {
                        failures.add("session:${row.id}")
                    } else {
                        db.waultDbQueries.upsertRatchetSession(row.id, resealed, session.updatedAt)
                        sessions += 1
                    }
                }
            }
        }

        if (failures.isEmpty()) {
            vault.completeRotation()
        }

        return RotationReport(
            previousGeneration = previousGeneration,
            newGeneration = newGeneration,
            itemsRewrapped = items,
            foldersRewrapped = folders,
            devicesRewrapped = devices,
            sessionsRewrapped = sessions,
            failures = failures
        )
    }

    private fun reseal(domain: ByteArray, label: String, sealed: ByteArray, newGeneration: Int): ByteArray? {
        val generation = secretBox.generationOf(sealed) ?: return null
        if (generation == newGeneration) return sealed
        val plaintext = vault.withDataKeyFor(generation) { key ->
            secretBox.openWith(key, domain, label, sealed)
        } ?: return null
        return vault.withDataKey { key ->
            secretBox.sealWith(key, newGeneration, domain, label, plaintext)
        }
    }
}
