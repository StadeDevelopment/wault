package net.wault.item

import net.wault.crypto.platformCrypto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

class ItemCodecTest {

    private val crypto = platformCrypto()
    private val codec = ItemCodec(crypto)
    private val dataKey = crypto.randomBytes(32)

    private companion object {
        const val GENERATION = 3
    }

    private fun login() = VaultItem(
        id = "item-1",
        content = ItemContent.Login(
            title = "GitHub",
            username = "eren",
            password = "s3cr3t-value",
            uris = listOf(MatchableUri("https://github.com"))
        ),
        createdAt = 1L,
        updatedAt = 1L,
        revision = 1L,
        updatedBy = "device-a"
    )

    @Test
    fun `seal then open round-trips the content`() {
        val item = login()
        val sealed = codec.sealItem(dataKey, GENERATION, item)
        val opened = codec.openItem(dataKey, SealedRecord(item.id, item.revision, sealed))

        val content = assertIs<ItemContent.Login>(opened.content)
        assertEquals("GitHub", content.title)
        assertEquals("eren", content.username)
        assertEquals("s3cr3t-value", content.password)
        assertEquals("https://github.com", content.uris.single().uri)
    }

    @Test
    fun `the plaintext password never appears in the sealed bytes`() {
        val item = login()
        val sealed = codec.sealItem(dataKey, GENERATION, item)
        assertFalse(sealed.decodeToString().contains("s3cr3t-value"))
        assertFalse(sealed.decodeToString().contains("GitHub"))
    }

    @Test
    fun `a different data key cannot open the record`() {
        val item = login()
        val sealed = codec.sealItem(dataKey, GENERATION, item)
        val otherKey = crypto.randomBytes(32)

        assertFailsWith<DecodeFailure> {
            codec.openItem(otherKey, SealedRecord(item.id, item.revision, sealed))
        }
    }

    @Test
    fun `replaying a record under a different revision fails`() {
        val item = login()
        val sealed = codec.sealItem(dataKey, GENERATION, item)

        assertFailsWith<DecodeFailure> {
            codec.openItem(dataKey, SealedRecord(item.id, item.revision + 1, sealed))
        }
    }

    @Test
    fun `moving a record onto another id fails`() {
        val item = login()
        val sealed = codec.sealItem(dataKey, GENERATION, item)

        assertFailsWith<DecodeFailure> {
            codec.openItem(dataKey, SealedRecord("item-2", item.revision, sealed))
        }
    }

    @Test
    fun `the envelope advertises the generation that sealed it`() {
        val item = login()
        val sealed = codec.sealItem(dataKey, GENERATION, item)
        assertEquals(GENERATION, codec.generationOf(sealed))
    }

    @Test
    fun `a record resealed under a new generation differs and still opens`() {
        val item = login()
        val first = codec.sealItem(dataKey, 1, item)
        val second = codec.sealItem(dataKey, 2, item)

        assertFalse(first.contentEquals(second))
        assertEquals(1, codec.generationOf(first))
        assertEquals(2, codec.generationOf(second))
        assertIs<ItemContent.Login>(codec.openItem(dataKey, SealedRecord(item.id, item.revision, first)).content)
        assertIs<ItemContent.Login>(codec.openItem(dataKey, SealedRecord(item.id, item.revision, second)).content)
    }

    @Test
    fun `rewriting the generation byte breaks authentication`() {
        val item = login()
        val sealed = codec.sealItem(dataKey, 1, item)
        sealed[4] = 2

        assertFailsWith<DecodeFailure> {
            codec.openItem(dataKey, SealedRecord(item.id, item.revision, sealed))
        }
    }

    @Test
    fun `the folder and favourite flag survive the round trip`() {
        val item = login().copy(folderId = "folder-9", favorite = true)
        val sealed = codec.sealItem(dataKey, GENERATION, item)
        val opened = codec.openItem(dataKey, SealedRecord(item.id, item.revision, sealed))

        assertEquals("folder-9", opened.folderId)
        assertEquals(true, opened.favorite)
    }

    @Test
    fun `the folder id is not readable in the sealed bytes`() {
        val item = login().copy(folderId = "banking-folder", favorite = true)
        val sealed = codec.sealItem(dataKey, GENERATION, item)
        assertFalse(sealed.decodeToString().contains("banking-folder"))
    }

    @Test
    fun `flipping a ciphertext byte fails`() {
        val item = login()
        val sealed = codec.sealItem(dataKey, GENERATION, item)
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 0x01).toByte()

        assertFailsWith<DecodeFailure> {
            codec.openItem(dataKey, SealedRecord(item.id, item.revision, sealed))
        }
    }
}
