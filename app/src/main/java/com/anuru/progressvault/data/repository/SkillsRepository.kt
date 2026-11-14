package com.anuru.progressvault.data.repository

import com.anuru.progressvault.data.db.dao.ActivityLogDao
import com.anuru.progressvault.data.db.dao.CourseDao
import com.anuru.progressvault.data.db.dao.HistoryDao
import com.anuru.progressvault.data.db.dao.SyncDao
import com.anuru.progressvault.data.db.entity.CourseStatus
import com.anuru.progressvault.data.db.entity.ManualSkillEntity
import com.anuru.progressvault.domain.skills.CourseEvidence
import com.anuru.progressvault.domain.skills.ManualSkillEvidence
import com.anuru.progressvault.domain.skills.SkillInputs
import com.anuru.progressvault.domain.skills.SkillReview
import com.anuru.progressvault.domain.skills.SkillReviewer
import com.anuru.progressvault.domain.skills.SolvedProblemEvidence
import com.anuru.progressvault.domain.skills.TagCountEvidence
import com.anuru.progressvault.domain.time.WeekCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Gathers skill evidence from Room and runs it through the bound [SkillReviewer]. */
@Singleton
class SkillsRepository @Inject constructor(
    private val syncDao: SyncDao,
    private val courseDao: CourseDao,
    private val logDao: ActivityLogDao,
    private val historyDao: HistoryDao,
    private val reviewer: SkillReviewer,
    private val weekCalculator: WeekCalculator,
) {
    fun observeInputs(): Flow<SkillInputs> {
        val zone = weekCalculator.zone
        val courses = combine(courseDao.observeAll(), logDao.observeCourseLastDates()) { list, last ->
            val lastById = last.associate { it.courseId to it.date }
            list.map { c ->
                val activity = listOfNotNull(lastById[c.id], c.finishDate).maxOrNull()
                CourseEvidence(
                    id = c.id,
                    title = c.title,
                    skillTags = c.skillTags,
                    completed = c.status == CourseStatus.COMPLETED,
                    startedOn = c.startDate ?: c.createdAt.atZone(zone).toLocalDate(),
                    lastActivity = activity,
                )
            }
        }
        return combine(syncDao.observeAllProblems(), syncDao.observeTagCounts(), courses, historyDao.observeManualSkills()) { problems, tags, cs, manual ->
            SkillInputs(
                solvedProblems = problems.map { SolvedProblemEvidence(it.topicTags, it.solvedAt.atZone(zone).toLocalDate()) },
                leetCodeTagCounts = tags.map { TagCountEvidence(it.tagName, it.problemsSolved) },
                courses = cs,
                manualSkills = manual.map { ManualSkillEvidence(it.id, it.name, it.firstSeen, it.lastPracticed, it.notes) },
            )
        }
    }

    fun observeReview(): Flow<SkillReview> = observeInputs().map { reviewer.review(it, weekCalculator.today()) }

    suspend fun addManual(name: String, firstSeen: LocalDate, lastPracticed: LocalDate?, notes: String?): Long =
        historyDao.insertManualSkill(ManualSkillEntity(name = name.trim(), firstSeen = firstSeen, lastPracticed = lastPracticed, notes = notes?.trim()?.ifBlank { null }))

    suspend fun updateManual(id: Long, name: String, firstSeen: LocalDate, lastPracticed: LocalDate?, notes: String?) {
        val existing = historyDao.getManualSkill(id) ?: return
        historyDao.updateManualSkill(existing.copy(name = name.trim(), firstSeen = firstSeen, lastPracticed = lastPracticed, notes = notes?.trim()?.ifBlank { null }))
    }

    /** "I practised this today" — creates a manual entry if the skill only came from LeetCode/courses. */
    suspend fun markPracticed(manualId: Long?, name: String, firstSeen: LocalDate?) {
        val today = weekCalculator.today()
        if (manualId != null) {
            historyDao.getManualSkill(manualId)?.let { historyDao.updateManualSkill(it.copy(lastPracticed = today)) }
        } else {
            addManual(name, firstSeen ?: today, today, null)
        }
    }

    suspend fun deleteManual(id: Long) = historyDao.deleteManualSkill(id)
}
