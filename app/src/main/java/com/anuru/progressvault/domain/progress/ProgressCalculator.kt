package com.anuru.progressvault.domain.progress

import com.anuru.progressvault.domain.model.BuiltInSectionKey
import com.anuru.progressvault.domain.time.WeekRange

/** Display-ready progress of one section in one week (live or archived). */
data class SectionProgress(
    val sectionId: Long,
    val builtInKey: BuiltInSectionKey?,
    val name: String,
    val iconKey: String,
    val accentColor: Long,
    val unit: String,
    val target: Double,
    val achieved: Double,
    /** 0..1, already capped. */
    val fraction: Double,
    val weight: Double,
    val countsInDial: Boolean,
    val isAutoSynced: Boolean,
    val isAcademics: Boolean,
) {
    val isComplete: Boolean get() = target > 0 && fraction >= 1.0
}

data class ModuleProgress(
    val moduleId: Long,
    val code: String,
    val name: String,
    val accentColor: Long,
    val targetHours: Double,
    val hours: Double,
) {
    val fraction: Double get() = ProgressCalculator.fraction(hours, targetHours)
}

data class WeekProgress(
    val week: WeekRange,
    val sections: List<SectionProgress>,
    val modules: List<ModuleProgress>,
    /** 0..1 weighted dial value. */
    val overall: Double,
    /** True when read from an immutable WeeklySnapshot. */
    val isArchived: Boolean,
)

enum class PaceStatus { AHEAD, ON_PACE, BEHIND }

object ProgressCalculator {
    /** Tolerance so being a hair behind the clock still counts as on pace. */
    const val PACE_TOLERANCE = 0.05
    const val AHEAD_MARGIN = 0.15

    /** min(achieved / target, 1); a non-positive target yields 0. */
    fun fraction(achieved: Double, target: Double): Double =
        if (target <= 0.0) 0.0 else (achieved / target).coerceIn(0.0, 1.0)

    /**
     * Academics = combined module progress: Σ min(hours_i, target_i) / Σ target_i, so extra hours
     * on one module can't hide another module being untouched.
     */
    fun academicsFraction(modules: List<ModuleProgress>): Double {
        val totalTarget = modules.sumOf { it.targetHours.coerceAtLeast(0.0) }
        if (totalTarget <= 0.0) return 0.0
        val capped = modules.sumOf { it.hours.coerceAtLeast(0.0).coerceAtMost(it.targetHours.coerceAtLeast(0.0)) }
        return (capped / totalTarget).coerceIn(0.0, 1.0)
    }

    /** Weighted average of each dial section's capped fraction; 0 when nothing counts. */
    fun overall(sections: List<SectionProgress>): Double {
        val included = sections.filter { it.countsInDial && it.weight > 0.0 }
        val totalWeight = included.sumOf { it.weight }
        if (totalWeight <= 0.0) return 0.0
        return (included.sumOf { it.fraction * it.weight } / totalWeight).coerceIn(0.0, 1.0)
    }

    /** Compares completion with the fraction of the week elapsed. */
    fun pace(overall: Double, elapsed: Double): PaceStatus = when {
        overall >= 1.0 || overall >= elapsed + AHEAD_MARGIN -> PaceStatus.AHEAD
        overall + PACE_TOLERANCE >= elapsed -> PaceStatus.ON_PACE
        else -> PaceStatus.BEHIND
    }
}
