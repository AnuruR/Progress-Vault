package com.anuru.progressvault.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Non-secret settings carried in a backup. The GitHub token is never exported. */
@Serializable
data class BackupSettings(
    val leetCodeUsername: String = "",
    val gitHubUsername: String = "",
    val firstLaunchEpochDay: Long? = null,
    val syncIntervalHours: Int = 6,
    val reminderMinuteOfDay: Int = 20 * 60,
    val dailyReminder: Boolean = true,
    val paceNudge: Boolean = true,
    val weeklySummary: Boolean = true,
    val monthlyRecap: Boolean = true,
)

@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    /** Room schema version the data came from. */
    val schemaVersion: Int,
    val exportedAt: String,
    val settings: BackupSettings,
    val tables: BackupTables,
) {
    companion object {
        const val FORMAT = "progress-vault-backup"
        const val VERSION = 1
    }
}

sealed interface DecodeResult {
    data class Ok(val file: BackupFile) : DecodeResult
    data object NotABackup : DecodeResult
    data object TooNew : DecodeResult
    data object Corrupt : DecodeResult
}

/** Pure JSON encode/decode + validation (unit-tested). */
object BackupCodec {
    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true // newer optional fields don't break older apps
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun decode(text: String): DecodeResult {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (_: SerializationException) {
            return if (text.contains(BackupFile.FORMAT)) DecodeResult.Corrupt else DecodeResult.NotABackup
        } catch (_: IllegalArgumentException) {
            return DecodeResult.Corrupt
        } catch (_: java.time.DateTimeException) {
            return DecodeResult.Corrupt
        }
        if (file.format != BackupFile.FORMAT) return DecodeResult.NotABackup
        if (file.version > BackupFile.VERSION) return DecodeResult.TooNew
        return DecodeResult.Ok(file)
    }
}
