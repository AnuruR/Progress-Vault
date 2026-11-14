package com.anuru.progressvault.domain.sync

import com.anuru.progressvault.data.network.GhDay
import com.anuru.progressvault.data.network.GhEvent
import com.anuru.progressvault.data.network.GhWeek
import com.anuru.progressvault.data.network.LcDifficultyCount
import com.anuru.progressvault.data.network.LcRecentAc
import com.anuru.progressvault.data.network.LcTag
import com.anuru.progressvault.data.network.LcTagCounts
import com.anuru.progressvault.data.network.Queries
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SyncMappersTest {
    private val now = Instant.parse("2026-11-14T12:00:00Z")

    @Test fun recentSolves_dedupesKeepingEarliest_andSkipsBadTimestamps() {
        val out = SyncMappers.recentSolves(
            listOf(
                LcRecentAc("Two Sum", "two-sum", "1763000000"),
                LcRecentAc("Two Sum", "two-sum", "1762000000"),
                LcRecentAc("Bad", "bad", "nope"),
            ),
        )
        assertEquals(1, out.size)
        assertEquals(Instant.ofEpochSecond(1762000000), out.single().solvedAt)
    }

    @Test fun statsSnapshot_readsDifficulties() {
        val s = SyncMappers.statsSnapshot(
            listOf(LcDifficultyCount("All", 10), LcDifficultyCount("Easy", 5), LcDifficultyCount("Medium", 4), LcDifficultyCount("Hard", 1)),
            now,
        )
        assertEquals(listOf(5, 4, 1, 10), listOf(s.easySolved, s.mediumSolved, s.hardSolved, s.totalSolved))
    }

    @Test fun statsSnapshot_totalFallsBackToSum() {
        val s = SyncMappers.statsSnapshot(listOf(LcDifficultyCount("Easy", 2), LcDifficultyCount("Hard", 1)), now)
        assertEquals(3, s.totalSolved)
    }

    @Test fun tagCounts_flattensLevels() {
        val tags = SyncMappers.tagCounts(
            LcTagCounts(
                advanced = listOf(LcTag("DP", "dynamic-programming", 3)),
                fundamental = listOf(LcTag("Array", "array", 9)),
            ),
            now,
        )
        assertEquals(setOf("array" to "fundamental", "dynamic-programming" to "advanced"), tags.map { it.tagSlug to it.level }.toSet())
    }

    @Test fun calendarDays_parsesAndSkipsInvalid() {
        val days = SyncMappers.calendarDays(listOf(GhWeek(listOf(GhDay("2026-11-10", 4), GhDay("x", 1)))), now)
        assertEquals(1, days.size)
        assertEquals(LocalDate.of(2026, 11, 10), days[0].date)
        assertEquals(4, days[0].count)
    }

    @Test fun eventsToDays_bucketsByLocalDay_countsCommits_fillsZeros() {
        val zone = ZoneId.of("Asia/Kolkata") // UTC+5:30
        val events = listOf(
            // 20:00Z on the 12th = 01:30 on the 13th locally.
            GhEvent("PushEvent", "2026-11-12T20:00:00Z", buildJsonObject { put("size", JsonPrimitive(3)) }),
            GhEvent("PullRequestEvent", "2026-11-13T05:00:00Z", buildJsonObject { put("action", JsonPrimitive("opened")) }),
            GhEvent("PullRequestEvent", "2026-11-13T06:00:00Z", buildJsonObject { put("action", JsonPrimitive("closed")) }),
            GhEvent("WatchEvent", "2026-11-11T05:00:00Z"),
        )
        val today = LocalDate.of(2026, 11, 14)
        val days = SyncMappers.eventsToDays(events, zone, today, floor = today.minusDays(89), now = now).associate { it.date to it.count }
        assertEquals(4, days[LocalDate.of(2026, 11, 13)])
        assertEquals(0, days[LocalDate.of(2026, 11, 11)]) // non-counted event still extends the range
        assertEquals(0, days[today])
        assertEquals(4, days.size) // 11th..14th
    }

    @Test fun questionQuery_dropsUnsafeSlugs() {
        val q = Queries.leetCodeQuestions(listOf("two-sum", "evil\") { x }"))
        assertTrue(q.contains("q0: question(titleSlug: \"two-sum\")"))
        assertFalse(q.contains("evil"))
    }

    @Test fun usernames() {
        assertTrue(Usernames.isValidGitHub(""))
        assertTrue(Usernames.isValidGitHub("octo-cat"))
        assertFalse(Usernames.isValidGitHub("-bad"))
        assertFalse(Usernames.isValidLeetCode("has space"))
    }
}
