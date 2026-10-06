package net.wault.item

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ItemType {
    @SerialName("login") Login,
    @SerialName("card") Card,
    @SerialName("note") Note,
    @SerialName("identity") Identity,
    @SerialName("sshKey") SshKey,
    @SerialName("authenticator") Authenticator
}

@Serializable
data class VaultItem(
    val id: String,
    val content: ItemContent,
    val folderId: String? = null,
    val favorite: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Long,
    val updatedBy: String,
    val deleted: Boolean = false
) {
    val type: ItemType
        get() = when (content) {
            is ItemContent.Login -> ItemType.Login
            is ItemContent.Card -> ItemType.Card
            is ItemContent.Note -> ItemType.Note
            is ItemContent.Identity -> ItemType.Identity
            is ItemContent.SshKey -> ItemType.SshKey
            is ItemContent.Authenticator -> ItemType.Authenticator
        }

    val title: String
        get() = content.title
}

@Serializable
sealed interface ItemContent {
    val title: String
    val notes: String
    val customFields: List<CustomField>

    @Serializable
    @SerialName("login")
    data class Login(
        override val title: String,
        val username: String = "",
        val password: String = "",
        val totp: TotpSecret? = null,
        val uris: List<MatchableUri> = emptyList(),
        val passwordHistory: List<PasswordHistoryEntry> = emptyList(),
        val passwordChangedAt: Long? = null,
        override val notes: String = "",
        override val customFields: List<CustomField> = emptyList()
    ) : ItemContent

    @Serializable
    @SerialName("card")
    data class Card(
        override val title: String,
        val cardholderName: String = "",
        val number: String = "",
        val brand: String = "",
        val expiryMonth: Int? = null,
        val expiryYear: Int? = null,
        val securityCode: String = "",
        override val notes: String = "",
        override val customFields: List<CustomField> = emptyList()
    ) : ItemContent

    @Serializable
    @SerialName("note")
    data class Note(
        override val title: String,
        val body: String = "",
        override val customFields: List<CustomField> = emptyList()
    ) : ItemContent {
        override val notes: String get() = body
    }

    @Serializable
    @SerialName("identity")
    data class Identity(
        override val title: String,
        val firstName: String = "",
        val lastName: String = "",
        val email: String = "",
        val phone: String = "",
        val addressLine1: String = "",
        val addressLine2: String = "",
        val city: String = "",
        val postalCode: String = "",
        val country: String = "",
        val nationalId: String = "",
        val passportNumber: String = "",
        override val notes: String = "",
        override val customFields: List<CustomField> = emptyList()
    ) : ItemContent

    @Serializable
    @SerialName("authenticator")
    data class Authenticator(
        override val title: String,
        val secret: TotpSecret,
        override val notes: String = "",
        override val customFields: List<CustomField> = emptyList()
    ) : ItemContent

    @Serializable
    @SerialName("sshKey")
    data class SshKey(
        override val title: String,
        val privateKey: String = "",
        val publicKey: String = "",
        val fingerprint: String = "",
        override val notes: String = "",
        override val customFields: List<CustomField> = emptyList()
    ) : ItemContent
}

@Serializable
data class ItemBody(
    val content: ItemContent,
    val folderId: String? = null,
    val favorite: Boolean = false
)

@Serializable
data class CustomField(
    val name: String,
    val value: String,
    val concealed: Boolean = false
)

@Serializable
data class PasswordHistoryEntry(
    val password: String,
    val replacedAt: Long
)

@Serializable
data class TotpSecret(
    val secret: String,
    val issuer: String = "",
    val account: String = "",
    val digits: Int = 6,
    val periodSeconds: Int = 30,
    val algorithm: TotpAlgorithm = TotpAlgorithm.Sha1
)

@Serializable
enum class TotpAlgorithm {
    @SerialName("SHA1") Sha1,
    @SerialName("SHA256") Sha256,
    @SerialName("SHA512") Sha512
}

@Serializable
data class MatchableUri(
    val uri: String,
    val match: UriMatch = UriMatch.Domain
)

@Serializable
enum class UriMatch {
    @SerialName("domain") Domain,
    @SerialName("host") Host,
    @SerialName("startsWith") StartsWith,
    @SerialName("exact") Exact,
    @SerialName("regex") Regex,
    @SerialName("never") Never
}

@Serializable
data class Folder(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Long,
    val updatedBy: String,
    val deleted: Boolean = false,
    val icon: String = ""
)
