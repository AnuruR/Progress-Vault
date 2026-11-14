package com.anuru.progressvault.data.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface LeetCodeApi {
    @POST("graphql")
    suspend fun profile(@Body body: GraphQlRequest): GraphQlResponse<LcProfileData>

    /** Body uses one alias per slug (q0, q1, …), so the result is keyed by alias. */
    @POST("graphql")
    suspend fun questions(@Body body: GraphQlRequest): GraphQlResponse<Map<String, LcQuestion?>>
}

interface GitHubApi {
    @POST("graphql")
    suspend fun graphql(@Header("Authorization") auth: String, @Body body: GraphQlRequest): GraphQlResponse<GhData>

    @GET("users/{user}/events/public")
    suspend fun publicEvents(
        @Path("user") user: String,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int = 100,
    ): List<GhEvent>
}

object Queries {
    val LEETCODE_PROFILE = """
        query profile(${'$'}username: String!, ${'$'}limit: Int!) {
          matchedUser(username: ${'$'}username) {
            submitStatsGlobal { acSubmissionNum { difficulty count } }
            tagProblemCounts {
              advanced { tagName tagSlug problemsSolved }
              intermediate { tagName tagSlug problemsSolved }
              fundamental { tagName tagSlug problemsSolved }
            }
          }
          recentAcSubmissionList(username: ${'$'}username, limit: ${'$'}limit) { title titleSlug timestamp }
        }
    """.trimIndent()

    private val SLUG = Regex("^[a-z0-9-]{1,120}$")

    /** Batched question lookup. Slugs are validated before being inlined. */
    fun leetCodeQuestions(slugs: List<String>): String = buildString {
        append("query {")
        // Alias index = position in [slugs], so callers can map results back even if one is skipped.
        slugs.forEachIndexed { i, slug ->
            if (SLUG.matches(slug)) append(" q$i: question(titleSlug: \"$slug\") { difficulty topicTags { name slug } }")
        }
        append(" }")
    }

    val GITHUB_CALENDAR = """
        query calendar(${'$'}login: String!, ${'$'}from: DateTime!, ${'$'}to: DateTime!) {
          user(login: ${'$'}login) {
            contributionsCollection(from: ${'$'}from, to: ${'$'}to) {
              contributionCalendar { weeks { contributionDays { date contributionCount } } }
            }
          }
        }
    """.trimIndent()
}
