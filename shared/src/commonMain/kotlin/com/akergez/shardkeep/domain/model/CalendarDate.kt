package com.akergez.shardkeep.domain.model

data class CalendarDate(
    val year: Int,
    val month: Int,
    val day: Int,
) : Comparable<CalendarDate> {
    init {
        require(month in 1..12) { "Month must be in 1..12" }
        val daysInMonth = when (month) {
            2 -> if (year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        require(day in 1..daysInMonth) { "Day is invalid for the specified month" }
    }

    override fun compareTo(other: CalendarDate): Int =
        compareValuesBy(this, other, CalendarDate::year, CalendarDate::month, CalendarDate::day)

    override fun toString(): String =
        "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}
