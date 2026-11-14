package com.anuru.progressvault.ui.academics

import com.anuru.progressvault.domain.model.Semester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SemesterWeekTest {
    private val sem = Semester(1, "Y2S1", LocalDate.of(2026, 8, 10), LocalDate.of(2026, 11, 29), true)

    @Test fun `first day is week 1`() = assertEquals(1 to 16, AcademicsUiState(today = sem.startDate, semester = sem).semesterWeek)
    @Test fun `mid semester week`() = assertEquals(14 to 16, AcademicsUiState(today = LocalDate.of(2026, 11, 14), semester = sem).semesterWeek)
    @Test fun `outside semester is null`() = assertNull(AcademicsUiState(today = LocalDate.of(2026, 12, 5), semester = sem).semesterWeek)
}
