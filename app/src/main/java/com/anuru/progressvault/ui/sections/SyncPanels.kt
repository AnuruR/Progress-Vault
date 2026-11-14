package com.anuru.progressvault.ui.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anuru.progressvault.R
import com.anuru.progressvault.data.db.entity.LeetCodeProblemEntity
import com.anuru.progressvault.data.db.entity.SyncSource
import com.anuru.progressvault.ui.components.StatusChip
import com.anuru.progressvault.ui.components.TerminalLabel
import com.anuru.progressvault.ui.components.VaultCard
import com.anuru.progressvault.ui.components.formatMedium
import com.anuru.progressvault.ui.connections.SyncStatusRow
import com.anuru.progressvault.ui.theme.VaultColors
import com.anuru.progressvault.domain.model.AccentPalette
import java.time.format.TextStyle

/** Sync status + synced data for LeetCode / GitHub sections. */
@Composable
fun SyncPanel(panel: SyncPanelState, accent: Color, onSync: () -> Unit, onConnections: () -> Unit) {
    VaultCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SyncStatusRow(panel.sync, panel.configured, panel.running, onSync)
            when (panel.source) {
                SyncSource.LEETCODE -> LeetCodeContent(panel, accent)
                SyncSource.GITHUB -> GitHubContent(panel, accent)
                null -> Unit
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sync_manual_note), style = MaterialTheme.typography.bodySmall, color = VaultColors.TextMuted, modifier = Modifier.weight(1f))
                TextButton(onClick = onConnections) { Text(stringResource(R.string.action_connections)) }
            }
        }
    }
}

@Composable
private fun LeetCodeContent(panel: SyncPanelState, accent: Color) {
    panel.stats?.let { s ->
        TerminalLabel(stringResource(R.string.lc_totals))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(s.easySolved, stringResource(R.string.lc_easy), VaultColors.Green)
            Stat(s.mediumSolved, stringResource(R.string.lc_medium), VaultColors.Amber)
            Stat(s.hardSolved, stringResource(R.string.lc_hard), VaultColors.Error)
            Stat(s.totalSolved, stringResource(R.string.lc_total), accent)
        }
    }
    TerminalLabel(stringResource(R.string.lc_solved_this_week))
    if (panel.problems.isEmpty()) {
        Text(stringResource(R.string.lc_no_solves), style = MaterialTheme.typography.bodyMedium, color = VaultColors.TextMuted)
    } else {
        panel.problems.forEach { ProblemRow(it) }
    }
}

@Composable
private fun Stat(value: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = VaultColors.TextMuted)
    }
}

@Composable
private fun ProblemRow(p: LeetCodeProblemEntity) {
    val uri = LocalUriHandler.current
    val zone = java.time.ZoneId.systemDefault()
    val label = stringResource(R.string.cd_open_problem, p.title)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .semantics { contentDescription = label }
            .then(Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            TextButton(onClick = { runCatching { uri.openUri("https://leetcode.com/problems/${p.slug}/") } }) {
                Text(p.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = VaultColors.TextPrimary)
            }
            val tags = p.topicTags.take(3).joinToString(" · ")
            Text(
                listOf(p.solvedAt.atZone(zone).toLocalDate().formatMedium(), tags).filter { it.isNotBlank() }.joinToString("  ·  "),
                style = MaterialTheme.typography.labelSmall,
                color = VaultColors.TextMuted,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        p.difficulty?.let { d ->
            val color = when (d) { "EASY" -> VaultColors.Green; "MEDIUM" -> VaultColors.Amber; else -> VaultColors.Error }
            StatusChip(d.lowercase(), color)
        }
    }
}

@Composable
private fun GitHubContent(panel: SyncPanelState, accent: Color) {
    val byDate = panel.days.associate { it.date to it.count }
    val total = byDate.values.sum()
    Row(verticalAlignment = Alignment.Bottom) {
        Text(total.toString(), style = MaterialTheme.typography.headlineSmall, color = accent)
        Spacer(Modifier.width(8.dp))
        TerminalLabel(stringResource(R.string.gh_this_week))
    }
    val max = (byDate.values.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        panel.weekDays.forEach { date ->
            val count = byDate[date] ?: 0
            // github.com's four contribution greens, scaled to this week's busiest day.
            val level = if (count == 0) 0 else (1 + 3 * (count - 1) / max.coerceAtLeast(1)).coerceIn(1, 4)
            val cd = androidx.compose.ui.res.pluralStringResource(R.plurals.cd_contribution_day, count, date.formatMedium(), count)
            Column(Modifier.weight(1f).clearAndSetSemantics { contentDescription = cd }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(6.dp))
                        .background(if (count == 0) VaultColors.Background else Color(AccentPalette.githubLevels[level - 1]))
                        .border(1.dp, VaultColors.Border, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (count > 0) Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = VaultColors.TextPrimary)
                }
                Spacer(Modifier.height(4.dp))
                Text(date.dayOfWeek.getDisplayName(TextStyle.NARROW, androidx.compose.ui.platform.LocalConfiguration.current.locales[0]), style = MaterialTheme.typography.labelSmall, color = VaultColors.TextMuted)
            }
        }
    }
}
