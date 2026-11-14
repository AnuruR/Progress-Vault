package com.anuru.progressvault.domain.planner

import com.anuru.progressvault.data.db.entity.TaskPriority
import com.anuru.progressvault.domain.time.WeekRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate

class AutoBalancerTest {
    private val week = WeekRange(LocalDate.of(2026, 11, 9))
    private val thursday = LocalDate.of(2026, 11, 12)
    private fun t(id: Long, day: java.time.DayOfWeek? = null, p: TaskPriority = TaskPriority.MEDIUM, deadline: LocalDate? = null) = BalanceTask(id, day, p, deadline)

    private fun finalDays(tasks: List<BalanceTask>, today: LocalDate): Map<Long, java.time.DayOfWeek?> {
        val moves = AutoBalancer.plan(tasks, week, today).associate { it.taskId to it.to }
        return tasks.associate { it.id to (moves[it.id] ?: it.day) }
    }

    @Test fun `overdue tasks move to today`() {
        val result = finalDays(listOf(t(1, MONDAY), t(2, TUESDAY)), thursday)
        assertEquals(THURSDAY, result[1]); assertEquals(THURSDAY, result[2])
    }

    @Test fun `remaining tasks spread evenly over remaining days`() {
        // Thu..Sun = 4 days, 8 tasks -> 2 per day
        val result = finalDays((1L..8L).map { t(it) }, thursday)
        val perDay = result.values.groupingBy { it }.eachCount()
        assertEquals(setOf(THURSDAY, FRIDAY, SATURDAY, SUNDAY), perDay.keys)
        assertTrue(perDay.values.all { it == 2 })
    }

    @Test fun `nearest deadline is scheduled first and never after its deadline`() {
        val tasks = listOf(t(1), t(2, deadline = LocalDate.of(2026, 11, 13)), t(3, deadline = LocalDate.of(2026, 11, 12)))
        val result = finalDays(tasks, thursday)
        assertEquals(THURSDAY, result[3])                // due Thu
        assertTrue(result[2]!!.value <= FRIDAY.value)    // due Fri
    }

    @Test fun `already overdue deadline goes to today`() {
        val result = finalDays(listOf(t(1, SATURDAY, deadline = LocalDate.of(2026, 11, 10))), thursday)
        assertEquals(THURSDAY, result[1])
    }

    @Test fun `high priority gets the earliest free slot among equals`() {
        val result = finalDays(listOf(t(1, p = TaskPriority.LOW), t(2, p = TaskPriority.HIGH)), thursday)
        assertEquals(THURSDAY, result[2])
        assertEquals(FRIDAY, result[1])
    }

    @Test fun `unchanged tasks produce no moves`() {
        assertTrue(AutoBalancer.plan(listOf(t(1, THURSDAY)), week, thursday).isEmpty())
    }

    @Test fun `future week uses all seven days and past week does nothing`() {
        val future = finalDays((1L..7L).map { t(it) }, week.start.minusDays(3))
        assertEquals(7, future.values.toSet().size)
        assertTrue(AutoBalancer.plan(listOf(t(1)), week, week.end.plusDays(1)).isEmpty())
    }

    @Test fun `a task stays on its day when that day is among the least loaded`() {
        assertTrue(AutoBalancer.plan(listOf(t(1, WEDNESDAY)), week, week.start).isEmpty())
    }
}
