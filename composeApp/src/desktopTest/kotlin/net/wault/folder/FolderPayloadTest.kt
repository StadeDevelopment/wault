package net.wault.folder

import kotlin.test.Test
import kotlin.test.assertEquals

private const val NUL = "\u0000"

class FolderPayloadTest {

    @Test
    fun `a name and icon survive a round trip`() {
        val encoded = FolderPayload.encode("Banking", "bank")
        assertEquals("Banking", FolderPayload.decodeName(encoded))
        assertEquals("bank", FolderPayload.decodeIcon(encoded))
    }

    @Test
    fun `folders saved before icons existed still read back`() {
        val legacy = "Work"
        assertEquals("Work", FolderPayload.decodeName(legacy))
        assertEquals("", FolderPayload.decodeIcon(legacy))
    }

    @Test
    fun `a folder with no icon stays in the old format`() {
        val encoded = FolderPayload.encode("Work", "")
        assertEquals(
            "Work",
            encoded,
            "an icon-less folder must stay readable by builds that predate icons"
        )
    }

    @Test
    fun `names containing spaces and punctuation are untouched`() {
        val tricky = "Bank & Co. - personal, 2026"
        val encoded = FolderPayload.encode(tricky, "bank")
        assertEquals(tricky, FolderPayload.decodeName(encoded))
        assertEquals("bank", FolderPayload.decodeIcon(encoded))
    }

    @Test
    fun `a deleted folder seals an empty name without crashing`() {
        assertEquals("", FolderPayload.decodeName(""))
        assertEquals("", FolderPayload.decodeIcon(""))
    }

    @Test
    fun `a truncated marker degrades to a plain name`() {
        val broken = NUL + "bank"
        assertEquals(broken, FolderPayload.decodeName(broken))
        assertEquals("", FolderPayload.decodeIcon(broken))
    }

    @Test
    fun `every catalog icon round trips`() {
        FolderIcons.all.forEach { option ->
            val encoded = FolderPayload.encode("Folder", option.id)
            assertEquals(option.id, FolderPayload.decodeIcon(encoded))
            assertEquals("Folder", FolderPayload.decodeName(encoded))
        }
    }

    @Test
    fun `an unknown icon id falls back to the plain folder glyph`() {
        assertEquals(
            FolderIcons.imageFor(FolderIcons.DEFAULT_ID),
            FolderIcons.imageFor("an-icon-from-a-newer-build"),
            "a folder synced from a newer version must still render"
        )
    }
}
