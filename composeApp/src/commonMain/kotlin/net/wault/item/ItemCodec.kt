package net.wault.item

import net.wault.crypto.CryptoApi
import net.wault.crypto.ENVELOPE_NONCE_LEN
import net.wault.crypto.Envelope
import net.wault.crypto.zero
import kotlinx.serialization.json.Json

private const val ITEM_KEY_LEN = 32
private val ITEM_KEY_INFO = "wault.item.v1".encodeToByteArray()
private val FOLDER_KEY_INFO = "wault.folder.v1".encodeToByteArray()

class DecodeFailure(val recordId: String) : IllegalStateException("cannot open record $recordId")

class ItemCodec(private val crypto: CryptoApi) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }

    fun sealItem(dataKey: ByteArray, generation: Int, item: VaultItem): ByteArray =
        seal(
            dataKey, generation, ITEM_KEY_INFO, item.id, item.revision,
            json.encodeToString(
                ItemBody.serializer(),
                ItemBody(item.content, item.folderId, item.favorite)
            )
        )

    fun openItem(dataKey: ByteArray, record: SealedRecord): ItemBody {
        val plaintext = open(dataKey, ITEM_KEY_INFO, record)
        return json.decodeFromString(ItemBody.serializer(), plaintext)
    }

    fun sealFolderName(dataKey: ByteArray, generation: Int, folderId: String, revision: Long, name: String): ByteArray =
        seal(dataKey, generation, FOLDER_KEY_INFO, folderId, revision, name)

    fun openFolderName(dataKey: ByteArray, record: SealedRecord): String =
        open(dataKey, FOLDER_KEY_INFO, record)

    fun generationOf(payload: ByteArray): Int? = Envelope.generationOf(payload)

    private fun seal(
        dataKey: ByteArray,
        generation: Int,
        info: ByteArray,
        id: String,
        revision: Long,
        plaintext: String
    ): ByteArray {
        val recordKey = deriveRecordKey(dataKey, info, id)
        try {
            val nonce = crypto.randomBytes(ENVELOPE_NONCE_LEN)
            val ciphertext = crypto.aeadSeal(
                key = recordKey,
                nonce = nonce,
                plaintext = plaintext.encodeToByteArray(),
                associatedData = associatedData(id, revision, generation)
            )
            return Envelope.wrap(generation, nonce, ciphertext)
        } finally {
            recordKey.zero()
        }
    }

    private fun open(dataKey: ByteArray, info: ByteArray, record: SealedRecord): String {
        val envelope = Envelope.unwrap(record.payload) ?: throw DecodeFailure(record.id)
        val recordKey = deriveRecordKey(dataKey, info, record.id)
        try {
            val plaintext = crypto.aeadOpen(
                key = recordKey,
                nonce = envelope.nonce,
                ciphertext = envelope.ciphertext,
                associatedData = associatedData(record.id, record.revision, envelope.generation)
            ) ?: throw DecodeFailure(record.id)
            return plaintext.decodeToString()
        } finally {
            recordKey.zero()
        }
    }

    private fun deriveRecordKey(dataKey: ByteArray, info: ByteArray, id: String): ByteArray =
        crypto.hkdf(dataKey, id.encodeToByteArray(), info, ITEM_KEY_LEN)

    private fun associatedData(id: String, revision: Long, generation: Int): ByteArray =
        "$id|$revision|$generation".encodeToByteArray()
}

data class SealedRecord(
    val id: String,
    val revision: Long,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SealedRecord) return false
        return id == other.id && revision == other.revision && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int = (id.hashCode() * 31 + revision.hashCode()) * 31 + payload.contentHashCode()
}
