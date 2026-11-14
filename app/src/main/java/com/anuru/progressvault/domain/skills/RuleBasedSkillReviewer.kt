package com.anuru.progressvault.domain.skills

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Tunable thresholds for [RuleBasedSkillReviewer]. */
object SkillRules {
    /** Score points per solved problem tagged with the skill. */
    const val POINTS_PER_PROBLEM = 1
    /** Points per course in progress / completed that teaches the skill. */
    const val POINTS_PER_COURSE = 4
    const val POINTS_PER_COMPLETED_COURSE = 8
    /** A manual skill alone counts as a little evidence. */
    const val POINTS_MANUAL = 2

    const val PRACTICING_SCORE = 5
    const val STRONG_SCORE = 20
    /** Strong also requires practice within this many days (if a date is known). */
    const val STRONG_RECENCY_DAYS = 60L

    const val RUSTY_AFTER_DAYS = 30L
    const val NEW_WITHIN_DAYS = 30L

    const val MAX_INSIGHTS_PER_KIND = 3
    /** "Close to next level" is suggested when within this many points. */
    const val CLOSE_WITHIN = 3
}

class RuleBasedSkillReviewer @Inject constructor() : SkillReviewer {

    override suspend fun review(inputs: SkillInputs, today: LocalDate): SkillReview = reviewNow(inputs, today)

    /** Non-suspending core, used by tests. */
    fun reviewNow(inputs: SkillInputs, today: LocalDate): SkillReview {
        val acc = LinkedHashMap<String, Acc>()
        fun get(name: String): Acc? {
            val key = SkillNames.key(name) ?: return null
            return acc.getOrPut(key) { Acc(key) }.also { it.offerName(name) }
        }

        inputs.solvedProblems.forEach { p ->
            p.tags.forEach { t -> get(t)?.apply { sources += SkillSource.LEETCODE; localProblems++; seen(p.solvedOn) } }
        }
        inputs.leetCodeTagCounts.filter { it.problemsSolved > 0 }.forEach { t ->
            get(t.name)?.apply { sources += SkillSource.LEETCODE; remoteProblems = maxOf(remoteProblems, t.problemsSolved) }
        }
        inputs.courses.forEach { c ->
            c.skillTags.distinctBy { SkillNames.key(it) }.forEach { t ->
                get(t)?.apply {
                    sources += SkillSource.COURSE
                    courses += c.title
                    if (c.completed) completedCourses++
                    seen(c.startedOn)
                    c.lastActivity?.let(::seen)
                }
            }
        }
        inputs.manualSkills.forEach { m ->
            get(m.name)?.apply {
                sources += SkillSource.MANUAL
                manualId = m.id
                manualName = m.name.trim()
                notes = m.notes
                seen(m.firstSeen)
                m.lastPracticed?.let(::seen)
            }
        }

        val skills = acc.values.map { it.toSkill(today) }
            .sortedWith(compareByDescending<Skill> { it.level }.thenByDescending { score(it) }.thenBy { it.name.lowercase() })
        return SkillReview(skills, insights(skills, today))
    }

    private fun insights(skills: List<Skill>, today: LocalDate): List<SkillInsight> = buildList {
        skills.filter { it.isRusty }.sortedByDescending { score(it) }.take(SkillRules.MAX_INSIGHTS_PER_KIND).forEach {
            add(SkillInsight.Rusty(it, ChronoUnit.DAYS.between(it.lastPracticed, today)))
        }
        skills.mapNotNull { s ->
            val sc = score(s)
            when (s.level) {
                SkillLevel.BEGINNER -> (SkillRules.PRACTICING_SCORE - sc).takeIf { it in 1..SkillRules.CLOSE_WITHIN }
                    ?.let { SkillInsight.CloseToNextLevel(s, SkillLevel.PRACTICING, it) }
                SkillLevel.PRACTICING -> (SkillRules.STRONG_SCORE - sc).takeIf { it in 1..SkillRules.CLOSE_WITHIN }
                    ?.let { SkillInsight.CloseToNextLevel(s, SkillLevel.STRONG, it) }
                SkillLevel.STRONG -> null
            }
        }.sortedBy { it.missingScore }.take(SkillRules.MAX_INSIGHTS_PER_KIND).forEach(::add)
    }

    companion object {
        fun score(s: Skill): Int = score(s.problemCount, s.courseCount, s.completedCourseCount, SkillSource.MANUAL in s.sources)

        fun score(problems: Int, courses: Int, completed: Int, manual: Boolean): Int =
            problems * SkillRules.POINTS_PER_PROBLEM +
                (courses - completed) * SkillRules.POINTS_PER_COURSE +
                completed * SkillRules.POINTS_PER_COMPLETED_COURSE +
                (if (manual) SkillRules.POINTS_MANUAL else 0)

        fun level(score: Int, lastPracticed: LocalDate?, today: LocalDate): SkillLevel {
            val recent = lastPracticed == null || ChronoUnit.DAYS.between(lastPracticed, today) <= SkillRules.STRONG_RECENCY_DAYS
            return when {
                score >= SkillRules.STRONG_SCORE && recent -> SkillLevel.STRONG
                score >= SkillRules.PRACTICING_SCORE -> SkillLevel.PRACTICING
                else -> SkillLevel.BEGINNER
            }
        }
    }

    private class Acc(val key: String) {
        val sources = mutableSetOf<SkillSource>()
        var localProblems = 0
        var remoteProblems = 0
        val courses = mutableListOf<String>()
        var completedCourses = 0
        var first: LocalDate? = null
        var last: LocalDate? = null
        var manualId: Long? = null
        var manualName: String? = null
        var notes: String? = null
        private var bestName: String? = null

        fun offerName(n: String) {
            val t = n.trim()
            // Prefer a capitalised spelling ("Dynamic Programming" over "dynamic programming").
            if (bestName == null || (bestName!!.first().isLowerCase() && t.first().isUpperCase())) bestName = t
        }

        fun seen(d: LocalDate) {
            if (first == null || d < first) first = d
            if (last == null || d > last) last = d
        }

        fun toSkill(today: LocalDate): Skill {
            val problems = maxOf(localProblems, remoteProblems)
            val sc = score(problems, courses.size, completedCourses, manualId != null)
            return Skill(
                key = key,
                name = manualName ?: SkillNames.display(bestName ?: key),
                sources = sources.toSet(),
                problemCount = problems,
                courseCount = courses.size,
                completedCourseCount = completedCourses,
                courseTitles = courses.toList(),
                firstSeen = first,
                lastPracticed = last,
                level = level(sc, last, today),
                isRusty = last != null && ChronoUnit.DAYS.between(last, today) > SkillRules.RUSTY_AFTER_DAYS,
                isNew = first != null && ChronoUnit.DAYS.between(first, today) in 0..SkillRules.NEW_WITHIN_DAYS,
                manualId = manualId,
                notes = notes,
            )
        }
    }
}

/** Normalises skill names so "Arrays", "array" and " ARRAY " merge. */
object SkillNames {
    private val ALIASES = mapOf(
        "arrays" to "array",
        "strings" to "string",
        "graphs" to "graph",
        "trees" to "tree",
        "dp" to "dynamic programming",
        "hash map" to "hash table",
        "hashmap" to "hash table",
        "hashing" to "hash table",
        "js" to "javascript",
        "ts" to "typescript",
        "ml" to "machine learning",
        "py" to "python",
        "binary search tree" to "binary search tree",
        "linked lists" to "linked list",
        "stacks" to "stack",
        "queues" to "queue",
        "heap" to "heap (priority queue)",
        "priority queue" to "heap (priority queue)",
    )

    /** "java" → "Java", "dynamic programming" → "Dynamic Programming"; mixed-case input is kept as typed. */
    fun display(name: String): String =
        if (name.any { it.isUpperCase() }) name
        else name.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase() } }

    fun key(name: String): String? {
        val k = name.trim().lowercase().replace(Regex("\\s+"), " ")
        if (k.isEmpty()) return null
        return ALIASES[k] ?: k
    }
}
