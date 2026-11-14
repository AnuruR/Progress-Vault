package com.anuru.progressvault.domain.sync

import com.anuru.progressvault.data.db.entity.GitHubContributionDayEntity
import com.anuru.progressvault.data.db.entity.LeetCodeStatsSnapshotEntity
import com.anuru.progressvault.data.db.entity.LeetCodeTagCountEntity
import com.anuru.progressvault.data.network.GhEvent
import com.anuru.progressvault.data.network.GhWeek
import com.anuru.progressvault.data.network.LcDifficultyCount
import com.anuru.progressvault.data.network.LcRecentAc
import com.anuru.progressvault.data.network.LcTagCounts
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Why a sync failed. Stored by name in SyncStateEntity.lastError and mapped to strings in the UI. */
enum class SyncError { NOT_CONFIGURED, USER_NOT_FOUND, UNAUTHORIZED, RATE_LIMITED, NETWORK, SERVER, BAD_RESPONSE }

sealed interface SyncOutcome {
    data class Success(val newItems: Int) : SyncOutcome
    data object Skipped : SyncOutcome
    data class Failed(val error: SyncError) : SyncOutcome
}

data class SolvedProblem(val slug: String, val title: String, val solvedAt: Instant)

/** Pure transformations of API payloads into DB rows (kept free of Android for unit tests). */
object SyncMappers {

    /** Dedupes by slug keeping the EARLIEST accepted time (a re-solve doesn't move the original solve). */
    fun recentSolves(list: List<LcRecentAc>): List<SolvedProblem> =
        list.mapNotNull { ac ->
            val seconds = ac.timestamp.toLongOrNull() ?: return@mapNotNull null
            SolvedProblem(ac.titleSlug, ac.title, Instant.ofEpochSecond(seconds))
        }.groupBy { it.slug }.map { (_, v) -> v.minBy { it.solvedAt } }

    fun statsSnapshot(counts: List<LcDifficultyCount>, now: Instant): LeetCodeStatsSnapshotEntity {
        fun c(d: String) = counts.firstOrNull { it.difficulty.equals(d, ignoreCase = true) }?.count ?: 0
        val easy = c("Easy"); val medium = c("Medium"); val hard = c("Hard")
        val all = counts.firstOrNull { it.difficulty.equals("All", ignoreCase = true) }?.count ?: (easy + medium + hard)
        return LeetCodeStatsSnapshotEntity(fetchedAt = now, easySolved = easy, mediumSolved = medium, hardSolved = hard, totalSolved = all)
    }

    fun tagCounts(tags: LcTagCounts, now: Instant): List<LeetCodeTagCountEntity> =
        listOf("fundamental" to tags.fundamental, "intermediate" to tags.intermediate, "advanced" to tags.advanced)
            .flatMap { (level, list) -> list.map { LeetCodeTagCountEntity(it.tagSlug, it.tagName, level, it.problemsSolved, now) } }
            .groupBy { it.tagSlug }.map { (_, v) -> v.maxBy { it.problemsSolved } }

    fun calendarDays(weeks: List<GhWeek>, now: Instant): List<GitHubContributionDayEntity> =
        weeks.flatMap { it.contributionDays }.mapNotNull { d ->
            runCatching { LocalDate.parse(d.date) }.getOrNull()?.let { GitHubContributionDayEntity(it, d.contributionCount, now) }
        }

    /** Event types that count as a contribution, approximating GitHub's own definition. */
    private val COUNTED = setOf("PushEvent", "PullRequestEvent", "IssuesEvent", "PullRequestReviewEvent", "CreateEvent")

    /**
     * Token-less fallback: buckets public events by local day. Push events count their commits.
     * Every day from the oldest fetched event (or [floor]) to [today] is emitted, so days with
     * nothing become 0 instead of keeping a stale value.
     */
    fun eventsToDays(events: List<GhEvent>, zone: ZoneId, today: LocalDate, floor: LocalDate, now: Instant): List<GitHubContributionDayEntity> {
        val counts = HashMap<LocalDate, Int>()
        var oldest = today
        events.forEach { e ->
            val date = runCatching { Instant.parse(e.createdAt).atZone(zone).toLocalDate() }.getOrNull() ?: return@forEach
            if (date.isBefore(oldest)) oldest = date
            if (e.type !in COUNTED) return@forEach
            val amount = when (e.type) {
                "PushEvent" -> ((e.payload?.get("distinct_size") ?: e.payload?.get("size")) as? JsonPrimitive)?.intOrNull?.coerceAtLeast(1) ?: 1
                "PullRequestEvent", "IssuesEvent" -> if ((e.payload?.get("action") as? JsonPrimitive)?.content == "opened") 1 else 0
                else -> 1
            }
            if (amount > 0) counts.merge(date, amount, Int::plus)
        }
        val start = maxOf(oldest, floor)
        return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }
            .map { GitHubContributionDayEntity(it, counts[it] ?: 0, now) }.toList()
    }
}
