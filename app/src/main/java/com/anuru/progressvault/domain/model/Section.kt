package com.anuru.progressvault.domain.model

import com.anuru.progressvault.data.db.entity.SectionKind

/** Stable identifiers for built-in sections (stored in [com.anuru.progressvault.data.db.entity.SectionEntity.builtInKey]). */
enum class BuiltInSectionKey(val key: String) {
    ACADEMICS("academics"),
    LINKEDIN("linkedin"),
    LEETCODE("leetcode"),
    COURSES("courses"),
    GITHUB("github"),
    SKILLS("skills"),
    ;

    companion object {
        fun fromKey(key: String?): BuiltInSectionKey? = entries.firstOrNull { it.key == key }
    }
}

data class Section(
    val id: Long,
    val builtInKey: BuiltInSectionKey?,
    val kind: SectionKind,
    val name: String,
    val iconKey: String,
    val accentColor: Long,
    val unit: String,
    val defaultWeeklyTarget: Double,
    val weight: Double,
    val sortOrder: Int,
    val isBuiltIn: Boolean,
    val isHidden: Boolean,
    val includeInDial: Boolean,
    val isAutoSynced: Boolean,
) {
    val isDerived: Boolean get() = kind == SectionKind.DERIVED
    val isAcademics: Boolean get() = kind == SectionKind.ACADEMICS

    /**
     * A section only counts toward the dial when the user wants it to, it isn't derived,
     * it isn't hidden and it actually has a positive target (e.g. GitHub with target 0 is
     * shown and tracked but excluded).
     */
    fun countsInDial(target: Double): Boolean =
        includeInDial && !isDerived && !isHidden && target > 0.0
}
