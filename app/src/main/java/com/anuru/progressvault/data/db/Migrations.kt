package com.anuru.progressvault.data.db

import androidx.room.migration.Migration

/**
 * All schema migrations. The database is NEVER rebuilt destructively: every version bump
 * must add either an explicit [Migration] here or an `@AutoMigration` on
 * [ProgressVaultDatabase]. Exported schemas live in `app/schemas/` for verification.
 *
 * Version history:
 *  1 – initial schema (Phase 1).
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
