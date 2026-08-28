package com.akergez.shardkeep.domain.model

data class CalendarDate(
    val year: Int,
    val month: Int,
    val day: Int,
) : Comparable<CalendarDate> {
    init {
        require(month in 1..12) { "Month must be in 1..12" }
        require(day in 1..daysInMonth(year, month)) { "Day is invalid for the specified month" }
    }

    override fun compareTo(other: CalendarDate): Int =
        compareValuesBy(this, other, CalendarDate::year, CalendarDate::month, CalendarDate::day)

    override fun toString(): String =
        "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

    companion object {
        fun parse(value: String): CalendarDate? {
            val match = ISO_DATE.matchEntire(value.trim()) ?: return null
            return runCatching {
                CalendarDate(
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    match.groupValues[3].toInt(),
                )
            }.getOrNull()
        }

        private val ISO_DATE = Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})")
    }
}

/** Понедельник = 1 … воскресенье = 7, как в ISO-8601 и в BYDAY у RFC 5545. */
val CalendarDate.isoDayOfWeek: Int get() = ((toEpochDay() + 3).mod(7L)).toInt() + 1

/**
 * Дней с 1970-01-01. Алгоритм Хиннанта (`days_from_civil`): март берётся за начало
 * года, тогда високосный день оказывается в хвосте и деления обходятся без ветвлений.
 */
fun CalendarDate.toEpochDay(): Long {
    val shiftedYear = year - if (month <= 2) 1 else 0
    val era = if (shiftedYear >= 0) shiftedYear / 400 else (shiftedYear - 399) / 400
    val yearOfEra = shiftedYear - era * 400
    val shiftedMonth = month + if (month > 2) -3 else 9
    val dayOfYear = (153 * shiftedMonth + 2) / 5 + day - 1
    val dayOfEra = yearOfEra * 365L + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    return era * 146097L + dayOfEra - 719468L
}

/** Обратное к [toEpochDay]. */
fun calendarDateFromEpochDay(epochDay: Long): CalendarDate {
    val shifted = epochDay + 719468L
    val era = if (shifted >= 0) shifted / 146097 else (shifted - 146096) / 146097
    val dayOfEra = shifted - era * 146097
    val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val shiftedMonth = (5 * dayOfYear + 2) / 153
    val day = (dayOfYear - (153 * shiftedMonth + 2) / 5 + 1).toInt()
    val month = (shiftedMonth + if (shiftedMonth < 10) 3 else -9).toInt()
    val year = (yearOfEra + era * 400 + if (month <= 2) 1 else 0).toInt()
    return CalendarDate(year, month, day)
}

fun CalendarDate.plusDays(days: Int): CalendarDate = calendarDateFromEpochDay(toEpochDay() + days)

internal fun daysInMonth(year: Int, month: Int): Int = when (month) {
    2 -> if (isLeapYear(year)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

internal fun isLeapYear(year: Int): Boolean = year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)
