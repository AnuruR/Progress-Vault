package com.anuru.progressvault.ui.vault

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anuru.progressvault.R
import com.anuru.progressvault.domain.history.SeriesPoint
import com.anuru.progressvault.domain.history.WeekResult
import com.anuru.progressvault.ui.components.formatAmount
import com.anuru.progressvault.ui.components.formatDayMonth
import com.anuru.progressvault.ui.components.formatMedium
import com.anuru.progressvault.ui.theme.VaultColors
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnSeries
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle as JTextStyle
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

private val CELL = 12.dp
private val GAP = 3.dp

/** GitHub-style year heatmap: one column per week (Mon at top), scrollable, tap a day for details. */
@Composable
fun YearHeatmap(year: Int, values: Map<LocalDate, Double>, accent: Color, today: LocalDate, levels: List<Color>? = null) {
    /** Colour for level 1–4: explicit palette (e.g. github.com greens) or the accent at rising opacity. */
    fun levelColor(l: Int): Color = levels?.getOrNull(l - 1) ?: accent.copy(alpha = 0.2f + 0.2f * l)
    val first = LocalDate.of(year, 1, 1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val last = LocalDate.of(year, 12, 31)
    val weeks = ((java.time.temporal.ChronoUnit.DAYS.between(first, last) / 7) + 1).toInt()
    // Quartile buckets over non-zero days so a few huge days don't wash out the rest.
    val sorted = values.values.sorted()
    fun level(v: Double): Int = when {
        v <= 0 || sorted.isEmpty() -> 0
        v <= sorted[(sorted.size * 0.25).toInt().coerceAtMost(sorted.lastIndex)] -> 1
        v <= sorted[(sorted.size * 0.5).toInt().coerceAtMost(sorted.lastIndex)] -> 2
        v <= sorted[(sorted.size * 0.75).toInt().coerceAtMost(sorted.lastIndex)] -> 3
        else -> 4
    }
    var selected by remember(year) { mutableStateOf<LocalDate?>(null) }
    val density = LocalDensity.current
    val cellPx = with(density) { CELL.toPx() }
    val gapPx = with(density) { GAP.toPx() }
    val scroll = rememberScrollState()
    LaunchedEffect(year, scroll.maxValue) {
        // Show the most recent weeks first for the current year.
        if (year == today.year) scroll.scrollTo(scroll.maxValue) else scroll.scrollTo(0)
    }
    val locale = LocalConfiguration.current.locales[0]
    val activeDays = values.size
    val summary = pluralStringResource(R.plurals.heatmap_summary, activeDays, activeDays, year)

    Column(Modifier.semantics { contentDescription = summary }) {
        Row(Modifier.horizontalScroll(scroll)) {
            Column {
                // Month labels, positioned over the first week column of each month (may overflow into later columns).
                Box(Modifier.width((CELL + GAP) * weeks).height(16.dp)) {
                    var lastMonth = -1
                    repeat(weeks) { w ->
                        val sunday = first.plusWeeks(w.toLong()).plusDays(6)
                        if (sunday.year == year && sunday.monthValue != lastMonth) {
                            lastMonth = sunday.monthValue
                            Text(
                                sunday.month.getDisplayName(JTextStyle.SHORT, locale),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = VaultColors.TextMuted,
                                softWrap = false,
                                modifier = Modifier.offset(x = (CELL + GAP) * w),
                            )
                        }
                    }
                }
                Canvas(
                    Modifier
                        .size(width = (CELL + GAP) * weeks, height = (CELL + GAP) * 7)
                        .pointerInput(year) {
                            detectTapGestures { pos ->
                                val col = (pos.x / (cellPx + gapPx)).toInt()
                                val row = (pos.y / (cellPx + gapPx)).toInt().coerceIn(0, 6)
                                val date = first.plusWeeks(col.toLong()).plusDays(row.toLong())
                                selected = date.takeIf { it.year == year }
                            }
                        },
                ) {
                    for (w in 0 until weeks) for (d in 0 until 7) {
                        val date = first.plusWeeks(w.toLong()).plusDays(d.toLong())
                        if (date.year != year) continue
                        val lvl = if (date.isAfter(today)) -1 else level(values[date] ?: 0.0)
                        val color = when (lvl) {
                            -1 -> VaultColors.Background
                            0 -> VaultColors.Border
                            else -> levelColor(lvl)
                        }
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(w * (cellPx + gapPx), d * (cellPx + gapPx)),
                            size = Size(cellPx, cellPx),
                            cornerRadius = CornerRadius(cellPx * 0.2f),
                        )
                        if (date == selected) {
                            drawRoundRect(
                                color = VaultColors.TextPrimary,
                                topLeft = Offset(w * (cellPx + gapPx), d * (cellPx + gapPx)),
                                size = Size(cellPx, cellPx),
                                cornerRadius = CornerRadius(cellPx * 0.2f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                selected?.let { d -> stringResource(R.string.heatmap_selected, d.formatMedium(), formatAmount(values[d] ?: 0.0)) }
                    ?: summary,
                style = MaterialTheme.typography.labelSmall,
                color = VaultColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.heatmap_less), style = MaterialTheme.typography.labelSmall, color = VaultColors.TextMuted)
            Spacer(Modifier.width(4.dp))
            (0..4).forEach { l ->
                Spacer(
                    Modifier.size(10.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (l == 0) VaultColors.Border else levelColor(l)),
                )
                Spacer(Modifier.width(2.dp))
            }
            Text(stringResource(R.string.heatmap_more), style = MaterialTheme.typography.labelSmall, color = VaultColors.TextMuted)
        }
    }
}


@Composable
private fun axisLabel() = rememberTextComponent(TextStyle(color = VaultColors.TextMuted, fontSize = 10.sp))

/** Weekly dial completion %, as a line. */
@Composable
fun CompletionTrendChart(weeks: List<WeekResult>, modifier: Modifier = Modifier) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(weeks) {
        producer.runTransaction { lineSeries { series(weeks.map { (it.completion * 100).roundToInt().coerceAtMost(100) }) } }
    }
    val labels = weeks.map { it.weekStart.formatDayMonth() }
    val desc = stringResource(
        R.string.cd_completion_chart,
        weeks.size,
        weeks.lastOrNull()?.let { (it.completion * 100).roundToInt() } ?: 0,
    )
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(Fill(VaultColors.Cyan))),
                ),
            ),
            startAxis = VerticalAxis.rememberStart(label = axisLabel(), valueFormatter = CartesianValueFormatter { _, v, _ -> "${v.roundToInt()}%" }),
            bottomAxis = HorizontalAxis.rememberBottom(
                label = axisLabel(),
                valueFormatter = CartesianValueFormatter { _, x, _ -> labels.getOrNull(x.roundToInt()).orEmpty() },
            ),
        ),
        modelProducer = producer,
        modifier = modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = desc },
    )
}

/** Per-section (or all) output per week/month, as columns. */
@Composable
fun OutputChart(points: List<SeriesPoint>, monthly: Boolean, accent: Color, modifier: Modifier = Modifier) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points) { producer.runTransaction { columnSeries { series(points.map { it.value }) } } }
    val locale = LocalConfiguration.current.locales[0]
    val labels = points.map { if (monthly) it.start.month.getDisplayName(JTextStyle.SHORT, locale) else it.start.formatDayMonth() }
    val total = points.sumOf { it.value }
    val desc = stringResource(R.string.cd_output_chart, points.size, formatAmount(total))
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    rememberLineComponent(fill = Fill(accent), thickness = 10.dp, shape = RoundedCornerShape(3.dp)),
                ),
            ),
            startAxis = VerticalAxis.rememberStart(label = axisLabel()),
            bottomAxis = HorizontalAxis.rememberBottom(
                label = axisLabel(),
                valueFormatter = CartesianValueFormatter { _, x, _ -> labels.getOrNull(x.roundToInt()).orEmpty() },
            ),
        ),
        modelProducer = producer,
        modifier = modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = desc },
    )
}

