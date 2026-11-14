package com.anuru.progressvault.ui.skills

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anuru.progressvault.R
import com.anuru.progressvault.domain.skills.Skill
import com.anuru.progressvault.domain.skills.SkillInsight
import com.anuru.progressvault.domain.skills.SkillLevel
import com.anuru.progressvault.domain.skills.SkillRules
import com.anuru.progressvault.domain.skills.SkillSource
import com.anuru.progressvault.ui.components.EmptyState
import com.anuru.progressvault.ui.components.StatusChip
import com.anuru.progressvault.ui.components.TerminalLabel
import com.anuru.progressvault.ui.components.VaultCard
import com.anuru.progressvault.ui.components.formatMedium
import com.anuru.progressvault.ui.theme.VaultColors
import java.time.temporal.ChronoUnit

fun SkillLevel.color(): Color = when (this) {
    SkillLevel.BEGINNER -> VaultColors.Blue
    SkillLevel.PRACTICING -> VaultColors.Purple
    SkillLevel.STRONG -> VaultColors.Green
}

@Composable
fun SkillLevel.label(): String = stringResource(
    when (this) {
        SkillLevel.BEGINNER -> R.string.skill_level_beginner
        SkillLevel.PRACTICING -> R.string.skill_level_practicing
        SkillLevel.STRONG -> R.string.skill_level_strong
    },
)

@Composable
fun SkillSource.label(): String = stringResource(
    when (this) {
        SkillSource.LEETCODE -> R.string.skill_source_leetcode
        SkillSource.COURSE -> R.string.skill_source_course
        SkillSource.MANUAL -> R.string.skill_source_manual
    },
)

@Composable
fun SkillsScreen(viewModel: SkillsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var detail by remember { mutableStateOf<Skill?>(null) }
    var draft by remember { mutableStateOf<SkillDraft?>(null) }

    Scaffold(
        containerColor = VaultColors.Background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { draft = viewModel.newDraft() },
                containerColor = VaultColors.Pink,
                contentColor = VaultColors.OnAccent,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_add_skill)) },
            )
        },
    ) { padding ->
        if (state.isLoading) return@Scaffold
        val review = state.review
        if (review.skills.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Psychology,
                title = stringResource(R.string.skills_empty_title),
                message = stringResource(R.string.skills_empty_body),
                accent = VaultColors.Pink,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                TerminalLabel(stringResource(R.string.skills_label), color = VaultColors.Pink)
                Text(
                    pluralStringResource(R.plurals.skills_count, review.skills.size, review.skills.size),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
            }
            item { LevelSummary(review.count(SkillLevel.STRONG), review.count(SkillLevel.PRACTICING), review.count(SkillLevel.BEGINNER)) }
            if (review.rusty.isNotEmpty()) {
                item { HighlightCard(stringResource(R.string.skills_rusty_title), stringResource(R.string.skills_rusty_body), VaultColors.Amber, review.rusty) { detail = it } }
            }
            if (review.newlyGained.isNotEmpty()) {
                item { HighlightCard(stringResource(R.string.skills_new_title), stringResource(R.string.skills_new_body), VaultColors.Green, review.newlyGained) { detail = it } }
            }
            if (review.insights.isNotEmpty()) {
                item { InsightsCard(review.insights) }
            }
            item { FilterRow(state.filter, viewModel::setFilter) }
            if (state.visible.isEmpty()) {
                item { Text(stringResource(R.string.skills_filter_empty), style = MaterialTheme.typography.bodyMedium, color = VaultColors.TextMuted) }
            }
            items(state.visible, key = { it.key }) { skill -> SkillRow(skill, state) { detail = skill } }
        }
    }

    detail?.let { s ->
        // Re-resolve so the dialog reflects updates (e.g. after "practised today").
        val live = state.review.skills.firstOrNull { it.key == s.key } ?: s
        SkillDetailDialog(
            skill = live,
            today = state.today,
            onDismiss = { detail = null },
            onPracticed = { viewModel.markPracticed(live) },
            onEdit = {
                detail = null
                draft = SkillDraft(live.manualId, live.name, live.firstSeen ?: state.today, live.lastPracticed, live.notes.orEmpty())
            },
            onDelete = { live.manualId?.let(viewModel::deleteManual); detail = null },
        )
    }
    draft?.let { d ->
        SkillEditDialog(
            initial = d,
            onDismiss = { draft = null },
            onSave = { viewModel.save(it); draft = null },
        )
    }
}

@Composable
private fun LevelSummary(strong: Int, practicing: Int, beginner: Int) {
    VaultCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(SkillLevel.STRONG to strong, SkillLevel.PRACTICING to practicing, SkillLevel.BEGINNER to beginner).forEach { (lvl, n) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(n.toString(), style = MaterialTheme.typography.headlineSmall, color = lvl.color())
                    Text(lvl.label(), style = MaterialTheme.typography.labelSmall, color = VaultColors.TextMuted)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HighlightCard(title: String, body: String, accent: Color, skills: List<Skill>, onOpen: (Skill) -> Unit) {
    VaultCard(Modifier.fillMaxWidth(), accent = accent) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TerminalLabel(title, color = accent)
            Text(body, style = MaterialTheme.typography.bodySmall, color = VaultColors.TextSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                skills.forEach { s ->
                    FilterChip(
                        selected = false,
                        onClick = { onOpen(s) },
                        label = { Text(s.name) },
                        colors = FilterChipDefaults.filterChipColors(labelColor = accent),
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightsCard(insights: List<SkillInsight>) {
    VaultCard(Modifier.fillMaxWidth(), accent = VaultColors.Pink) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TerminalLabel(stringResource(R.string.skills_insights), color = VaultColors.Pink)
            insights.forEach { i ->
                val text = when (i) {
                    is SkillInsight.Rusty -> pluralStringResource(R.plurals.insight_rusty, i.daysIdle.toInt(), i.skill.name, i.daysIdle.toInt())
                    is SkillInsight.CloseToNextLevel -> pluralStringResource(R.plurals.insight_close, i.missingScore, i.skill.name, i.missingScore, i.next.label())
                }
                Row {
                    Text("›", color = VaultColors.Pink, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(stringResource(R.string.skills_rules_note, SkillRules.POINTS_PER_PROBLEM, SkillRules.POINTS_PER_COURSE, SkillRules.POINTS_PER_COMPLETED_COURSE, SkillRules.POINTS_MANUAL), style = MaterialTheme.typography.labelSmall, color = VaultColors.TextMuted)
        }
    }

}

@Composable
private fun FilterRow(selected: SkillFilter, onSelect: (SkillFilter) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SkillFilter.entries.forEach { f ->
            FilterChip(
                selected = f == selected,
                onClick = { onSelect(f) },
                label = {
                    Text(
                        stringResource(
                            when (f) {
                                SkillFilter.ALL -> R.string.filter_all
                                SkillFilter.RUSTY -> R.string.filter_rusty
                                SkillFilter.NEW -> R.string.filter_new
                                SkillFilter.LEETCODE -> R.string.skill_source_leetcode
                                SkillFilter.COURSES -> R.string.filter_courses
                                SkillFilter.MANUAL -> R.string.skill_source_manual
                            },
                        ),
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = VaultColors.Pink.copy(alpha = 0.18f),
                    selectedLabelColor = VaultColors.Pink,
                ),
            )
        }
    }
}

@Composable
fun skillCountsText(skill: Skill): String = listOfNotNull(
    skill.problemCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.skill_problems, it, it) },
    skill.courseCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.skill_courses, it, it) },
).joinToString(" · ").ifEmpty { stringResource(R.string.skill_manual_only) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkillRow(skill: Skill, state: SkillsUiState, onClick: () -> Unit) {
    VaultCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(skill.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusChip(skill.level.label(), skill.level.color())
            }
            Text(skillCountsText(skill), style = MaterialTheme.typography.bodyMedium, color = VaultColors.TextSecondary)
            val last = skill.lastPracticed
            Text(
                listOfNotNull(
                    skill.firstSeen?.let { stringResource(R.string.skill_first_seen, it.formatMedium()) },
                    last?.let { stringResource(R.string.skill_last_practiced, relativeDays(ChronoUnit.DAYS.between(it, state.today))) },
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.labelSmall,
                color = VaultColors.TextMuted,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                skill.sources.sortedBy { it.ordinal }.forEach { StatusChip(it.label(), VaultColors.TextSecondary) }
                if (skill.isRusty) StatusChip(stringResource(R.string.filter_rusty), VaultColors.Amber)
                if (skill.isNew) StatusChip(stringResource(R.string.filter_new), VaultColors.Green)
            }
        }
    }
}

@Composable
fun relativeDays(days: Long): String = when {
    days <= 0L -> stringResource(R.string.today)
    days == 1L -> stringResource(R.string.yesterday)
    else -> pluralStringResource(R.plurals.days_ago, days.toInt(), days.toInt())
}

