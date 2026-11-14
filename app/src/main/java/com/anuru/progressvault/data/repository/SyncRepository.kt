package com.anuru.progressvault.data.repository

import com.anuru.progressvault.data.db.dao.SyncDao
import com.anuru.progressvault.data.db.entity.LeetCodeProblemEntity
import com.anuru.progressvault.data.db.entity.SyncSource
import com.anuru.progressvault.data.db.entity.SyncStateEntity
import com.anuru.progressvault.data.network.GitHubApi
import com.anuru.progressvault.data.network.GraphQlRequest
import com.anuru.progressvault.data.network.LeetCodeApi
import com.anuru.progressvault.data.network.Queries
import com.anuru.progressvault.data.security.TokenStore
import com.anuru.progressvault.data.settings.SettingsRepository
import com.anuru.progressvault.domain.sync.SyncError
import com.anuru.progressvault.domain.sync.SyncMappers
import com.anuru.progressvault.domain.sync.SyncOutcome
import com.anuru.progressvault.domain.time.TimeProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pulls LeetCode / GitHub activity into Room. Additive and offline-first: the UI only reads
 * Room, a failed sync never deletes data, and every attempt is recorded in sync_states.
 */
@Singleton
class SyncRepository @Inject constructor(
    private val leetCodeApi: LeetCodeApi,
    private val gitHubApi: GitHubApi,
    private val syncDao: SyncDao,
    private val settings: SettingsRepository,
    private val tokenStore: TokenStore,
    private val time: TimeProvider,
) {
    private val mutex = Mutex()
    private val _running = MutableStateFlow<Set<SyncSource>>(emptySet())
    /** Sources currently syncing, for spinners. */
    val running: StateFlow<Set<SyncSource>> = _running.asStateFlow()

    suspend fun syncAll(): Map<SyncSource, SyncOutcome> =
        mapOf(SyncSource.LEETCODE to syncLeetCode(), SyncSource.GITHUB to syncGitHub())

    suspend fun syncLeetCode(): SyncOutcome {
        val username = settings.current().leetCodeUsername
        if (username.isBlank()) return SyncOutcome.Skipped
        return track(SyncSource.LEETCODE) {
            val now = now()
            val vars = buildJsonObject {
                put("username", JsonPrimitive(username))
                put("limit", JsonPrimitive(RECENT_LIMIT))
            }
            val response = leetCodeApi.profile(GraphQlRequest(Queries.LEETCODE_PROFILE, vars))
            val data = response.data ?: throw SyncFailure(SyncError.BAD_RESPONSE)
            val user = data.matchedUser ?: throw SyncFailure(SyncError.USER_NOT_FOUND)

            val solves = SyncMappers.recentSolves(data.recentAcSubmissionList.orEmpty())
            val inserted = syncDao.insertProblems(
                solves.map { LeetCodeProblemEntity(it.slug, it.title, difficulty = null, topicTags = emptyList(), solvedAt = it.solvedAt) },
            ).count { it != -1L }

            user.submitStatsGlobal?.let { stats ->
                val snapshot = SyncMappers.statsSnapshot(stats.acSubmissionNum, now)
                val latest = syncDao.getLatestStats()
                // Only keep a new snapshot when totals change, so the table stays small.
                if (latest == null || latest.totalSolved != snapshot.totalSolved || latest.easySolved != snapshot.easySolved ||
                    latest.mediumSolved != snapshot.mediumSolved || latest.hardSolved != snapshot.hardSolved
                ) syncDao.insertStatsSnapshot(snapshot)
            }
            user.tagProblemCounts?.let { syncDao.upsertTagCounts(SyncMappers.tagCounts(it, now)) }

            // Best-effort enrichment: difficulty + topic tags for problems that lack them.
            runCatching { fillProblemDetails() }.onFailure { if (it is CancellationException) throw it }
            inserted
        }
    }

    private suspend fun fillProblemDetails() {
        val missing = syncDao.getProblemsMissingDetails(DETAILS_BATCH)
        if (missing.isEmpty()) return
        val slugs = missing.map { it.slug }
        val result = leetCodeApi.questions(GraphQlRequest(Queries.leetCodeQuestions(slugs))).data ?: return
        val updated = missing.mapIndexedNotNull { i, p ->
            val q = result["q$i"] ?: return@mapIndexedNotNull null
            p.copy(difficulty = q.difficulty?.uppercase(), topicTags = q.topicTags.map { it.name })
        }
        if (updated.isNotEmpty()) syncDao.updateProblems(updated)
    }

    suspend fun syncGitHub(): SyncOutcome {
        val username = settings.current().gitHubUsername
        if (username.isBlank()) return SyncOutcome.Skipped
        return track(SyncSource.GITHUB) {
            val now = now()
            val token = tokenStore.gitHubToken()
            val days = if (token != null) {
                val to = now.atOffset(ZoneOffset.UTC)
                val vars = buildJsonObject {
                    put("login", JsonPrimitive(username))
                    put("from", JsonPrimitive(to.minusDays(364).toString()))
                    put("to", JsonPrimitive(to.toString()))
                }
                val response = gitHubApi.graphql("Bearer $token", GraphQlRequest(Queries.GITHUB_CALENDAR, vars))
                val user = response.data?.user
                    ?: throw SyncFailure(if (response.errors.isNullOrEmpty()) SyncError.BAD_RESPONSE else SyncError.USER_NOT_FOUND)
                SyncMappers.calendarDays(user.contributionsCollection.contributionCalendar.weeks, now)
            } else {
                val events = (1..EVENT_PAGES).flatMap { page ->
                    gitHubApi.publicEvents(username, page).also { if (it.isEmpty()) return@flatMap it }
                }
                val zone = time.clock().zone
                val today = LocalDate.now(time.clock())
                SyncMappers.eventsToDays(events, zone, today, floor = today.minusDays(89), now = now)
            }
            syncDao.upsertContributionDays(days)
            days.count { it.count > 0 }
        }
    }

    private suspend fun track(source: SyncSource, block: suspend () -> Int): SyncOutcome = mutex.withLock {
        _running.update { it + source }
        val previous = syncDao.getSyncState(source) ?: SyncStateEntity(source)
        val attemptAt = now()
        try {
            val newItems = block()
            syncDao.upsertSyncState(previous.copy(lastAttemptAt = attemptAt, lastSuccessAt = now(), lastError = null))
            SyncOutcome.Success(newItems)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = classify(e)
            syncDao.upsertSyncState(previous.copy(lastAttemptAt = attemptAt, lastError = error.name))
            SyncOutcome.Failed(error)
        } finally {
            _running.update { it - source }
        }
    }

    private fun classify(e: Exception): SyncError = when (e) {
        is SyncFailure -> e.error
        is HttpException -> when (e.code()) {
            401 -> SyncError.UNAUTHORIZED
            403, 429 -> SyncError.RATE_LIMITED
            404 -> SyncError.USER_NOT_FOUND
            in 500..599 -> SyncError.SERVER
            else -> SyncError.BAD_RESPONSE
        }
        is IOException -> SyncError.NETWORK
        is SerializationException, is IllegalArgumentException -> SyncError.BAD_RESPONSE
        else -> SyncError.BAD_RESPONSE
    }

    private fun now(): Instant = time.clock().instant()

    private class SyncFailure(val error: SyncError) : Exception(error.name)

    private companion object {
        /** LeetCode's public API caps recent accepted submissions at 20. */
        const val RECENT_LIMIT = 20
        const val DETAILS_BATCH = 20
        /** Public events API returns at most 300 events / 90 days. */
        const val EVENT_PAGES = 3
    }
}
