package com.family.mealplanner.util

import com.family.mealplanner.data.model.Weekday
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object DateUtil {
    private val range = DateTimeFormatter.ofPattern("MMM d")
    private val dayShort = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    /** The Sunday that starts the week containing [today]. */
    fun currentSunday(today: LocalDate = LocalDate.now()): LocalDate {
        val offset = today.dayOfWeek.value % 7 // MON=1..SUN=7 -> SUN=0
        return today.minusDays(offset.toLong())
    }

    /** Weekday of a date, Sunday-first to match our plan. */
    fun weekdayOf(date: LocalDate): Weekday {
        val index = date.dayOfWeek.value % 7
        return Weekday.entries[index]
    }

    fun shortLabel(date: LocalDate): String = dayShort[date.dayOfWeek.value % 7]

    fun rangeLabel(weekStart: LocalDate): String =
        "${weekStart.format(range)} – ${weekStart.plusDays(6).format(range)}"

    /** "today" / "yesterday" / "N days ago" for repeat alerts. */
    fun daysAgoLabel(days: Long): String = when (days) {
        0L -> "today"
        1L -> "yesterday"
        else -> "$days days ago"
    }
}
