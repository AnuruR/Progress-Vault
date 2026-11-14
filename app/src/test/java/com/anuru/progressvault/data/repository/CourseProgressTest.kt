package com.anuru.progressvault.data.repository

import com.anuru.progressvault.data.db.entity.CourseEntity
import com.anuru.progressvault.data.db.entity.CoursePlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class CourseProgressTest {
    private fun course(total: Int?) = CourseEntity(title = "c", platform = CoursePlatform.UDEMY, totalLessons = total, createdAt = Instant.EPOCH)

    @Test fun `percent complete when total known`() = assertEquals(0.25, CourseWithProgress(course(40), 10.0).fraction!!, 1e-9)
    @Test fun `percent is capped`() = assertEquals(1.0, CourseWithProgress(course(4), 10.0).fraction!!, 1e-9)
    @Test fun `no percent without total`() = assertNull(CourseWithProgress(course(null), 10.0).fraction)
}
