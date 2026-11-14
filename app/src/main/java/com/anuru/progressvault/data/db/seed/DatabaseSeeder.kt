package com.anuru.progressvault.data.db.seed

import android.content.Context
import androidx.annotation.StringRes
import com.anuru.progressvault.R
import com.anuru.progressvault.data.db.dao.SectionDao
import com.anuru.progressvault.data.db.entity.SectionEntity
import com.anuru.progressvault.data.db.entity.SectionKind
import com.anuru.progressvault.domain.model.AccentPalette
import com.anuru.progressvault.domain.model.BuiltInSectionKey
import com.anuru.progressvault.domain.time.WeekCalculator
import com.anuru.progressvault.ui.components.SectionIconKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Definition of a built-in section as seeded on first launch. */
data class BuiltInSectionSpec(
    val key: BuiltInSectionKey,
    val kind: SectionKind,
    @param:StringRes val nameRes: Int,
    @param:StringRes val unitRes: Int,
    val iconKey: String,
    val accentColor: Long,
    val defaultTarget: Double,
    val includeInDial: Boolean,
    val isAutoSynced: Boolean,
)

object BuiltInSections {
    val specs: List<BuiltInSectionSpec> = listOf(
        BuiltInSectionSpec(
            BuiltInSectionKey.ACADEMICS, SectionKind.ACADEMICS,
            R.string.section_academics, R.string.unit_hours,
            SectionIconKey.SCHOOL, AccentPalette.PURPLE,
            // Academics' real target is the sum of its active modules' targets.
            defaultTarget = 0.0, includeInDial = true, isAutoSynced = false,
        ),
        BuiltInSectionSpec(
            BuiltInSectionKey.LINKEDIN, SectionKind.STANDARD,
            R.string.section_linkedin, R.string.unit_posts,
            SectionIconKey.CAMPAIGN, AccentPalette.BLUE,
            defaultTarget = 1.0, includeInDial = true, isAutoSynced = false,
        ),
        BuiltInSectionSpec(
            BuiltInSectionKey.LEETCODE, SectionKind.STANDARD,
            R.string.section_leetcode, R.string.unit_problems,
            SectionIconKey.CODE, AccentPalette.AMBER,
            defaultTarget = 2.0, includeInDial = true, isAutoSynced = true,
        ),
        BuiltInSectionSpec(
            BuiltInSectionKey.COURSES, SectionKind.STANDARD,
            R.string.section_courses, R.string.unit_lessons,
            SectionIconKey.BOOK, AccentPalette.CYAN,
            defaultTarget = 3.0, includeInDial = true, isAutoSynced = false,
        ),
        BuiltInSectionSpec(
            BuiltInSectionKey.GITHUB, SectionKind.STANDARD,
            R.string.section_github, R.string.unit_commits,
            SectionIconKey.COMMIT, AccentPalette.GITHUB_GREEN,
            // Tracked and shown, but excluded from the dial while the target is 0.
            defaultTarget = 0.0, includeInDial = false, isAutoSynced = true,
        ),
        BuiltInSectionSpec(
            BuiltInSectionKey.SKILLS, SectionKind.DERIVED,
            R.string.section_skills, R.string.unit_skills,
            SectionIconKey.PSYCHOLOGY, AccentPalette.PINK,
            defaultTarget = 0.0, includeInDial = false, isAutoSynced = false,
        ),
    )
}

/**
 * Idempotently inserts built-in sections. Safe to call on every launch: existing rows
 * (matched by unique builtInKey) are left untouched, so user edits are never overwritten,
 * and any built-in added in a future version is seeded automatically.
 */
@Singleton
class DatabaseSeeder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sectionDao: SectionDao,
    private val weekCalculator: WeekCalculator,
) {
    /**
     * One-time colour update for installs seeded before GitHub got github.com's green:
     * GitHub cyan → GitHub green and Courses green → cyan, only if both are still the old defaults.
     */
    suspend fun applyGitHubGreen() {
        val gh = sectionDao.getByBuiltInKey(BuiltInSectionKey.GITHUB.key) ?: return
        val courses = sectionDao.getByBuiltInKey(BuiltInSectionKey.COURSES.key)
        if (gh.accentColor == AccentPalette.CYAN) sectionDao.update(gh.copy(accentColor = AccentPalette.GITHUB_GREEN))
        if (courses != null && courses.accentColor == AccentPalette.GREEN) sectionDao.update(courses.copy(accentColor = AccentPalette.CYAN))
    }

    suspend fun ensureSeeded() {
        val now = weekCalculator.now()
        val rows = BuiltInSections.specs.mapIndexed { index, spec ->
            SectionEntity(
                builtInKey = spec.key.key,
                kind = spec.kind,
                name = context.getString(spec.nameRes),
                iconKey = spec.iconKey,
                accentColor = spec.accentColor,
                unit = context.getString(spec.unitRes),
                defaultWeeklyTarget = spec.defaultTarget,
                weight = 1.0,
                sortOrder = index,
                isBuiltIn = true,
                includeInDial = spec.includeInDial,
                isAutoSynced = spec.isAutoSynced,
                createdAt = now,
            )
        }
        sectionDao.insertIgnore(rows)
    }
}
