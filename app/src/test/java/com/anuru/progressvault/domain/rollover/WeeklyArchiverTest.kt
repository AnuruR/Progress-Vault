package com.anuru.progressvault.domain.rollover

import com.anuru.progressvault.domain.progress.SectionProgress
import com.anuru.progressvault.domain.progress.WeekProgress
import com.anuru.progressvault.domain.time.WeekRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class WeeklyArchiverTest {
    private val current = WeekRange(LocalDate.of(2026, 11, 16))

    @Test fun `archives every finished week since first use, oldest first`() {
        val weeks = WeeklyArchiver.weeksToArchive(LocalDate.of(2026, 10, 28), current, emptySet())
        assertEquals(listOf("2026-10-26", "2026-11-02", "2026-11-09").map(LocalDate::parse), weeks.map { it.start })
    }

    @Test fun `skips weeks that already have a snapshot and never the current week`() {
        val weeks = WeeklyArchiver.weeksToArchive(LocalDate.of(2026, 11, 2), current, setOf(LocalDate.of(2026, 11, 2)))
        assertEquals(listOf(LocalDate.of(2026, 11, 9)), weeks.map { it.start })
    }

    @Test fun `nothing to archive in the first week`() {
        assertTrue(WeeklyArchiver.weeksToArchive(LocalDate.of(2026, 11, 17), current, emptySet()).isEmpty())
    }

    @Test fun `catch-up is bounded`() {
        val weeks = WeeklyArchiver.weeksToArchive(LocalDate.of(2000, 1, 1), current, emptySet())
        assertEquals(WeeklyArchiver.MAX_CATCH_UP_WEEKS, weeks.size)
    }

    @Test fun `snapshot copies section names targets and dial flags`() {
        val week = WeekRange(LocalDate.of(2026, 11, 9))
        val sp = SectionProgress(3, null, "LeetCode", "code", 0xFF, "problems", 2.0, 1.0, 0.5, 1.0, true, true, false)
        val (snap, rows) = WeeklyArchiver.snapshot(WeekProgress(week, listOf(sp), emptyList(), 0.5, false), Instant.EPOCH)
        assertEquals(week.start, snap.weekStart)
        assertEquals(0.5, snap.overallCompletion, 1e-9)
        assertEquals("LeetCode", rows.single().sectionName)
        assertEquals(2.0, rows.single().target, 1e-9)
        assertTrue(rows.single().includedInDial)
    }
}
