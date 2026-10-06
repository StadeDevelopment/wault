package net.wault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.wault.AppContainer
import net.wault.health.HealthIssue
import net.wault.ui.components.LocalHomeBarClearance
import net.wault.ui.components.secondaryClickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import net.wault.health.ReuseGroup
import net.wault.ui.i18n.LocalStrings
import net.wault.ui.theme.WaultColors

@Composable
fun HealthScreen(
    container: AppContainer,
    onOpenItem: (String) -> Unit
) {
    val strings = LocalStrings.current
    val items by container.items.items.collectAsState()
    val report = remember(items) { container.health() }

    val scoreColor = when {
        report.score >= 85 -> WaultColors.excellent
        report.score >= 65 -> WaultColors.strong
        report.score >= 45 -> WaultColors.fair
        report.score >= 25 -> WaultColors.weak
        else -> WaultColors.critical
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = LocalHomeBarClearance.current + 24.dp)
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.healthTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${report.score}/100",
                        style = MaterialTheme.typography.headlineSmall,
                        color = scoreColor
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    strings.itemCount(report.totalLogins),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
            }
        }

        if (report.reuseGroups.isNotEmpty()) {
            item(key = "reuse-header") {
                Text(
                    text = strings.healthReused,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
            }

            items(report.reuseGroups, key = { "reuse-${it.id}" }) { group ->
                ReuseGroupCard(group = group, onOpenItem = onOpenItem)
                Spacer(Modifier.height(8.dp))
            }
        }

        if (report.findings.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        strings.healthAllClear,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HealthIssue.entries.forEach { issue ->
            if (issue == HealthIssue.Reused) return@forEach
            val findings = report.byIssue(issue)
            if (findings.isEmpty()) return@forEach

            item(key = "header-${issue.name}") {
                Column {
                    HorizontalDivider()
                    Text(
                        text = headingFor(issue),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                }
            }

            items(findings, key = { "${issue.name}-${it.itemId}" }) { finding ->
                ListItem(
                    headlineContent = { Text(finding.title.ifBlank { finding.itemId.take(8) }) },
                    supportingContent = {
                        val detail = when (issue) {
                            HealthIssue.Reused -> strings.healthReusedDetail(finding.detail)
                            HealthIssue.Old -> strings.healthOldDetail(finding.detail)
                            else -> finding.detail
                        }
                        if (detail.isNotBlank()) Text(detail)
                    },
                    modifier = Modifier.secondaryClickable { onOpenItem(finding.itemId) }
                )
            }
        }
    }
}

@Composable
private fun ReuseGroupCard(group: ReuseGroup, onOpenItem: (String) -> Unit) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "reuseChevron"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .secondaryClickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.healthReuseGroup(group.count),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = strings.healthReuseGroupBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = rotation }
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    group.members.forEach { member ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .secondaryClickable { onOpenItem(member.itemId) }
                                .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = member.title,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun headingFor(issue: HealthIssue): String {
    val strings = LocalStrings.current
    return when (issue) {
        HealthIssue.Reused -> strings.healthReused
        HealthIssue.Weak -> strings.healthWeak
        HealthIssue.Old -> strings.healthOld
        HealthIssue.MissingTotp -> strings.healthMissingTotp
        HealthIssue.ExpiringCard -> strings.healthExpiringCard
    }
}
