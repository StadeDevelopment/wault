package net.wault.autofill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FieldHeuristicsTest {

    private fun classify(
        passwordFlagged: Boolean = false,
        hints: List<String> = emptyList(),
        inputTypePassword: Boolean = false,
        inputTypeEmail: Boolean = false,
        hint: String? = null,
        idEntry: String? = null,
        contentDescription: String? = null
    ) = FieldHeuristics.classify(
        FieldSignals(
            passwordFlagged = passwordFlagged,
            autofillHints = hints,
            inputTypePassword = inputTypePassword,
            inputTypeEmail = inputTypeEmail,
            hint = hint,
            idEntry = idEntry,
            contentDescription = contentDescription
        )
    )

    @Test
    fun `a node the platform marks as a password wins outright`() {
        assertEquals(FillFieldKind.Password, classify(passwordFlagged = true))
    }

    @Test
    fun `a password marked node stays a password even with a misleading label`() {
        assertEquals(
            FillFieldKind.Password,
            classify(passwordFlagged = true, hint = "Email address")
        )
    }

    @Test
    fun `explicit autofill hints are honoured`() {
        assertEquals(FillFieldKind.Password, classify(hints = listOf("password")))
        assertEquals(FillFieldKind.Username, classify(hints = listOf("username")))
        assertEquals(FillFieldKind.Username, classify(hints = listOf("emailAddress")))
    }

    @Test
    fun `input type variations are read when no hint is present`() {
        assertEquals(FillFieldKind.Password, classify(inputTypePassword = true))
        assertEquals(FillFieldKind.Username, classify(inputTypeEmail = true))
    }

    @Test
    fun `english labels are recognised`() {
        assertEquals(FillFieldKind.Password, classify(hint = "Password"))
        assertEquals(FillFieldKind.Username, classify(hint = "Username"))
        assertEquals(FillFieldKind.Username, classify(hint = "Email"))
    }

    @Test
    fun `turkish labels are recognised`() {
        assertEquals(FillFieldKind.Password, classify(hint = "Parola"))
        assertEquals(FillFieldKind.Password, classify(hint = "Şifre"))
        assertEquals(FillFieldKind.Username, classify(hint = "Kullanıcı adı"))
        assertEquals(FillFieldKind.Username, classify(hint = "E-posta"))
    }

    @Test
    fun `resource ids are a usable signal`() {
        assertEquals(FillFieldKind.Password, classify(idEntry = "login_pwd_field"))
        assertEquals(FillFieldKind.Username, classify(idEntry = "txtUserName"))
    }

    @Test
    fun `a search box is never offered a credential`() {
        assertNull(classify(hint = "Search"))
        assertNull(classify(hint = "Arama"))
        assertNull(classify(idEntry = "search_query"))
    }

    @Test
    fun `a search box is not rescued by an email keyboard`() {
        assertNull(classify(hint = "Search", inputTypeEmail = true))
    }

    @Test
    fun `one-time codes are left alone`() {
        assertNull(classify(hint = "Verification code"))
        assertNull(classify(hint = "Doğrulama kodu"))
    }

    @Test
    fun `a message box is not a username`() {
        assertNull(classify(hint = "Message"))
        assertNull(classify(hint = "Mesaj"))
    }

    @Test
    fun `an unlabelled plain text box is left alone`() {
        assertNull(classify())
        assertNull(classify(hint = "Enter a value"))
    }

    @Test
    fun `password beats username when a label mentions both`() {
        assertEquals(FillFieldKind.Password, classify(hint = "Username or password"))
    }

    @Test
    fun `the confirm password field is still a password`() {
        assertEquals(FillFieldKind.Password, classify(idEntry = "confirmPassword"))
    }
}
