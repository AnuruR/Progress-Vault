package com.anuru.progressvault.ui.vault

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.anuru.progressvault.domain.model.AccentPalette
import com.anuru.progressvault.domain.model.BuiltInSectionKey
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anuru.progressvault.R
import com.anuru.progressvault.domain.history.ComparePreset
import com.anuru.progressvault.domain.history.Comparison
import com.anuru.progressvault.domain.history.Milestone
import com.anuru.progressvault.domain.history.MilestoneKind
import com.anuru.progressvault.domain.history.MonthlyRecap
import com.anuru.progressvault.domain.history.NextGoal
import com.anuru.progressvault.domain.history.Records
import com.anuru.progressvault.domain.history.SectionInfo
import com.anuru.progressvault.domain.history.Totals
import com.anuru.progressvault.ui.components.DateField
import com.anuru.progressvault.ui.components.GlowProgressBar
import com.anuru.progressvault.ui.components.TerminalLabel
import com.anuru.progressvault.ui.components.VaultCard
import com.anuru.progressvault.ui.components.formatAmount
import com.anuru.progressvault.ui.components.formatDayMonth
import com.anuru.progressvault.ui.components.formatMedium
import com.anuru.progressvault.ui.theme.VaultColors
import com.anuru.progressvault.ui.theme.accentColor
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// LazyColumn item indices (header, jump row, lifetime, records, heatmap, compare, trends, milestones, recap).
private const val ITEM_HEATMAP = 4
private const val ITEM_COMPARE = 5
private const val ITEM_TRENDS = 6
private const val ITEM_MILESTONES = 7
private const val ITEM_RECAP = 8

private val monthFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy")

@Composable
fun VaultScreen(
    recapLink: java.time.YearMonth? = null,
    onRecapLinkHandled: () -> Unit = {},
    viewModel: VaultViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.isLoading) return
    val lifetime = state.lifetime ?: return
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Monthly recap notification: open that month and scroll to the card.
    androidx.compose.runtime.LaunchedEffect(recapLink) {
        if (recapLink != null) {
            viewModel.setRecapMonth(recapLink)
            listState.animateScrollToItem(ITEM_RECAP)
            onRecapLinkHandled()
        }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            TerminalLabel(stringResource(R.string.vault_label), color = VaultColors.Purple)
            Text(stringResource(R.string.vault_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            state.firstDay?.let {
                Text(stringResource(R.string.vault_since, it.formatMedium()), style = MaterialTheme.typography.bodyMedium, color = VaultColors.TextSecondary)
            }
        }
        item {
            // Quick jumps: the page is long, and Compare sits below the fold.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(
                    R.string.vault_jump_compare to ITEM_COMPARE,
                    R.string.vault_jump_heatmap to ITEM_HEATMAP,
                    R.string.vault_jump_trends to ITEM_TRENDS,
                    R.string.vault_jump_milestones to ITEM_MILESTONES,
                    R.string.vault_jump_recap to ITEM_RECAP,
                ).forEach { (label, index) ->
                    androidx.compose.material3.AssistChip(
                        onClick = { scope.launch { listState.animateScrollToItem(index) } },
                        label = { Text(stringResource(label)) },
                        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(labelColor = VaultColors.Purple),
                    )
                }
            }
        }
        item { LifetimeGrid(lifetime, state.weeksTracked, state.sections) }
        item { state.records?.let { RecordsCard(it) } }
        item {
            Section(stringResource(R.string.vault_heatmap)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.setHeatYear(state.heatYear - 1) }, enabled = state.heatYear > state.heatYears.first) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.cd_previous_year))
                    }
                    Text(state.heatYear.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    IconButton(onClick = { viewModel.setHeatYear(state.heatYear + 1) }, enabled = state.heatYear < state.heatYears.last) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.cd_next_year))
                    }
                }
                SectionChips(state.chartSections, state.controls.heatSection, viewModel::setHeatSection)
                val heatSec = state.sections.firstOrNull { it.id == state.controls.heatSection }
                val accent = heatSec?.let { accentColor(it.accentColor) } ?: VaultColors.Green
                // GitHub (and the combined view) use github.com's own contribution greens.
                val levels = if (heatSec == null || heatSec.builtInKey == BuiltInSectionKey.GITHUB.key) AccentPalette.githubLevels.map { Color(it) } else null
                YearHeatmap(state.heatYear, state.heatmap, accent, state.today, levels)
            }
        }
        item {
            Section(stringResource(R.string.vault_compare)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.availablePresets.forEach { p ->
                        FilterChip(
                            selected = state.controls.preset == p,
                            onClick = { viewModel.setPreset(p) },
                            label = { Text(presetLabel(p)) },
                            colors = chipColors(),
                        )
                    }
                }
                DateField(
                    label = stringResource(R.string.vault_compare_then),
                    date = state.thenDate,
                    onDateChange = viewModel::setCustomThen,
                )
                state.comparison?.let { CompareTable(it) }
            }
        }
        item {
            Section(stringResource(R.string.vault_trends)) {
                Text(stringResource(R.string.vault_trend_completion), style = MaterialTheme.typography.labelLarge, color = VaultColors.TextSecondary)
                if (state.weekCompletion.size >= 2) CompletionTrendChart(state.weekCompletion)
                else Text(stringResource(R.string.vault_trend_need_weeks), style = MaterialTheme.typography.bodySmall, color = VaultColors.TextMuted)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.vault_trend_output), style = MaterialTheme.typography.labelLarge, color = VaultColors.TextSecondary, modifier = Modifier.weight(1f))
                    TrendRange.entries.forEach { r ->
                        FilterChip(
                            selected = state.controls.trendRange == r,
                            onClick = { viewModel.setTrendRange(r) },
                            label = { Text(stringResource(if (r == TrendRange.WEEKS) R.string.vault_weeks else R.string.vault_months)) },
                            colors = chipColors(),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                SectionChips(state.chartSections, state.controls.trendSection, viewModel::setTrendSection)
                val sec = state.sections.firstOrNull { it.id == state.controls.trendSection }
                if (sec == null) {
                    Text(stringResource(R.string.vault_trend_all_note), style = MaterialTheme.typography.labelSmall, color = VaultColors.TextMuted)
                }
                OutputChart(state.sectionSeries, state.controls.trendRange == TrendRange.MONTHS, sec?.let { accentColor(it.accentColor) } ?: VaultColors.Purple)
            }
        }
        item { MilestonesCard(state.milestones, state.nextGoals) }
        item {
            state.recap?.let { recap ->
                RecapCard(
                    recap = recap,
                    canPrev = state.recapMonths.lastOrNull()?.let { recap.month.isAfter(it) } == true,
                    canNext = state.recapMonths.firstOrNull()?.let { recap.month.isBefore(it) } == true,
                    onPrev = { viewModel.setRecapMonth(recap.month.minusMonths(1)) },
                    onNext = { viewModel.setRecapMonth(recap.month.plusMonths(1)) },
                )
            }
        }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = VaultColors.Purple.copy(alpha = 0.18f),
    selectedLabelColor = VaultColors.Purple,
)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    VaultCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TerminalLabel(title, color = VaultColors.Purple)
            content()
        }
    }
}

@Composable
private fun SectionChips(sections: List<SectionInfo>, selected: Long?, onSelect: (Long?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(stringResource(R.string.filter_all)) }, colors = chipColors())
        sections.forEach { s ->
            FilterChip(selected = selected == s.id, onClick = { onSelect(s.id) }, label = { Text(s.name) }, colors = chipColors())
        }
    }
}

@Composable
private fun presetLabel(p: ComparePreset) = stringResource(
    when (p) {
        ComparePreset.ONE_MONTH -> R.string.preset_1m
        ComparePreset.THREE_MONTHS -> R.string.preset_3m
        ComparePreset.SIX_MONTHS -> R.string.preset_6m
        ComparePreset.SEMESTER_START -> R.string.preset_semester
        ComparePreset.FIRST_DAY -> R.string.preset_first_day
    },
)

@Composable
private fun Counter(value: Int, label: String, color: Color, modifier: Modifier = Modifier, suffix: String = "") {
    val animated by animateIntAsState(value, tween(900), label = "counter")
    VaultCard(modifier, contentPadding = PaddingValues(14.dp)) {
        Column(Modifier.clearAndSetSemantics { contentDescription = "$value$suffix $label" }) {
            Text("$animated$suffix", style = MaterialTheme.typography.headlineMedium, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = VaultColors.TextMuted)
        }
    }
}

@Composable
private fun LifetimeGrid(t: Totals, weeks: Int, sections: List<SectionInfo>) {
    val tiles = listOf(
        Triple(t.problems, stringResource(R.string.lifetime_problems), VaultColors.Amber),
        Triple(t.commits, stringResource(R.string.lifetime_commits), Color(AccentPalette.GITHUB_GREEN)),
        Triple(t.posts, stringResource(R.string.lifetime_posts), VaultColors.Blue),
        Triple(t.lessons, stringResource(R.string.lifetime_lessons), VaultColors.Cyan),
        Triple(t.studyHours.roundToInt(), stringResource(R.string.lifetime_hours), VaultColors.Purple),
        Triple(t.skills, stringResource(R.string.lifetime_skills), VaultColors.Pink),
        Triple(t.coursesCompleted, stringResource(R.string.lifetime_courses), VaultColors.Cyan),
        Triple(weeks, stringResource(R.string.lifetime_weeks), VaultColors.Cyan),
    ) + t.custom.mapNotNull { (id, total) ->
        sections.firstOrNull { it.id == id }?.let { Triple(total.roundToInt(), "${it.name} · ${it.unit}", accentColor(it.accentColor)) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TerminalLabel(stringResource(R.string.vault_lifetime))
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (v, l, c) -> Counter(v, l, c, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RecordsCard(r: Records) {
    Section(stringResource(R.string.vault_records)) {
        RecordRow(
            stringResource(R.string.record_best_week),
            r.bestWeek?.let { stringResource(R.string.record_best_week_value, (it.completion * 100).roundToInt(), it.weekStart.formatMedium()) },
        )
        RecordRow(stringResource(R.string.record_day_streak), pluralStringResource(R.plurals.days, r.longestDayStreak, r.longestDayStreak).takeIf { r.longestDayStreak > 0 })
        RecordRow(stringResource(R.string.record_current_streak), pluralStringResource(R.plurals.days, r.currentDayStreak, r.currentDayStreak).takeIf { r.currentDayStreak > 0 })
        RecordRow(stringResource(R.string.record_perfect_streak), pluralStringResource(R.plurals.weeks, r.longestPerfectStreak, r.longestPerfectStreak).takeIf { r.longestPerfectStreak > 0 })
        RecordRow(
            stringResource(R.string.record_best_month),
            r.mostActiveMonth?.let { stringResource(R.string.record_best_month_value, it.format(monthFormat), formatAmount(r.mostActiveMonthPoints)) },
        )
    }
}

@Composable
private fun RecordRow(label: String, value: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = VaultColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(value ?: stringResource(R.string.record_none), style = MaterialTheme.typography.titleSmall, color = if (value != null) VaultColors.TextPrimary else VaultColors.TextMuted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompareTable(c: Comparison) {
    Row {
        Spacer(Modifier.weight(1.4f))
        Text(c.then.date.plusDays(1).formatDayMonth(), style = MaterialTheme.typography.labelSmall, color = VaultColors.TextMuted, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Text(stringResource(R.string.vault_now), style = MaterialTheme.typography.labelSmall, color = VaultColors.TextMuted, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Text(stringResource(R.string.vault_change), style = MaterialTheme.typography.labelSmall, color = VaultColors.TextMuted, modifier = Modifier.weight(0.9f), textAlign = TextAlign.End)
    }
    CompareRow(stringResource(R.string.lifetime_problems), c.then.problems.toDouble(), c.now.problems.toDouble())
    CompareRow(stringResource(R.string.lifetime_commits), c.then.commits.toDouble(), c.now.commits.toDouble())
    CompareRow(stringResource(R.string.lifetime_posts), c.then.posts.toDouble(), c.now.posts.toDouble())
    CompareRow(stringResource(R.string.lifetime_lessons), c.then.lessons.toDouble(), c.now.lessons.toDouble())
    CompareRow(stringResource(R.string.lifetime_hours), c.then.studyHours, c.now.studyHours)
    CompareRow(stringResource(R.string.lifetime_courses), c.then.coursesCompleted.toDouble(), c.now.coursesCompleted.toDouble())
    CompareRow(stringResource(R.string.lifetime_skills), c.then.skills.toDouble(), c.now.skills.toDouble())

    val summary = listOfNotNull(
        c.problems.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.diff_problems, it, it) },
        c.commits.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.diff_commits, it, it) },
        c.posts.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.diff_posts, it, it) },
        c.lessons.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.diff_lessons, it, it) },
        c.skills.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.diff_skills, it, it) },
        c.coursesCompleted.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.diff_courses, it, it) },
    )
    if (summary.isNotEmpty()) {
        Text(summary.joinToString(", "), style = MaterialTheme.typography.titleSmall, color = VaultColors.Green)
    }
    if (c.newSkills.isNotEmpty()) {
        TerminalLabel(stringResource(R.string.vault_new_skills))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            c.newSkills.forEach { com.anuru.progressvault.ui.components.StatusChip(it, VaultColors.Pink) }
        }
    }
    val lcThen = c.then.leetCode
    val lcNow = c.now.leetCode
    if (lcNow != null) {
        TerminalLabel(stringResource(R.string.vault_lc_breakdown))
        listOf(
            Triple(stringResource(R.string.lc_easy), lcThen?.easy, lcNow.easy),
            Triple(stringResource(R.string.lc_medium), lcThen?.medium, lcNow.medium),
            Triple(stringResource(R.string.lc_hard), lcThen?.hard, lcNow.hard),
        ).forEach { (label, a, b) ->
            if (a != null) CompareRow(label, a.toDouble(), b.toDouble())
            else Row {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.4f))
                Text(stringResource(R.string.vault_no_data), style = MaterialTheme.typography.bodySmall, color = VaultColors.TextMuted, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Text(b.toString(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Spacer(Modifier.weight(0.9f))
            }
        }
    }
}

@Composable
private fun CompareRow(label: String, then: Double, now: Double) {
    val delta = now - then
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.4f))
        Text(formatAmount(then), style = MaterialTheme.typography.bodyMedium, color = VaultColors.TextSecondary, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Text(formatAmount(now), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Text(
            if (delta > 0) "+${formatAmount(delta)}" else if (delta < 0) formatAmount(delta) else "·",
            style = MaterialTheme.typography.titleSmall,
            color = if (delta > 0) VaultColors.Green else VaultColors.TextMuted,
            modifier = Modifier.weight(0.9f),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
fun milestoneText(m: Milestone): String = when (m.kind) {
    MilestoneKind.PROBLEMS -> pluralStringResource(R.plurals.ms_problems, m.threshold, m.threshold)
    MilestoneKind.COMMITS -> pluralStringResource(R.plurals.ms_commits, m.threshold, m.threshold)
    MilestoneKind.POSTS -> pluralStringResource(R.plurals.ms_posts, m.threshold, m.threshold)
    MilestoneKind.LESSONS -> pluralStringResource(R.plurals.ms_lessons, m.threshold, m.threshold)
    MilestoneKind.STUDY_HOURS -> pluralStringResource(R.plurals.ms_hours, m.threshold, m.threshold)
    MilestoneKind.COURSES -> pluralStringResource(R.plurals.ms_courses, m.threshold, m.threshold)
    MilestoneKind.SKILLS -> pluralStringResource(R.plurals.ms_skills, m.threshold, m.threshold)
    MilestoneKind.PERFECT_WEEK -> stringResource(R.string.ms_perfect_week)
    MilestoneKind.PERFECT_STREAK -> pluralStringResource(R.plurals.ms_perfect_streak, m.threshold, m.threshold)
    MilestoneKind.SEMESTER -> stringResource(R.string.ms_semester, m.label.orEmpty())
}

@Composable
private fun goalText(g: NextGoal): String = milestoneText(Milestone(g.kind, g.target, java.time.LocalDate.MIN))

@Composable
private fun MilestonesCard(milestones: List<Milestone>, goals: List<NextGoal>) {
    Section(stringResource(R.string.vault_milestones)) {
        if (goals.isNotEmpty()) {
            Text(stringResource(R.string.vault_next_up), style = MaterialTheme.typography.labelLarge, color = VaultColors.TextSecondary)
            goals.forEach { g ->
                Column {
                    Row {
                        Text(goalText(g), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("${g.current}/${g.target}", style = MaterialTheme.typography.labelLarge, color = VaultColors.Purple)
                    }
                    Spacer(Modifier.height(4.dp))
                    GlowProgressBar(g.current.toFloat() / g.target, VaultColors.Purple)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        if (milestones.isEmpty()) {
            Text(stringResource(R.string.vault_no_milestones), style = MaterialTheme.typography.bodyMedium, color = VaultColors.TextMuted)
        }
        milestones.forEachIndexed { i, m ->
            Row(verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(24.dp)) {
                    Box(Modifier.size(12.dp).background(VaultColors.Amber, CircleShape))
                    if (i != milestones.lastIndex) Box(Modifier.width(2.dp).height(34.dp).background(VaultColors.Border))
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.EmojiEvents, null, tint = VaultColors.Amber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(milestoneText(m), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(m.date.formatMedium(), style = MaterialTheme.typography.labelSmall, color = VaultColors.TextMuted)
                }
            }
        }
    }
}

@Composable
fun RecapCard(recap: MonthlyRecap, canPrev: Boolean, canNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    VaultCard(Modifier.fillMaxWidth(), accent = VaultColors.Amber) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TerminalLabel(stringResource(R.string.vault_recap), color = VaultColors.Amber, modifier = Modifier.weight(1f))
                IconButton(onClick = onPrev, enabled = canPrev) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.cd_previous_month)) }
                IconButton(onClick = onNext, enabled = canNext) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.cd_next_month)) }
            }
            Text(recap.month.format(monthFormat), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            if (recap.isEmpty) {
                Text(stringResource(R.string.recap_empty), style = MaterialTheme.typography.bodyMedium, color = VaultColors.TextMuted)
            } else {
                Text(recapSentence(recap), style = MaterialTheme.typography.bodyLarge)
                Text(pluralStringResource(R.plurals.recap_active_days, recap.activeDays, recap.activeDays), style = MaterialTheme.typography.bodySmall, color = VaultColors.TextSecondary)
                recap.milestones.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.EmojiEvents, null, tint = VaultColors.Amber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(milestoneText(m), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/** "9 problems, 4 posts, 1 course finished, best week 92%". */
@Composable
fun recapSentence(r: MonthlyRecap): String = listOfNotNull(
    r.problems.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.recap_problems, it, it) },
    r.commits.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.recap_commits, it, it) },
    r.posts.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.recap_posts, it, it) },
    r.lessons.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.recap_lessons, it, it) },
    r.studyHours.takeIf { it > 0 }?.let { stringResource(R.string.recap_hours, formatAmount(it)) },
    r.coursesFinished.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.recap_courses, it, it) },
    r.bestWeek?.takeIf { it.completion > 0 }?.let { stringResource(R.string.recap_best_week, (it.completion * 100).roundToInt()) },
).joinToString(", ")

