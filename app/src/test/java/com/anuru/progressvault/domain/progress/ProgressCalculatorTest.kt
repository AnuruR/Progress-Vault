package com.anuru.progressvault.domain.progress

import com.anuru.progressvault.data.db.entity.ActivityLogEntity
import com.anuru.progressvault.data.db.entity.GitHubContributionDayEntity
import com.anuru.progressvault.data.db.entity.LeetCodeProblemEntity
import com.anuru.progressvault.data.db.entity.ModuleEntity
import com.anuru.progressvault.data.db.entity.SectionEntity
import com.anuru.progressvault.data.db.entity.SectionKind
import com.anuru.progressvault.data.db.entity.WeeklyTargetOverrideEntity
import com.anuru.progressvault.data.repository.ProgressRepository
import com.anuru.progressvault.data.repository.WeekInputs
import com.anuru.progressvault.domain.time.WeekRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ProgressCalculatorTest {

    private fun sp(id: Long, fraction: Double, weight: Double = 1.0, counts: Boolean = true) = SectionProgress(
        sectionId = id, builtInKey = null, name = "s$id", iconKey = "code", accentColor = 0, unit = "u",
        target = 1.0, achieved = fraction, fraction = fraction, weight = weight, countsInDial = counts,
        isAutoSynced = false, isAcademics = false,
    )

    @Test fun `fraction caps at 1 and handles zero target`() {
        assertEquals(0.5, ProgressCalculator.fraction(1.0, 2.0), 1e-9)
        assertEquals(1.0, ProgressCalculator.fraction(5.0, 2.0), 1e-9)
        assertEquals(0.0, ProgressCalculator.fraction(5.0, 0.0), 1e-9)
    }

    @Test fun `overall is equal-weight average by default`() {
        assertEquals(0.5, ProgressCalculator.overall(listOf(sp(1, 1.0), sp(2, 0.0))), 1e-9)
    }

    @Test fun `overall respects weights`() {
        // (1.0*3 + 0.0*1) / 4
        assertEquals(0.75, ProgressCalculator.overall(listOf(sp(1, 1.0, weight = 3.0), sp(2, 0.0))), 1e-9)
    }

    @Test fun `excluded and zero-weight sections are ignored`() {
        val sections = listOf(sp(1, 1.0), sp(2, 0.0, counts = false), sp(3, 0.0, weight = 0.0))
        assertEquals(1.0, ProgressCalculator.overall(sections), 1e-9)
    }

    @Test fun `overall with nothing counted is zero`() {
        assertEquals(0.0, ProgressCalculator.overall(emptyList()), 1e-9)
    }

    @Test fun `academics caps each module so surplus can't hide a neglected module`() {
        val modules = listOf(
            ModuleProgress(1, "A", "A", 0, targetHours = 4.0, hours = 10.0),
            ModuleProgress(2, "B", "B", 0, targetHours = 4.0, hours = 0.0),
        )
        assertEquals(0.5, ProgressCalculator.academicsFraction(modules), 1e-9)
    }

    @Test fun `pace compares completion to elapsed time`() {
        assertEquals(PaceStatus.BEHIND, ProgressCalculator.pace(0.2, 0.5))
        assertEquals(PaceStatus.ON_PACE, ProgressCalculator.pace(0.47, 0.5))
        assertEquals(PaceStatus.AHEAD, ProgressCalculator.pace(0.8, 0.5))
        assertEquals(PaceStatus.AHEAD, ProgressCalculator.pace(1.0, 1.0))
    }

    // --- live week computation ---

    private val week = WeekRange(LocalDate.of(2026, 11, 9))
    private val now = Instant.parse("2026-11-10T10:00:00Z")

    private fun section(id: Long, key: String?, target: Double, kind: SectionKind = SectionKind.STANDARD, include: Boolean = true, hidden: Boolean = false) =
        SectionEntity(id = id, builtInKey = key, kind = kind, name = key ?: "custom", iconKey = "code", accentColor = 0,
            unit = "u", defaultWeeklyTarget = target, sortOrder = id.toInt(), isBuiltIn = key != null,
            isHidden = hidden, includeInDial = include, createdAt = now)

    private fun log(sectionId: Long, amount: Double, moduleId: Long? = null) = ActivityLogEntity(
        sectionId = sectionId, sectionNameAtLog = "x", amount = amount, timestamp = now,
        localDate = LocalDate.of(2026, 11, 10), moduleId = moduleId)

    @Test fun `live week combines manual logs, synced data, overrides and modules`() {
        val inputs = WeekInputs(
            sections = listOf(
                section(1, "academics", 0.0, SectionKind.ACADEMICS),
                section(2, "linkedin", 1.0),
                section(3, "leetcode", 2.0),
                section(4, "github", 0.0, include = false),
                section(5, "skills", 0.0, SectionKind.DERIVED, include = false),
                section(6, null, 5.0, hidden = true),
            ),
            overrides = listOf(WeeklyTargetOverrideEntity(sectionId = 2, weekStart = week.start, target = 2.0)),
            logs = listOf(log(1, 2.0, moduleId = 10), log(2, 1.0), log(3, 1.0), log(6, 5.0)),
            problems = listOf(LeetCodeProblemEntity("two-sum", "Two Sum", "EASY", emptyList(), now)),
            contributions = listOf(GitHubContributionDayEntity(LocalDate.of(2026, 11, 10), 7, now)),
            modules = listOf(ModuleEntity(id = 10, semesterId = 1, code = "CS", name = "CS", accentColor = 0, weeklyTargetHours = 4.0)),
        )
        val result = ProgressRepository.computeLive(week, inputs)
        val byId = result.sections.associateBy { it.sectionId }

        assertFalse("derived and hidden sections are not shown", 5L in byId || 6L in byId)
        assertEquals(0.5, byId.getValue(1).fraction, 1e-9)           // 2h of 4h
        assertEquals(2.0, byId.getValue(2).target, 1e-9)             // override applied
        assertEquals(0.5, byId.getValue(2).fraction, 1e-9)
        assertEquals(2.0, byId.getValue(3).achieved, 1e-9)           // 1 synced + 1 manual
        assertTrue(byId.getValue(3).isComplete)
        assertEquals(7.0, byId.getValue(4).achieved, 1e-9)           // tracked...
        assertFalse(byId.getValue(4).countsInDial)                   // ...but not in dial
        assertEquals((0.5 + 0.5 + 1.0) / 3, result.overall, 1e-9)
    }
}
