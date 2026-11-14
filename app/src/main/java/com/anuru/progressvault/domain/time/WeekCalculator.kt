package com.anuru.progressvault.domain.time

import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

/** Supplies the current clock. Re-read on every call so timezone changes are picked up. */
fun interface TimeProvider {
    fun clock(): Clock
}

class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun clock(): Clock = Clock.systemDefaultZone()
}

/** A Monday-to-Sunday week, identified by its Monday. */
data class WeekRange(val start: LocalDate) {
    init {
        require(start.dayOfWeek == DayOfWeek.MONDAY) { "Week must start on Monday, was $start" }
    }

    /** Sunday (inclusive). */
    val end: LocalDate get() = start.plusDays(6)
    val days: List<LocalDate> get() = (0L..6L).map(start::plusDays)

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    fun previous(): WeekRange = WeekRange(start.minusWeeks(1))
    fun next(): WeekRange = WeekRange(start.plusWeeks(1))

    /** Monday 00:00 in [zone]. */
    fun startInstant(zone: ZoneId): Instant = start.atStartOfDay(zone).toInstant()

    /** Next Monday 00:00 in [zone] (exclusive end; covers Sunday 23:59:59.999). */
    fun endExclusiveInstant(zone: ZoneId): Instant = start.plusWeeks(1).atStartOfDay(zone).toInstant()

    companion object {
        fun containing(date: LocalDate): WeekRange =
            WeekRange(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
    }
}

/**
 * Week rules: weeks start Monday 00:00 and end Sunday 23:59 in the device's local timezone.
 */
@Singleton
class WeekCalculator @Inject constructor(private val timeProvider: TimeProvider) {

    val zone: ZoneId get() = timeProvider.clock().zone

    fun now(): Instant = timeProvider.clock().instant()

    fun today(): LocalDate = LocalDate.now(timeProvider.clock())

    fun currentWeek(): WeekRange = WeekRange.containing(today())

    fun weekOf(instant: Instant): WeekRange =
        WeekRange.containing(instant.atZone(zone).toLocalDate())

    /** Fraction (0..1) of [week] that has elapsed at the current instant. */
    fun fractionElapsed(week: WeekRange): Double {
        val start = week.startInstant(zone)
        val end = week.endExclusiveInstant(zone)
        val now = now()
        if (!now.isAfter(start)) return 0.0
        if (!now.isBefore(end)) return 1.0
        val total = Duration.between(start, end).toMillis().toDouble()
        return Duration.between(start, now).toMillis() / total
    }

    /** Whole days remaining in [week] including today (7 on Monday, 1 on Sunday, 0 if past). */
    fun daysLeft(week: WeekRange): Int {
        val today = today()
        return when {
            today.isBefore(week.start) -> 7
            today.isAfter(week.end) -> 0
            else -> (ChronoUnit.DAYS.between(today, week.end) + 1).toInt()
        }
    }
}
