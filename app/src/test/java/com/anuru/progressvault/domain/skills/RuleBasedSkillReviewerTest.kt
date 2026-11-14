package com.anuru.progressvault.domain.skills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RuleBasedSkillReviewerTest {
    private val today = LocalDate.of(2026, 11, 14)
    private val reviewer = RuleBasedSkillReviewer()
    private fun inputs(
        problems: List<SolvedProblemEvidence> = emptyList(),
        tags: List<TagCountEvidence> = emptyList(),
        courses: List<CourseEvidence> = emptyList(),
        manual: List<ManualSkillEvidence> = emptyList(),
    ) = SkillInputs(problems, tags, courses, manual)

    @Test fun mergesSameSkillAcrossSourcesAndSpellings() {
        val r = reviewer.reviewNow(
            inputs(
                problems = listOf(SolvedProblemEvidence(listOf("Array"), today.minusDays(3))),
                courses = listOf(CourseEvidence(1, "DSA", listOf("arrays"), false, today.minusDays(10), null)),
                manual = listOf(ManualSkillEvidence(7, " ARRAY ", today.minusDays(20), null, null)),
            ),
            today,
        )
        val s = r.skills.single()
        assertEquals(setOf(SkillSource.LEETCODE, SkillSource.COURSE, SkillSource.MANUAL), s.sources)
        assertEquals(today.minusDays(20), s.firstSeen)
        assertEquals(today.minusDays(3), s.lastPracticed)
        assertEquals(7L, s.manualId)
    }

    @Test fun problemCountUsesMaxOfLocalAndRemote() {
        val r = reviewer.reviewNow(
            inputs(
                problems = List(3) { SolvedProblemEvidence(listOf("Graph"), today) },
                tags = listOf(TagCountEvidence("Graph", 25)),
            ),
            today,
        )
        assertEquals(25, r.skills.single().problemCount)
        assertEquals(SkillLevel.STRONG, r.skills.single().level)
    }

    @Test fun levelThresholds() {
        assertEquals(SkillLevel.BEGINNER, RuleBasedSkillReviewer.level(SkillRules.PRACTICING_SCORE - 1, today, today))
        assertEquals(SkillLevel.PRACTICING, RuleBasedSkillReviewer.level(SkillRules.PRACTICING_SCORE, today, today))
        assertEquals(SkillLevel.STRONG, RuleBasedSkillReviewer.level(SkillRules.STRONG_SCORE, today, today))
        // Strong needs recency.
        assertEquals(
            SkillLevel.PRACTICING,
            RuleBasedSkillReviewer.level(SkillRules.STRONG_SCORE, today.minusDays(SkillRules.STRONG_RECENCY_DAYS + 1), today),
        )
    }

    @Test fun completedCourseWorthMore() {
        val inProgress = RuleBasedSkillReviewer.score(0, 1, 0, false)
        val completed = RuleBasedSkillReviewer.score(0, 1, 1, false)
        assertTrue(completed > inProgress)
    }

    @Test fun rustyAndNewFlags() {
        val r = reviewer.reviewNow(
            inputs(
                manual = listOf(
                    ManualSkillEvidence(1, "Old", today.minusDays(200), today.minusDays(31), null),
                    ManualSkillEvidence(2, "Fresh", today.minusDays(5), today.minusDays(5), null),
                    ManualSkillEvidence(3, "Undated", today.minusDays(100), null, null),
                ),
            ),
            today,
        )
        val by = r.skills.associateBy { it.name }
        assertTrue(by.getValue("Old").isRusty)
        assertFalse(by.getValue("Old").isNew)
        assertTrue(by.getValue("Fresh").isNew)
        assertFalse(by.getValue("Fresh").isRusty)
        // Undated: last practised falls back to first seen → rusty.
        assertTrue(by.getValue("Undated").isRusty)
        assertTrue(r.insights.any { it is SkillInsight.Rusty && it.skill.name == "Old" && it.daysIdle == 31L })
    }

    @Test fun tagCountsWithoutDatesAreNeitherRustyNorNew() {
        val s = reviewer.reviewNow(inputs(tags = listOf(TagCountEvidence("Math", 3))), today).skills.single()
        assertNull(s.firstSeen)
        assertFalse(s.isRusty)
        assertFalse(s.isNew)
    }

    @Test fun closeToNextLevelInsight() {
        val r = reviewer.reviewNow(inputs(tags = listOf(TagCountEvidence("Tree", SkillRules.STRONG_SCORE - 2))), today)
        val i = r.insights.filterIsInstance<SkillInsight.CloseToNextLevel>().single()
        assertEquals(SkillLevel.STRONG, i.next)
        assertEquals(2, i.missingScore)
    }

    @Test fun displayNamesCapitalised() {
        assertEquals("Java", SkillNames.display("java"))
        assertEquals("Dynamic Programming", SkillNames.display("dynamic programming"))
        assertEquals("iOS", SkillNames.display("iOS"))
    }

    @Test fun aliasesNormalise() {
        assertEquals("dynamic programming", SkillNames.key("DP"))
        assertEquals("hash table", SkillNames.key("HashMap"))
        assertNull(SkillNames.key("   "))
    }

    @Test fun sortedStrongFirst() {
        val r = reviewer.reviewNow(
            inputs(tags = listOf(TagCountEvidence("Weak", 1), TagCountEvidence("Strong", 40))),
            today,
        )
        assertEquals("Strong", r.skills.first().name)
    }
}
