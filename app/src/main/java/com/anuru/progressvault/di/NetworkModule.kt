package com.anuru.progressvault.di

import com.anuru.progressvault.BuildConfig
import com.anuru.progressvault.data.network.GitHubApi
import com.anuru.progressvault.data.network.LeetCodeApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val USER_AGENT = "ProgressVault-Android"

    @Provides @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    @Provides @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build()) }
        .apply {
            // BASIC never logs headers, so the GitHub token can't leak into logcat.
            if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
        }
        .build()

    @Provides @Singleton
    fun leetCodeApi(client: OkHttpClient, json: Json): LeetCodeApi = Retrofit.Builder()
        .baseUrl("https://leetcode.com/")
        .client(client.newBuilder().addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("Referer", "https://leetcode.com/").build())
        }.build())
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(LeetCodeApi::class.java)

    @Provides @Singleton
    fun gitHubApi(client: OkHttpClient, json: Json): GitHubApi = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(client.newBuilder().addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .build(),
            )
        }.build())
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(GitHubApi::class.java)
}
