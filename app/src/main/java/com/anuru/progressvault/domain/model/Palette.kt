package com.anuru.progressvault.domain.model

/** Neon accent palette available for sections and modules (ARGB). */
object AccentPalette {
    const val CYAN = 0xFF22D3EE
    const val GREEN = 0xFF4ADE80
    const val PURPLE = 0xFFA78BFA
    const val AMBER = 0xFFFBBF24
    const val PINK = 0xFFF472B6
    const val BLUE = 0xFF60A5FA
    /** github.com's dark-mode contribution green (brightest level). */
    const val GITHUB_GREEN = 0xFF39D353

    /** github.com dark-mode contribution graph levels 1–4. */
    val githubLevels: List<Long> = listOf(0xFF0E4429, 0xFF006D32, 0xFF26A641, GITHUB_GREEN)

    val all: List<Long> = listOf(CYAN, GREEN, PURPLE, AMBER, PINK, BLUE, GITHUB_GREEN)

    fun forIndex(index: Int): Long = all[Math.floorMod(index, all.size)]
}
