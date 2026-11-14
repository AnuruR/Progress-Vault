package com.anuru.progressvault.domain.planner

import com.anuru.progressvault.data.db.entity.TaskPriority
import com.anuru.progressvault.domain.time.WeekRange
import java.time.DayOfWeek
import java.time.LocalDate

/** Minimal view of an unfinished task for balancing. */
data class BalanceTask(
    val id: Long,
    val day: DayOfWeek?,
    val priority: TaskPriority,
    /** Linked deadline date, if any. */
    val deadline: LocalDate?,
)

data class TaskMove(val taskId: Long, val from: DayOfWeek?, val to: DayOfWeek)

/**
 * Rule-based planner balancing (no AI):
 *  1. Overdue tasks (scheduled on a day before today) move to today.
 *  2. Every other unfinished task is re-spread over the remaining days (today..Sunday),
 *     nearest deadline first, then higher priority, each going to the least-loaded day
 *     that is not after its deadline. Ties keep the task's current day, else the earliest day.
 * Only returns tasks whose day actually changes.
 */
object AutoBalancer {

    fun plan(tasks: List<BalanceTask>, week: WeekRange, today: LocalDate): List<TaskMove> {
        if (today.isAfter(week.end)) return emptyList() // past week: nothing left to balance
        val firstDay = if (today.isBefore(week.start)) week.start else today
        val remaining = generateSequence(firstDay) { it.plusDays(1) }.takeWhile { !it.isAfter(week.end) }.toList()
        val load = remaining.associateWith { 0 }.toMutableMap()
        val assignment = mutableMapOf<Long, LocalDate>()

        fun dateOf(d: DayOfWeek) = week.start.plusDays((d.value - 1).toLong())

        val (overdue, flexible) = tasks.partition { t -> t.day != null && dateOf(t.day).isBefore(firstDay) }
        overdue.forEach { t ->
            assignment[t.id] = firstDay
            load[firstDay] = load.getValue(firstDay) + 1
        }

        val ordered = flexible.sortedWith(
            compareBy<BalanceTask>({ it.deadline ?: LocalDate.MAX })
                .thenByDescending { it.priority.ordinal }
                .thenBy { it.day?.value ?: Int.MAX_VALUE }
                .thenBy { it.id },
        )
        ordered.forEach { t ->
            val latest = t.deadline?.let { d -> if (d.isBefore(firstDay)) firstDay else minOf(d, week.end) } ?: week.end
            val candidates = remaining.filter { !it.isAfter(latest) }
            val minLoad = candidates.minOf { load.getValue(it) }
            val best = candidates.filter { load.getValue(it) == minLoad }
            // Among equally loaded days, keep the task where the user put it.
            val current = t.day?.let(::dateOf)
            val target = if (current != null && current in best) current else best.first()
            assignment[t.id] = target
            load[target] = load.getValue(target) + 1
        }

        return tasks.mapNotNull { t ->
            val to = assignment.getValue(t.id).dayOfWeek
            if (to == t.day) null else TaskMove(t.id, t.day, to)
        }
    }
}
