package com.anuru.progressvault.domain.skills

import java.time.LocalDate

enum class SkillSource { LEETCODE, COURSE, MANUAL }

enum class SkillLevel { BEGINNER, PRACTICING, STRONG }

/** Raw evidence, already loaded from Room. Kept Android-free so reviewers are unit-testable. */
data class SkillInputs(
    /** Locally stored solved problems: topic tag names + solve date. */
    val solvedProblems: List<SolvedProblemEvidence>,
    /** LeetCode's own all-time per-tag counts (no dates). */
    val leetCodeTagCounts: List<TagCountEvidence>,
    val courses: List<CourseEvidence>,
    val manualSkills: List<ManualSkillEvidence>,
)

data class SolvedProblemEvidence(val tags: List<String>, val solvedOn: LocalDate)
data class TagCountEvidence(val name: String, val problemsSolved: Int)
data class CourseEvidence(
    val id: Long,
    val title: String,
    val skillTags: List<String>,
    val completed: Boolean,
    val startedOn: LocalDate,
    /** Most recent lesson log or finish date, whichever is later. */
    val lastActivity: LocalDate?,
)
data class ManualSkillEvidence(val id: Long, val name: String, val firstSeen: LocalDate, val lastPracticed: LocalDate?, val notes: String?)

data class Skill(
    /** Normalised identity used to merge the same skill from different sources. */
    val key: String,
    val name: String,
    val sources: Set<SkillSource>,
    val problemCount: Int,
    val courseCount: Int,
    val completedCourseCount: Int,
    val courseTitles: List<String>,
    val firstSeen: LocalDate?,
    val lastPracticed: LocalDate?,
    val level: SkillLevel,
    val isRusty: Boolean,
    val isNew: Boolean,
    /** Set when a manual entry exists (editable/deletable). */
    val manualId: Long?,
    val notes: String?,
)

/** Rule-generated suggestions; the UI turns each into a localized sentence. */
sealed interface SkillInsight {
    data class Rusty(val skill: Skill, val daysIdle: Long) : SkillInsight
    /** [missingScore] more points (≈ problems) to reach the next level. */
    data class CloseToNextLevel(val skill: Skill, val next: SkillLevel, val missingScore: Int) : SkillInsight
}

data class SkillReview(
    val skills: List<Skill>,
    val insights: List<SkillInsight>,
) {
    val rusty: List<Skill> get() = skills.filter { it.isRusty }
    val newlyGained: List<Skill> get() = skills.filter { it.isNew }
    fun count(level: SkillLevel) = skills.count { it.level == level }
}

/**
 * Turns raw evidence into a skill review.
 *
 * Today the app binds [RuleBasedSkillReviewer] (see `di/SkillsModule`).
 *
 * FUTURE — `AiSkillReviewer`: an implementation could send an anonymised summary of
 * [SkillInputs] to an LLM API and return richer [SkillInsight]s (e.g. study plans). It should:
 *  - start from `RuleBasedSkillReviewer().review(...)` and only *add* insights, so levels stay deterministic;
 *  - fall back to the rule-based result when offline or on any error (the app is offline-first);
 *  - require explicit opt-in in Settings, and keep any API key in `TokenStore`.
 * Swap it in by changing the single `@Binds` in `SkillsModule`. No AI calls exist in this codebase yet.
 */
interface SkillReviewer {
    suspend fun review(inputs: SkillInputs, today: LocalDate): SkillReview
}
