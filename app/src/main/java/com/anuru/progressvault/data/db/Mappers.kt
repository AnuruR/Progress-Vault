package com.anuru.progressvault.data.db

import com.anuru.progressvault.data.db.entity.ModuleEntity
import com.anuru.progressvault.data.db.entity.SectionEntity
import com.anuru.progressvault.data.db.entity.SemesterEntity
import com.anuru.progressvault.domain.model.BuiltInSectionKey
import com.anuru.progressvault.domain.model.Module
import com.anuru.progressvault.domain.model.Section
import com.anuru.progressvault.domain.model.Semester

fun SectionEntity.toDomain() = Section(
    id = id,
    builtInKey = BuiltInSectionKey.fromKey(builtInKey),
    kind = kind,
    name = name,
    iconKey = iconKey,
    accentColor = accentColor,
    unit = unit,
    defaultWeeklyTarget = defaultWeeklyTarget,
    weight = weight,
    sortOrder = sortOrder,
    isBuiltIn = isBuiltIn,
    isHidden = isHidden,
    includeInDial = includeInDial,
    isAutoSynced = isAutoSynced,
)

fun SemesterEntity.toDomain() = Semester(
    id = id,
    name = name,
    startDate = startDate,
    endDate = endDate,
    isActive = isActive,
)

fun ModuleEntity.toDomain() = Module(
    id = id,
    semesterId = semesterId,
    code = code,
    name = name,
    accentColor = accentColor,
    weeklyTargetHours = weeklyTargetHours,
    credits = credits,
)
