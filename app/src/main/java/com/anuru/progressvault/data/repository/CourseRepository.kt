package com.anuru.progressvault.data.repository

import com.anuru.progressvault.data.db.dao.ActivityLogDao
import com.anuru.progressvault.data.db.dao.CourseDao
import com.anuru.progressvault.data.db.entity.CourseEntity
import com.anuru.progressvault.data.db.entity.CourseStatus
import com.anuru.progressvault.domain.time.WeekCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class CourseWithProgress(val course: CourseEntity, val lessonsDone: Double) {
    /** 0..1 when the total is known, else null. */
    val fraction: Double? get() = course.totalLessons?.takeIf { it > 0 }?.let { (lessonsDone / it).coerceIn(0.0, 1.0) }
}

@Singleton
class CourseRepository @Inject constructor(
    private val courseDao: CourseDao,
    private val logDao: ActivityLogDao,
    private val weekCalculator: WeekCalculator,
) {
    fun observeAll(): Flow<List<CourseWithProgress>> = combine(courseDao.observeAll(), logDao.observeCourseTotals()) { courses, totals ->
        val byId = totals.associate { it.courseId to it.total }
        courses.map { CourseWithProgress(it, byId[it.id] ?: 0.0) }
    }

    suspend fun get(id: Long): CourseEntity? = courseDao.observe(id).first()

    /** Inserts or updates; marking COMPLETED fills in today's finish date if missing. */
    suspend fun save(course: CourseEntity): Long {
        val today = weekCalculator.today()
        val normalized = course.copy(
            title = course.title.trim(),
            url = course.url?.trim()?.ifEmpty { null },
            certificateUrl = course.certificateUrl?.trim()?.ifEmpty { null },
            skillTags = course.skillTags.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() },
            finishDate = if (course.status == CourseStatus.COMPLETED) course.finishDate ?: today else course.finishDate,
        )
        return if (course.id == 0L) courseDao.insert(normalized.copy(createdAt = weekCalculator.now()))
        else normalized.also { courseDao.update(it) }.id
    }

    suspend fun setStatus(course: CourseEntity, status: CourseStatus) { save(course.copy(status = status)) }

    /** Deleting a course keeps its lesson logs (counted in lifetime totals). */
    suspend fun delete(course: CourseEntity) = courseDao.delete(course)

    fun observeCount(): Flow<Int> = courseDao.observeAll().map { it.size }
}
