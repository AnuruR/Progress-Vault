package com.anuru.progressvault.domain.model

import java.time.LocalDate

data class Semester(
    val id: Long,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val isActive: Boolean,
)

data class Module(
    val id: Long,
    val semesterId: Long,
    val code: String,
    val name: String,
    val accentColor: Long,
    val weeklyTargetHours: Double,
    val credits: Int?,
)

/** A module being drafted before it is saved (onboarding / new-semester flow). */
data class ModuleDraft(
    val code: String,
    val name: String,
    val accentColor: Long,
    val weeklyTargetHours: Double,
    val credits: Int?,
)
