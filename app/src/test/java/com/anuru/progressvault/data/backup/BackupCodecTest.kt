package com.anuru.progressvault.data.backup

import com.anuru.progressvault.data.db.entity.PlannerTaskEntity
import com.anuru.progressvault.data.db.entity.SectionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

class BackupCodecTest {
    private val now = Instant.parse("2026-11-14T10:15:30Z")
    private val file = BackupFile(
        schemaVersion = 1,
        exportedAt = now.toString(),
        settings = BackupSettings(leetCodeUsername = "lc", gitHubUsername = "gh", firstLaunchEpochDay = 20_000),
        tables = BackupTables(
            sections = listOf(SectionEntity(id = 3, name = "Reading", iconKey = "book", accentColor = 0xFF39D353, unit = "pages",
                defaultWeeklyTarget = 50.0, weight = 1.5, sortOrder = 2, createdAt = now)),
            plannerTasks = listOf(PlannerTaskEntity(id = 7, title = "Essay", weekStart = LocalDate.of(2026, 11, 9),
                day = DayOfWeek.THURSDAY, createdAt = now)),
        ),
    )

    @Test fun roundTripPreservesEverything() {
        val decoded = BackupCodec.decode(BackupCodec.encode(file))
        assertEquals(DecodeResult.Ok(file), decoded)
    }

    @Test fun tokenFieldNeverPresent() {
        assertTrue("token" !in BackupCodec.encode(file).lowercase())
    }

    @Test fun rejectsForeignJson() {
        assertEquals(DecodeResult.NotABackup, BackupCodec.decode("""{"hello":"world"}"""))
        assertEquals(DecodeResult.NotABackup, BackupCodec.decode("not json"))
    }

    @Test fun rejectsNewerVersion() {
        assertEquals(DecodeResult.TooNew, BackupCodec.decode(BackupCodec.encode(file.copy(version = 99))))
    }

    @Test fun truncatedBackupIsCorrupt() {
        val text = BackupCodec.encode(file)
        assertEquals(DecodeResult.Corrupt, BackupCodec.decode(text.take(text.length / 2)))
    }

    @Test fun missingTablesDecodeAsEmpty() {
        val minimal = """{"format":"progress-vault-backup","version":1,"schemaVersion":1,"exportedAt":"x","settings":{},"tables":{}}"""
        val r = BackupCodec.decode(minimal) as DecodeResult.Ok
        assertTrue(r.file.tables.sections.isEmpty())
    }
}
