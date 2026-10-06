package net.wault.health

import net.wault.item.ItemContent
import net.wault.item.ItemSearch
import net.wault.item.VaultItem
import net.wault.strength.StrengthLevel
import net.wault.strength.StrengthMeter

enum class HealthIssue { Reused, Weak, Old, MissingTotp, ExpiringCard }

data class HealthFinding(
    val issue: HealthIssue,
    val itemId: String,
    val title: String,
    val detail: String
)

data class ReuseGroupMember(val itemId: String, val title: String)

data class ReuseGroup(
    val id: String,
    val members: List<ReuseGroupMember>
) {
    val count: Int get() = members.size
}

data class HealthReport(
    val score: Int,
    val totalLogins: Int,
    val findings: List<HealthFinding>,
    val reuseGroups: List<ReuseGroup> = emptyList()
) {
    fun byIssue(issue: HealthIssue): List<HealthFinding> = findings.filter { it.issue == issue }
}

object VaultHealth {

    private const val OLD_PASSWORD_DAYS = 365L
    private const val MILLIS_PER_DAY = 86_400_000L

    fun analyze(items: List<VaultItem>, nowMillis: Long): HealthReport {
        val logins = items.filter { it.content is ItemContent.Login }
        val findings = ArrayList<HealthFinding>()

        val byPassword = HashMap<String, MutableList<VaultItem>>()
        for (item in logins) {
            val login = item.content as ItemContent.Login
            if (login.password.isBlank()) continue
            byPassword.getOrPut(login.password) { ArrayList() }.add(item)
        }

        val reuseGroups = ArrayList<ReuseGroup>()

        for ((_, sharing) in byPassword) {
            if (sharing.size < 2) continue
            val others = sharing.map { it.title.ifBlank { it.id.take(8) } }
            reuseGroups.add(
                ReuseGroup(
                    id = sharing.map { it.id }.sorted().joinToString("|"),
                    members = sharing.map {
                        ReuseGroupMember(it.id, it.title.ifBlank { it.id.take(8) })
                    }
                )
            )
            for (item in sharing) {
                val peers = others.filterNot { it == item.title }
                findings.add(
                    HealthFinding(
                        issue = HealthIssue.Reused,
                        itemId = item.id,
                        title = item.title,
                        detail = peers.joinToString(", ")
                    )
                )
            }
        }

        for (item in logins) {
            val login = item.content as ItemContent.Login
            if (login.password.isBlank()) continue

            val report = StrengthMeter.evaluate(login.password)
            if (report.level == StrengthLevel.Critical || report.level == StrengthLevel.Weak) {
                findings.add(
                    HealthFinding(
                        issue = HealthIssue.Weak,
                        itemId = item.id,
                        title = item.title,
                        detail = "${report.entropyBits.toInt()} bits"
                    )
                )
            }

            val changedAt = login.passwordChangedAt ?: item.createdAt
            val ageDays = (nowMillis - changedAt) / MILLIS_PER_DAY
            if (ageDays > OLD_PASSWORD_DAYS) {
                findings.add(
                    HealthFinding(
                        issue = HealthIssue.Old,
                        itemId = item.id,
                        title = item.title,
                        detail = "$ageDays"
                    )
                )
            }

            if (login.totp == null && login.uris.any { uri -> ItemSearch.hostOf(uri.uri) in TOTP_EXPECTED_HOSTS }) {
                findings.add(
                    HealthFinding(
                        issue = HealthIssue.MissingTotp,
                        itemId = item.id,
                        title = item.title,
                        detail = ""
                    )
                )
            }
        }

        for (item in items) {
            val card = item.content as? ItemContent.Card ?: continue
            val month = card.expiryMonth ?: continue
            val year = card.expiryYear ?: continue
            if (expiresWithinNinetyDays(year, month, nowMillis)) {
                findings.add(
                    HealthFinding(
                        issue = HealthIssue.ExpiringCard,
                        itemId = item.id,
                        title = item.title,
                        detail = "$month/$year"
                    )
                )
            }
        }

        return HealthReport(
            score = scoreFor(logins.size, findings),
            totalLogins = logins.size,
            findings = findings,
            reuseGroups = reuseGroups.sortedByDescending { it.count }
        )
    }

    private fun scoreFor(totalLogins: Int, findings: List<HealthFinding>): Int {
        if (totalLogins == 0) return 100
        val weighted = findings.sumOf { finding ->
            when (finding.issue) {
                HealthIssue.Reused -> 3.0
                HealthIssue.Weak -> 4.0
                HealthIssue.Old -> 1.0
                HealthIssue.MissingTotp -> 1.0
                HealthIssue.ExpiringCard -> 0.5
            }
        }
        val worstCase = totalLogins * 8.0
        if (worstCase <= 0.0) return 100
        return (100.0 * (1.0 - (weighted / worstCase))).coerceIn(0.0, 100.0).toInt()
    }

    private fun expiresWithinNinetyDays(year: Int, month: Int, nowMillis: Long): Boolean {
        val nowDays = nowMillis / MILLIS_PER_DAY
        val nowYear = 1970 + (nowDays / 365).toInt()
        val nowMonth = (((nowDays % 365) / 30).toInt() + 1).coerceIn(1, 12)
        val monthsUntil = (year - nowYear) * 12 + (month - nowMonth)
        return monthsUntil <= 3
    }

    private val TOTP_EXPECTED_HOSTS = setOf(
        "github.com", "gitlab.com", "google.com", "accounts.google.com",
        "amazon.com", "paypal.com", "dropbox.com", "microsoft.com",
        "live.com", "cloudflare.com", "digitalocean.com", "aws.amazon.com"
    )
}
