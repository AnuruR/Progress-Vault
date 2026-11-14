package com.anuru.progressvault.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anuru.progressvault.data.repository.HistoryRepository
import com.anuru.progressvault.domain.history.ComparePreset
import com.anuru.progressvault.domain.history.Comparison
import com.anuru.progressvault.domain.history.HistoryCalculator
import com.anuru.progressvault.domain.history.HistoryData
import com.anuru.progressvault.domain.history.Milestone
import com.anuru.progressvault.domain.history.MonthlyRecap
import com.anuru.progressvault.domain.history.NextGoal
import com.anuru.progressvault.domain.history.Records
import com.anuru.progressvault.domain.history.SectionInfo
import com.anuru.progressvault.domain.history.SeriesPoint
import com.anuru.progressvault.domain.history.Totals
import com.anuru.progressvault.domain.history.WeekResult
import com.anuru.progressvault.domain.model.BuiltInSectionKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

enum class TrendRange { WEEKS, MONTHS }

/** User choices on the Vault screen. */
data class VaultControls(
    val heatYear: Int? = null,
    val heatSection: Long? = null,
    val preset: ComparePreset? = ComparePreset.ONE_MONTH,
    val customThen: LocalDate? = null,
    val trendSection: Long? = null,
    val trendRange: TrendRange = TrendRange.WEEKS,
    val recapMonth: YearMonth? = null,
)

data class VaultUiState(
    val isLoading: Boolean = true,
    val controls: VaultControls = VaultControls(),
    val today: LocalDate = LocalDate.now(),
    val firstDay: LocalDate? = null,
    val lifetime: Totals? = null,
    val weeksTracked: Int = 0,
    val records: Records? = null,
    val sections: List<SectionInfo> = emptyList(),
    val heatYear: Int = LocalDate.now().year,
    val heatYears: IntRange = heatYear..heatYear,
    val heatmap: Map<LocalDate, Double> = emptyMap(),
    val thenDate: LocalDate? = null,
    val availablePresets: List<ComparePreset> = emptyList(),
    val comparison: Comparison? = null,
    val weekCompletion: List<WeekResult> = emptyList(),
    val sectionSeries: List<SeriesPoint> = emptyList(),
    val milestones: List<Milestone> = emptyList(),
    val nextGoals: List<NextGoal> = emptyList(),
    val recap: MonthlyRecap? = null,
    val recapMonths: List<YearMonth> = emptyList(),
) {
    /** Sections that produce countable history (Skills is derived, so excluded). */
    val chartSections: List<SectionInfo> get() = sections.filter { it.builtInKey != BuiltInSectionKey.SKILLS.key }
}

@HiltViewModel
class VaultViewModel @Inject constructor(repository: HistoryRepository) : ViewModel() {
    private val controls = MutableStateFlow(VaultControls())

    val state: StateFlow<VaultUiState> = combine(repository.observe(), controls) { data, c -> build(data, c) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultUiState())

    private fun build(data: HistoryData, c: VaultControls): VaultUiState {
        val today = data.today
        val firstYear = (data.firstDay ?: today).year
        val year = (c.heatYear ?: today.year).coerceIn(firstYear, today.year)
        val presets = ComparePreset.entries.filter { HistoryCalculator.presetDate(data, it) != null }
        val then = (c.customThen ?: c.preset?.let { HistoryCalculator.presetDate(data, it) } ?: today.minusMonths(1))
            .let { if (it.isAfter(today)) today else it }
        val firstMonth = YearMonth.from(data.firstDay ?: today)
        val months = generateSequence(YearMonth.from(today)) { it.minusMonths(1) }.takeWhile { !it.isBefore(firstMonth) }.toList()
        val recapMonth = c.recapMonth ?: months.getOrElse(1) { months.first() } // default: last full month
        return VaultUiState(
            isLoading = false,
            controls = c,
            today = today,
            firstDay = data.firstDay,
            lifetime = HistoryCalculator.totalsAt(data, today),
            weeksTracked = HistoryCalculator.weeksTracked(data),
            records = HistoryCalculator.records(data),
            sections = data.sections,
            heatYear = year,
            heatYears = firstYear..today.year,
            heatmap = HistoryCalculator.heatmap(data, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), c.heatSection),
            thenDate = then,
            availablePresets = presets,
            comparison = HistoryCalculator.compare(data, then),
            weekCompletion = data.weeks.takeLast(26),
            sectionSeries = when (c.trendRange) {
                TrendRange.WEEKS -> HistoryCalculator.weeklySeries(data, c.trendSection, 12)
                TrendRange.MONTHS -> HistoryCalculator.monthlySeries(data, c.trendSection, 12)
            },
            milestones = HistoryCalculator.milestones(data).reversed(),
            nextGoals = HistoryCalculator.nextGoals(data).take(3),
            recap = HistoryCalculator.monthlyRecap(data, recapMonth),
            recapMonths = months,
        )
    }

    fun setHeatYear(y: Int) = controls.update { it.copy(heatYear = y) }
    fun setHeatSection(id: Long?) = controls.update { it.copy(heatSection = id) }
    fun setPreset(p: ComparePreset) = controls.update { it.copy(preset = p, customThen = null) }
    fun setCustomThen(d: LocalDate) = controls.update { it.copy(customThen = d, preset = null) }
    fun setTrendSection(id: Long?) = controls.update { it.copy(trendSection = id) }
    fun setTrendRange(r: TrendRange) = controls.update { it.copy(trendRange = r) }
    fun setRecapMonth(m: YearMonth) = controls.update { it.copy(recapMonth = m) }
}
