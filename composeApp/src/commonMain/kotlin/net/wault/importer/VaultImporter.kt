package net.wault.importer

import net.wault.item.ItemContent
import net.wault.item.MatchableUri
import net.wault.item.TotpSecret
import net.wault.totp.OtpAuthUri

enum class ImportFormat(val displayName: String) {
    Bitwarden("Bitwarden"),
    OnePassword("1Password"),
    Chrome("Chrome"),
    LastPass("LastPass"),
    KeePass("KeePass")
}

data class ImportedItem(
    val content: ItemContent,
    val folderName: String?,
    val favorite: Boolean
)

data class ImportPreview(
    val format: ImportFormat,
    val items: List<ImportedItem>,
    val skipped: Int
) {
    val folderNames: List<String> get() = items.mapNotNull { it.folderName }.distinct().sorted()
}

class UnrecognisedExport : IllegalArgumentException("this file is not a recognised password export")

object VaultImporter {

    fun preview(text: String): ImportPreview {
        val rows = Csv.parse(text)
        if (rows.size < 2) throw UnrecognisedExport()

        val header = rows.first().map { it.trim().lowercase() }
        val format = detect(header) ?: throw UnrecognisedExport()
        val body = rows.drop(1)

        var skipped = 0
        val items = body.mapNotNull { row ->
            val cells = header.indices.associateWith { row.getOrNull(it)?.trim().orEmpty() }
            val get = { name: String -> header.indexOf(name).takeIf { it >= 0 }?.let { cells[it] }.orEmpty() }
            val item = convert(format, get)
            if (item == null) skipped++
            item
        }

        if (items.isEmpty()) throw UnrecognisedExport()
        return ImportPreview(format, items, skipped)
    }

    private fun detect(header: List<String>): ImportFormat? {
        val hasTypedColumns = header.any {
            it.startsWith("login_") || it.startsWith("card_") || it.startsWith("identity_")
        }
        return when {
            hasTypedColumns -> ImportFormat.Bitwarden
            header.contains("type") && header.containsAll(listOf("folder", "favorite")) ->
                ImportFormat.Bitwarden

            header.contains("grouping") && header.containsAll(listOf("url", "username", "password")) ->
                ImportFormat.LastPass

            header.containsAll(listOf("title", "username", "password")) &&
                (header.contains("urls") || header.contains("websites") || header.contains("vault")) ->
                ImportFormat.OnePassword

            header.containsAll(listOf("name", "url", "username", "password")) -> ImportFormat.Chrome

            header.containsAll(listOf("title", "username", "password")) -> ImportFormat.KeePass

            else -> null
        }
    }

    private fun convert(format: ImportFormat, get: (String) -> String): ImportedItem? = when (format) {
        ImportFormat.Bitwarden -> fromBitwarden(get)
        ImportFormat.Chrome -> fromColumns(get, "name", "username", "password", "url", null, "note")
        ImportFormat.LastPass -> fromColumns(get, "name", "username", "password", "url", "grouping", "extra")
        ImportFormat.OnePassword -> fromOnePassword(get)
        ImportFormat.KeePass -> fromColumns(get, "title", "username", "password", "url", "group", "notes")
    }

    private fun fromBitwarden(get: (String) -> String): ImportedItem? {
        val type = get("type").ifBlank { "login" }
        val title = get("name")
        if (title.isBlank()) return null
        val notes = get("notes")
        val folder = get("folder").takeIf { it.isNotBlank() }
        val favorite = get("favorite").let { it == "1" || it.equals("true", ignoreCase = true) }

        val content: ItemContent = when {
            type.equals("card", ignoreCase = true) -> ItemContent.Card(
                title = title,
                cardholderName = get("card_cardholdername"),
                number = get("card_number"),
                brand = get("card_brand"),
                expiryMonth = get("card_expmonth").toIntOrNull(),
                expiryYear = get("card_expyear").toIntOrNull(),
                securityCode = get("card_code"),
                notes = notes
            )

            type.equals("note", ignoreCase = true) || type.equals("securenote", ignoreCase = true) ->
                ItemContent.Note(title = title, body = notes)

            type.equals("identity", ignoreCase = true) -> ItemContent.Identity(
                title = title,
                firstName = get("identity_firstname"),
                lastName = get("identity_lastname"),
                email = get("identity_email"),
                phone = get("identity_phone"),
                addressLine1 = get("identity_address1"),
                addressLine2 = get("identity_address2"),
                city = get("identity_city"),
                postalCode = get("identity_postalcode"),
                country = get("identity_country"),
                notes = notes
            )

            else -> ItemContent.Login(
                title = title,
                username = get("login_username"),
                password = get("login_password"),
                totp = totpFrom(get("login_totp")),
                uris = urisFrom(get("login_uri")),
                notes = notes
            )
        }
        return ImportedItem(content, folder, favorite)
    }

    private fun fromOnePassword(get: (String) -> String): ImportedItem? {
        val title = get("title")
        if (title.isBlank()) return null
        val urls = get("urls").ifBlank { get("websites") }
        return ImportedItem(
            content = ItemContent.Login(
                title = title,
                username = get("username"),
                password = get("password"),
                totp = totpFrom(get("otpauth").ifBlank { get("one-time password") }),
                uris = urisFrom(urls),
                notes = get("notes")
            ),
            folderName = get("vault").takeIf { it.isNotBlank() },
            favorite = get("favorite").equals("true", ignoreCase = true)
        )
    }

    private fun fromColumns(
        get: (String) -> String,
        titleKey: String,
        usernameKey: String,
        passwordKey: String,
        urlKey: String,
        folderKey: String?,
        notesKey: String?
    ): ImportedItem? {
        val title = get(titleKey).ifBlank { get(urlKey) }
        if (title.isBlank()) return null
        return ImportedItem(
            content = ItemContent.Login(
                title = title,
                username = get(usernameKey),
                password = get(passwordKey),
                uris = urisFrom(get(urlKey)),
                notes = notesKey?.let { get(it) }.orEmpty()
            ),
            folderName = folderKey?.let { get(it) }?.takeIf { it.isNotBlank() },
            favorite = false
        )
    }

    private fun urisFrom(raw: String): List<MatchableUri> =
        raw.split(',', '\n', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "http://" && it != "https://" }
            .map { MatchableUri(it) }

    private fun totpFrom(raw: String): TotpSecret? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        OtpAuthUri.parse(value)?.let { return it }
        val cleaned = value.uppercase().filter { it.isLetterOrDigit() }
        if (cleaned.length < 16) return null
        return TotpSecret(secret = cleaned)
    }
}
