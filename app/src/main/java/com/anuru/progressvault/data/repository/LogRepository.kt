package com.anuru.progressvault.data.repository

import com.anuru.progressvault.data.db.dao.ActivityLogDao
import com.anuru.progressvault.data.db.dao.SectionDao
import com.anuru.progressvault.data.db.entity.ActivityLogEntity
import com.anuru.progressvault.domain.time.WeekCalculator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogRepository @Inject constructor(
    private val logDao: ActivityLogDao,
    private val sectionDao: SectionDao,
    private val weekCalculator: WeekCalculator,
) {
    /** Logs manual progress now. Returns the new log id (for undo). */
    suspend fun log(
        sectionId: Long,
        amount: Double,
        moduleId: Long? = null,
        courseId: Long? = null,
        title: String? = null,
        url: String? = null,
        note: String? = null,
    ): Long {
        val section = requireNotNull(sectionDao.getById(sectionId)) { "Unknown section $sectionId" }
        return logDao.insert(
            ActivityLogEntity(
                sectionId = sectionId,
                sectionNameAtLog = section.name,
                amount = amount,
                timestamp = weekCalculator.now(),
                localDate = weekCalculator.today(),
                moduleId = moduleId,
                courseId = courseId,
                title = title?.trim()?.takeIf { it.isNotEmpty() },
                url = url?.trim()?.takeIf { it.isNotEmpty() },
                note = note?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
    }

    suspend fun undo(logId: Long) = logDao.deleteById(logId)
}
