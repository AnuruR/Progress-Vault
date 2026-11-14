package com.anuru.progressvault.domain.rollover

import com.anuru.progressvault.data.db.entity.WeeklySectionSnapshotEntity
import com.anuru.progressvault.data.db.entity.WeeklySnapshotEntity
import com.anuru.progressvault.domain.progress.WeekProgress
import com.anuru.progressvault.domain.time.WeekRange
import java.time.Instant
import java.time.LocalDate

/** Pure rules for archiving finished weeks into immutable WeeklySnapshots. */
object WeeklyArchiver {
    const val MAX_CATCH_UP_WEEKS = 104

    /**
     * Finished weeks (before [current]) since the week of [firstUse] that have no snapshot yet,
     * oldest first. Bounded so a very old firstUse can't run away.
     */
    fun weeksToArchive(firstUse: LocalDate, current: WeekRange, archived: Set<LocalDate>): List<WeekRange> {
        val first = WeekRange.containing(firstUse)
        val earliest = maxOf(first.start, current.start.minusWeeks(MAX_CATCH_UP_WEEKS.toLong()))
        return generateSequence(WeekRange(earliest)) { it.next() }
            .takeWhile { it.start.isBefore(current.start) }
            .filter { it.start !in archived }
            .toList()
    }

    fun snapshot(progress: WeekProgress, now: Instant): Pair<WeeklySnapshotEntity, List<WeeklySectionSnapshotEntity>> =
        WeeklySnapshotEntity(progress.week.start, progress.overall, now) to progress.sections.map { s ->
            WeeklySectionSnapshotEntity(
                weekStart = progress.week.start,
                sectionId = s.sectionId,
                sectionName = s.name,
                builtInKey = s.builtInKey?.key,
                unit = s.unit,
                accentColor = s.accentColor,
                target = s.target,
                achieved = s.achieved,
                weight = s.weight,
                includedInDial = s.countsInDial,
            )
        }
}
