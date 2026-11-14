package com.anuru.progressvault.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Commit
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** Stable string keys stored in the DB for section icons. */
object SectionIconKey {
    const val SCHOOL = "school"
    const val CAMPAIGN = "campaign"
    const val CODE = "code"
    const val BOOK = "book"
    const val COMMIT = "commit"
    const val PSYCHOLOGY = "psychology"
    const val FITNESS = "fitness"
    const val BRUSH = "brush"
    const val LANGUAGE = "language"
    const val MUSIC = "music"
    const val SCIENCE = "science"
    const val WORK = "work"
    const val BOLT = "bolt"
    const val STAR = "star"
}

object SectionIcons {
    private val map: Map<String, ImageVector> = linkedMapOf(
        SectionIconKey.SCHOOL to Icons.Outlined.School,
        SectionIconKey.CAMPAIGN to Icons.Outlined.Campaign,
        SectionIconKey.CODE to Icons.Outlined.Code,
        SectionIconKey.BOOK to Icons.AutoMirrored.Outlined.MenuBook,
        SectionIconKey.COMMIT to Icons.Outlined.Commit,
        SectionIconKey.PSYCHOLOGY to Icons.Outlined.Psychology,
        SectionIconKey.FITNESS to Icons.Outlined.FitnessCenter,
        SectionIconKey.BRUSH to Icons.Outlined.Brush,
        SectionIconKey.LANGUAGE to Icons.Outlined.Translate,
        SectionIconKey.MUSIC to Icons.Outlined.MusicNote,
        SectionIconKey.SCIENCE to Icons.Outlined.Science,
        SectionIconKey.WORK to Icons.Outlined.Work,
        SectionIconKey.BOLT to Icons.Outlined.Bolt,
        SectionIconKey.STAR to Icons.Outlined.Star,
    )

    /** Keys offered in the custom-section icon picker. */
    val pickerKeys: List<String> get() = map.keys.toList()

    fun forKey(key: String): ImageVector = map[key] ?: Icons.Outlined.Star
}
