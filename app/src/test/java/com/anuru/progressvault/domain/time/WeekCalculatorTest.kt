package com.anuru.progressvault.domain.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class WeekCalculatorTest {

    private fun calcAt(dateTime: LocalDateTime, zone: ZoneId = ZoneId.of("Asia/Kolkata")): WeekCalculator {
        val clock = Clock.fixed(dateTime.atZone(zone).toInstant(), zone)
        return WeekCalculator { clock }
    }

    @Test
    fun `week starts on Monday for every day of the week`() {
        val monday = LocalDate.of(2026, 11, 9)
        for (offset in 0L..6L) {
            val week = WeekRange.containing(monday.plusDays(offset))
            assertEquals(monday, week.start)
            assertEquals(LocalDate.of(2026, 11, 15), week.end)
        }
    }

    @Test
    fun `sunday late night is still the same week, monday midnight is the next`() {
        val sundayLate = calcAt(LocalDateTime.of(2026, 11, 15, 23, 59, 59))
        assertEquals(LocalDate.of(2026, 11, 9), sundayLate.currentWeek().start)

        val mondayMidnight = calcAt(LocalDateTime.of(2026, 11, 16, 0, 0))
        assertEquals(LocalDate.of(2026, 11, 16), mondayMidnight.currentWeek().start)
    }

    @Test
    fun `week boundaries use local timezone`() {
        // 2026-11-15 20:00 UTC is already Monday 01:30 in Kolkata (+05:30) but Sunday in New York.
        val instant = LocalDateTime.of(2026, 11, 15, 20, 0).atZone(ZoneId.of("UTC")).toInstant()
        val kolkata = WeekCalculator { Clock.fixed(instant, ZoneId.of("Asia/Kolkata")) }
        val newYork = WeekCalculator { Clock.fixed(instant, ZoneId.of("America/New_York")) }
        assertEquals(LocalDate.of(2026, 11, 16), kolkata.currentWeek().start)
        assertEquals(LocalDate.of(2026, 11, 9), newYork.currentWeek().start)
    }

    @Test
    fun `week instants span exactly Monday 00_00 to next Monday 00_00 local`() {
        val zone = ZoneId.of("Europe/London")
        val week = WeekRange(LocalDate.of(2026, 10, 19))
        assertEquals(LocalDateTime.of(2026, 10, 19, 0, 0).atZone(zone).toInstant(), week.startInstant(zone))
        assertEquals(LocalDateTime.of(2026, 10, 26, 0, 0).atZone(zone).toInstant(), week.endExclusiveInstant(zone))
    }

    @Test
    fun `week containing DST change still has 7 days`() {
        // Europe/London DST ends Sunday 2026-10-25, making that week 169 hours long.
        val week = WeekRange.containing(LocalDate.of(2026, 10, 25))
        assertEquals(7, week.days.size)
        assertEquals(DayOfWeek.SUNDAY, week.end.dayOfWeek)
    }

    @Test
    fun `days left counts today inclusively`() {
        assertEquals(7, calcAt(LocalDateTime.of(2026, 11, 9, 8, 0)).let { it.daysLeft(it.currentWeek()) })
        assertEquals(4, calcAt(LocalDateTime.of(2026, 11, 12, 8, 0)).let { it.daysLeft(it.currentWeek()) })
        assertEquals(1, calcAt(LocalDateTime.of(2026, 11, 15, 23, 0)).let { it.daysLeft(it.currentWeek()) })
    }

    @Test
    fun `fraction elapsed is 0 at start, half on Thursday noon, 1 for past weeks`() {
        val mondayStart = calcAt(LocalDateTime.of(2026, 11, 9, 0, 0))
        assertEquals(0.0, mondayStart.fractionElapsed(mondayStart.currentWeek()), 1e-9)

        val thursdayNoon = calcAt(LocalDateTime.of(2026, 11, 12, 12, 0))
        assertEquals(0.5, thursdayNoon.fractionElapsed(thursdayNoon.currentWeek()), 1e-9)

        assertEquals(1.0, thursdayNoon.fractionElapsed(thursdayNoon.currentWeek().previous()), 1e-9)
    }

    @Test
    fun `contains checks inclusive bounds`() {
        val week = WeekRange(LocalDate.of(2026, 11, 9))
        assertTrue(LocalDate.of(2026, 11, 9) in week)
        assertTrue(LocalDate.of(2026, 11, 15) in week)
        assertFalse(LocalDate.of(2026, 11, 16) in week)
        assertFalse(LocalDate.of(2026, 11, 8) in week)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `week range rejects non-Monday start`() {
        WeekRange(LocalDate.of(2026, 11, 10))
    }
}
