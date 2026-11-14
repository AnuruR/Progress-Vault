package com.anuru.progressvault.data.repository

import com.anuru.progressvault.data.db.dao.ActivityLogDao
import com.anuru.progressvault.data.db.dao.SectionDao
import com.anuru.progressvault.data.db.entity.ActivityLogEntity
import com.anuru.progressvault.data.db.entity.SectionEntity
import com.anuru.progressvault.data.db.entity.SectionKind
import com.anuru.progressvault.data.db.entity.WeeklyTargetOverrideEntity
import com.anuru.progressvault.data.db.toDomain
import com.anuru.progressvault.domain.model.Section
import com.anuru.progressvault.domain.time.WeekCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Editable fields of a section (create or update). */
data class SectionDraft(
    val name: String,
    val iconKey: String,
    val accentColor: Long,
    val unit: String,
    val defaultWeeklyTarget: Double,
    val weight: Double,
    val includeInDial: Boolean,
    val isHidden: Boolean,
)

@Singleton
class SectionRepository @Inject constructor(
    private val sectionDao: SectionDao,
    private val logDao: ActivityLogDao,
    private val weekCalculator: WeekCalculator,
) {
    fun observeAll(): Flow<List<Section>> = sectionDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeVisible(): Flow<List<Section>> = sectionDao.observeVisible().map { list -> list.map { it.toDomain() } }

    fun observe(id: Long): Flow<Section?> = sectionDao.observe(id).map { it?.toDomain() }

    fun observeLogs(sectionId: Long): Flow<List<ActivityLogEntity>> = logDao.observeForSection(sectionId)

    suspend fun deleteLog(log: ActivityLogEntity) = logDao.delete(log)

    /** Sets a section's default weekly target and dial inclusion (a 0 target always excludes it). */
    suspend fun updateDefaultTarget(sectionId: Long, target: Double, includeInDial: Boolean) {
        sectionDao.updateTarget(sectionId, target.coerceAtLeast(0.0), includeInDial && target > 0.0)
    }

    /** Dial weight, clamped to 0.5–5 (0 would silently hide a section; use "include in dial" for that). */
    suspend fun setWeight(sectionId: Long, weight: Double) {
        sectionDao.setWeight(sectionId, weight.coerceIn(0.5, 5.0))
    }

    suspend fun setIncludeInDial(sectionId: Long, include: Boolean) {
        sectionDao.setIncludeInDial(sectionId, include)
    }

    suspend fun create(draft: SectionDraft): Long = sectionDao.insert(
        SectionEntity(
            name = draft.name.trim(),
            iconKey = draft.iconKey,
            accentColor = draft.accentColor,
            unit = draft.unit.trim(),
            defaultWeeklyTarget = draft.defaultWeeklyTarget.coerceAtLeast(0.0),
            weight = draft.weight.coerceAtLeast(0.0),
            sortOrder = sectionDao.maxSortOrder() + 1,
            includeInDial = draft.includeInDial && draft.defaultWeeklyTarget > 0,
            isHidden = draft.isHidden,
            createdAt = weekCalculator.now(),
        ),
    )

    /** Updates a section. Kind, built-in key and built-in flag can never change here. */
    suspend fun update(id: Long, draft: SectionDraft) {
        val existing = sectionDao.getById(id) ?: return
        val targetMatters = existing.kind == SectionKind.STANDARD
        sectionDao.update(
            existing.copy(
                name = draft.name.trim(),
                iconKey = draft.iconKey,
                accentColor = draft.accentColor,
                unit = draft.unit.trim(),
                defaultWeeklyTarget = if (targetMatters) draft.defaultWeeklyTarget.coerceAtLeast(0.0) else existing.defaultWeeklyTarget,
                weight = draft.weight.coerceAtLeast(0.0),
                includeInDial = when (existing.kind) {
                    SectionKind.DERIVED -> false
                    SectionKind.ACADEMICS -> draft.includeInDial
                    SectionKind.STANDARD -> draft.includeInDial && draft.defaultWeeklyTarget > 0
                },
                isHidden = draft.isHidden,
            ),
        )
    }

    suspend fun setHidden(id: Long, hidden: Boolean) {
        val existing = sectionDao.getById(id) ?: return
        sectionDao.update(existing.copy(isHidden = hidden))
    }

    suspend fun reorder(idsInOrder: List<Long>) = sectionDao.reorder(idsInOrder)

    /**
     * Deletes a custom section. Its logs are deliberately kept (they carry the section name)
     * so lifetime history and "then vs now" never lose data. Built-ins can only be hidden.
     */
    suspend fun deleteCustom(id: Long) = sectionDao.deleteCustom(id)

    fun observeOverride(sectionId: Long, weekStart: LocalDate): Flow<WeeklyTargetOverrideEntity?> =
        sectionDao.observeOverride(sectionId, weekStart)

    suspend fun setOverride(sectionId: Long, weekStart: LocalDate, target: Double) =
        sectionDao.upsertOverride(WeeklyTargetOverrideEntity(sectionId, weekStart, target.coerceAtLeast(0.0)))

    suspend fun clearOverride(sectionId: Long, weekStart: LocalDate) = sectionDao.deleteOverride(sectionId, weekStart)
}
