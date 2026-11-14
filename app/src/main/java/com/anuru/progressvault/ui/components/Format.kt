package com.anuru.progressvault.ui.components

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** "2" for whole numbers, "1.5" otherwise — locale aware. */
fun formatAmount(value: Double): String {
    val nf = NumberFormat.getNumberInstance().apply {
        maximumFractionDigits = 1
        minimumFractionDigits = 0
    }
    return nf.format(value)
}

/** Parses user-entered numbers leniently (accepts "," or "." decimals). */
fun parseAmount(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

private val mediumDate: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val shortDayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

fun LocalDate.formatMedium(): String = format(mediumDate)
fun LocalDate.formatDayMonth(): String = format(shortDayMonth)
