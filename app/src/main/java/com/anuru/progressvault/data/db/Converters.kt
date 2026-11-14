package com.anuru.progressvault.data.db

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/** Dates as epoch-day, instants as epoch-millis, string lists as JSON arrays. */
class Converters {
    @TypeConverter fun localDateToLong(value: LocalDate?): Long? = value?.toEpochDay()
    @TypeConverter fun longToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter fun dayOfWeekToInt(value: DayOfWeek?): Int? = value?.value
    @TypeConverter fun intToDayOfWeek(value: Int?): DayOfWeek? = value?.let(DayOfWeek::of)

    @TypeConverter fun stringListToJson(value: List<String>): String =
        json.encodeToString(stringList, value)

    @TypeConverter fun jsonToStringList(value: String): List<String> =
        runCatching { json.decodeFromString(stringList, value) }.getOrDefault(emptyList())

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val stringList = ListSerializer(String.serializer())
    }
}
