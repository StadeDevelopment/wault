package net.wault.autofill

enum class FillFieldKind { Username, Password }

data class FieldSignals(
    val passwordFlagged: Boolean = false,
    val autofillHints: List<String> = emptyList(),
    val inputTypePassword: Boolean = false,
    val inputTypeEmail: Boolean = false,
    val hint: String? = null,
    val idEntry: String? = null,
    val contentDescription: String? = null
)

private val PASSWORD_HINTS = setOf(
    "password",
    "passwd",
    "pwd",
    "newpassword",
    "new_password",
    "current-password",
    "new-password"
)

private val USERNAME_HINTS = setOf(
    "username",
    "user",
    "email",
    "emailaddress",
    "email_address",
    "phone",
    "name"
)

private val PASSWORD_WORDS = listOf(
    "password",
    "passwd",
    "pwd",
    "passphrase",
    "parola",
    "şifre",
    "sifre"
)

private val USERNAME_WORDS = listOf(
    "username",
    "user_name",
    "userid",
    "user id",
    "login",
    "email",
    "e-mail",
    "eposta",
    "e-posta",
    "kullanıcı",
    "kullanici",
    "identifier",
    "account"
)

private val NEGATIVE_WORDS = listOf(
    "search",
    "query",
    "arama",
    "ara ",
    "filter",
    "message",
    "mesaj",
    "comment",
    "yorum",
    "subject",
    "url",
    "address bar",
    "otp",
    "verification",
    "doğrulama",
    "dogrulama"
)

object FieldHeuristics {

    fun classify(signals: FieldSignals): FillFieldKind? {
        if (signals.passwordFlagged) return FillFieldKind.Password

        signals.autofillHints.forEach { raw ->
            val hint = raw.lowercase().trim()
            if (hint in PASSWORD_HINTS) return FillFieldKind.Password
            if (hint in USERNAME_HINTS) return FillFieldKind.Username
        }

        if (signals.inputTypePassword) return FillFieldKind.Password

        val text = listOfNotNull(
            signals.hint,
            signals.idEntry,
            signals.contentDescription
        ).joinToString(" ").lowercase()

        if (NEGATIVE_WORDS.any { text.contains(it) }) return null

        if (PASSWORD_WORDS.any { text.contains(it) }) return FillFieldKind.Password
        if (USERNAME_WORDS.any { text.contains(it) }) return FillFieldKind.Username

        if (signals.inputTypeEmail) return FillFieldKind.Username

        return null
    }
}
