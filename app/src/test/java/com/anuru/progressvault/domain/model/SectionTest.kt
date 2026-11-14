package com.anuru.progressvault.domain.model

import com.anuru.progressvault.data.db.entity.SectionKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionTest {
    private fun section(kind: SectionKind = SectionKind.STANDARD, include: Boolean = true, hidden: Boolean = false) =
        Section(
            id = 1, builtInKey = null, kind = kind, name = "x", iconKey = "code", accentColor = 0,
            unit = "u", defaultWeeklyTarget = 1.0, weight = 1.0, sortOrder = 0, isBuiltIn = false,
            isHidden = hidden, includeInDial = include, isAutoSynced = false,
        )

    @Test fun `zero target is excluded from dial even when included`() = assertFalse(section().countsInDial(0.0))
    @Test fun `positive target counts`() = assertTrue(section().countsInDial(2.0))
    @Test fun `derived section never counts`() = assertFalse(section(kind = SectionKind.DERIVED).countsInDial(5.0))
    @Test fun `hidden section never counts`() = assertFalse(section(hidden = true).countsInDial(5.0))
    @Test fun `excluded section never counts`() = assertFalse(section(include = false).countsInDial(5.0))
}
