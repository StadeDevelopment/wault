package net.wault.importer

import net.wault.item.ItemContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CsvTest {

    @Test
    fun `plain rows parse`() {
        val rows = Csv.parse("a,b,c\n1,2,3")
        assertEquals(listOf(listOf("a", "b", "c"), listOf("1", "2", "3")), rows)
    }

    @Test
    fun `quoted fields keep their commas`() {
        val rows = Csv.parse("name,notes\n\"Bank\",\"one, two, three\"")
        assertEquals(listOf("Bank", "one, two, three"), rows[1])
    }

    @Test
    fun `escaped quotes survive`() {
        val rows = Csv.parse("a\n\"he said \"\"hi\"\"\"")
        assertEquals("he said \"hi\"", rows[1][0])
    }

    @Test
    fun `newlines inside quotes stay in the field`() {
        val rows = Csv.parse("a,b\n\"line1\nline2\",x")
        assertEquals(2, rows.size)
        assertEquals("line1\nline2", rows[1][0])
    }

    @Test
    fun `carriage returns are handled`() {
        val rows = Csv.parse("a,b\r\n1,2\r\n")
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), rows)
    }

    @Test
    fun `a byte order mark is stripped`() {
        val rows = Csv.parse("﻿name,url\nx,y")
        assertEquals("name", rows[0][0])
    }

    @Test
    fun `empty fields are preserved`() {
        val rows = Csv.parse("a,b,c\n1,,3")
        assertEquals(listOf("1", "", "3"), rows[1])
    }
}

class VaultImporterTest {

    @Test
    fun `a bitwarden export is recognised`() {
        val csv = """
            folder,favorite,type,name,notes,fields,login_uri,login_username,login_password,login_totp
            Work,1,login,GitHub,some notes,,https://github.com,eren,s3cr3t,JBSWY3DPEHPK3PXP
        """.trimIndent()

        val preview = VaultImporter.preview(csv)

        assertEquals(ImportFormat.Bitwarden, preview.format)
        val item = preview.items.single()
        assertEquals("Work", item.folderName)
        assertTrue(item.favorite)
        val login = assertIs<ItemContent.Login>(item.content)
        assertEquals("GitHub", login.title)
        assertEquals("eren", login.username)
        assertEquals("s3cr3t", login.password)
        assertEquals("https://github.com", login.uris.single().uri)
        assertNotNull(login.totp)
    }

    @Test
    fun `bitwarden cards become card items`() {
        val csv = """
            folder,favorite,type,name,notes,card_brand,card_number,card_expmonth,card_expyear,card_code,card_cardholdername
            ,0,card,Visa,,Visa,4111111111111111,12,2030,123,Eren
        """.trimIndent()

        val card = assertIs<ItemContent.Card>(VaultImporter.preview(csv).items.single().content)
        assertEquals("4111111111111111", card.number)
        assertEquals(12, card.expiryMonth)
        assertEquals(2030, card.expiryYear)
        assertEquals("Eren", card.cardholderName)
    }

    @Test
    fun `bitwarden secure notes become notes`() {
        val csv = """
            folder,favorite,type,name,notes,login_uri,login_username,login_password
            ,0,note,Recovery codes,abc-def,,,
        """.trimIndent()

        val note = assertIs<ItemContent.Note>(VaultImporter.preview(csv).items.single().content)
        assertEquals("abc-def", note.body)
    }

    @Test
    fun `a chrome export is recognised`() {
        val csv = """
            name,url,username,password,note
            github.com,https://github.com/login,eren,s3cr3t,
        """.trimIndent()

        val preview = VaultImporter.preview(csv)
        assertEquals(ImportFormat.Chrome, preview.format)
        val login = assertIs<ItemContent.Login>(preview.items.single().content)
        assertEquals("eren", login.username)
        assertEquals("https://github.com/login", login.uris.single().uri)
    }

    @Test
    fun `a lastpass export keeps its grouping as a folder`() {
        val csv = """
            url,username,password,extra,name,grouping,fav
            https://bank.com,eren,s3cr3t,note text,Bank,Finance,0
        """.trimIndent()

        val preview = VaultImporter.preview(csv)
        assertEquals(ImportFormat.LastPass, preview.format)
        assertEquals("Finance", preview.items.single().folderName)
    }

    @Test
    fun `a 1password export is recognised`() {
        val csv = """
            title,username,password,urls,notes,vault
            Mail,eren@stade.dev,s3cr3t,https://mail.com,,Personal
        """.trimIndent()

        val preview = VaultImporter.preview(csv)
        assertEquals(ImportFormat.OnePassword, preview.format)
        assertEquals("Personal", preview.items.single().folderName)
    }

    @Test
    fun `a keepass export is recognised`() {
        val csv = """
            Title,Username,Password,URL,Notes,Group
            Router,admin,admin123,http://192.168.1.1,,Home
        """.trimIndent()

        val preview = VaultImporter.preview(csv)
        assertEquals(ImportFormat.KeePass, preview.format)
        assertEquals("Home", preview.items.single().folderName)
    }

    @Test
    fun `rows without a title are skipped rather than imported blank`() {
        val csv = """
            name,url,username,password
            GitHub,https://github.com,eren,s3cr3t
            ,,nobody,nothing
        """.trimIndent()

        val preview = VaultImporter.preview(csv)
        assertEquals(1, preview.items.size)
        assertEquals(1, preview.skipped)
    }

    @Test
    fun `a totp secret without a uri still imports`() {
        val csv = """
            folder,favorite,type,name,login_uri,login_username,login_password,login_totp
            ,0,login,Mail,https://mail.com,eren,pw,JBSWY3DPEHPK3PXP
        """.trimIndent()

        val login = assertIs<ItemContent.Login>(VaultImporter.preview(csv).items.single().content)
        assertEquals("JBSWY3DPEHPK3PXP", login.totp?.secret)
    }

    @Test
    fun `folder names are collected for the preview`() {
        val csv = """
            folder,favorite,type,name,login_uri,login_username,login_password
            Work,0,login,A,,a,1
            Home,0,login,B,,b,2
            Work,0,login,C,,c,3
        """.trimIndent()

        assertEquals(listOf("Home", "Work"), VaultImporter.preview(csv).folderNames)
    }

    @Test
    fun `an unrelated csv is refused`() {
        assertFailsWith<UnrecognisedExport> {
            VaultImporter.preview("date,amount,payee\n2026-01-01,12.00,Shop")
        }
    }

    @Test
    fun `an empty file is refused`() {
        assertFailsWith<UnrecognisedExport> { VaultImporter.preview("") }
    }

    @Test
    fun `quoted multi-line notes survive the import`() {
        val csv = "name,url,username,password,note\n" +
            "Bank,https://bank.com,eren,pw,\"line one\nline two\""

        val login = assertIs<ItemContent.Login>(VaultImporter.preview(csv).items.single().content)
        assertEquals("line one\nline two", login.notes)
    }
}
