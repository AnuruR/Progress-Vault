package com.anuru.progressvault.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class GraphQlRequest(val query: String, val variables: JsonObject = JsonObject(emptyMap()))

@Serializable
data class GraphQlResponse<T>(val data: T? = null, val errors: List<GraphQlError>? = null)

@Serializable
data class GraphQlError(val message: String = "")

// ---- LeetCode (public GraphQL at leetcode.com/graphql) ----

@Serializable
data class LcProfileData(
    val matchedUser: LcUser? = null,
    val recentAcSubmissionList: List<LcRecentAc>? = null,
)

@Serializable
data class LcUser(
    val submitStatsGlobal: LcSubmitStats? = null,
    val tagProblemCounts: LcTagCounts? = null,
)

@Serializable
data class LcSubmitStats(val acSubmissionNum: List<LcDifficultyCount> = emptyList())

@Serializable
data class LcDifficultyCount(val difficulty: String, val count: Int)

@Serializable
data class LcTagCounts(
    val advanced: List<LcTag> = emptyList(),
    val intermediate: List<LcTag> = emptyList(),
    val fundamental: List<LcTag> = emptyList(),
)

@Serializable
data class LcTag(val tagName: String, val tagSlug: String, val problemsSolved: Int)

@Serializable
data class LcRecentAc(val title: String, val titleSlug: String, val timestamp: String)

@Serializable
data class LcQuestion(val difficulty: String? = null, val topicTags: List<LcTopicTag> = emptyList())

@Serializable
data class LcTopicTag(val name: String, val slug: String = "")

// ---- GitHub ----

@Serializable
data class GhData(val user: GhUser? = null)

@Serializable
data class GhUser(val contributionsCollection: GhContributions)

@Serializable
data class GhContributions(val contributionCalendar: GhCalendar)

@Serializable
data class GhCalendar(val weeks: List<GhWeek> = emptyList())

@Serializable
data class GhWeek(val contributionDays: List<GhDay> = emptyList())

@Serializable
data class GhDay(val date: String, val contributionCount: Int)

/** Public event from the REST events API (used when no token is stored). */
@Serializable
data class GhEvent(
    val type: String = "",
    @SerialName("created_at") val createdAt: String,
    val payload: JsonObject? = null,
)
