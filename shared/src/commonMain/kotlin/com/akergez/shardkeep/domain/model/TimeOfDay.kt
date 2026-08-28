package com.akergez.shardkeep.domain.model

/**
 * Время суток без даты и без часового пояса. В заметке стоит `⏰ 10:00-18:00` —
 * это стенные часы там, где человек находится. Зоны в markdown нет, выдумывать её
 * мы не станем: в календарь такое уезжает плавающим временем (RFC 5545, floating),
 * то есть «десять утра» остаётся десятью утра и после перелёта.
 */
data class TimeOfDay(val hour: Int, val minute: Int) : Comparable<TimeOfDay> {
    init {
        require(hour in 0..23) { "Hour must be in 0..23" }
        require(minute in 0..59) { "Minute must be in 0..59" }
    }

    val minuteOfDay: Int get() = hour * 60 + minute

    override fun compareTo(other: TimeOfDay): Int = minuteOfDay - other.minuteOfDay

    override fun toString(): String =
        "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

    companion object {
        val Midnight = TimeOfDay(0, 0)

        fun parse(value: String): TimeOfDay? {
            val match = PATTERN.matchEntire(value.trim()) ?: return null
            return runCatching {
                TimeOfDay(match.groupValues[1].toInt(), match.groupValues[2].toInt())
            }.getOrNull()
        }

        private val PATTERN = Regex("(\\d{1,2})[:.](\\d{2})")
    }
}

/**
 * Диапазон `⏰ 10:00-18:00`. Обе границы необязательны — одна из них всё же должна
 * быть, иначе диапазона нет вовсе, и парсер возвращает null вместо пустышки.
 */
data class TaskTimeSpan(val start: TimeOfDay?, val end: TimeOfDay?) {
    init {
        require(start != null || end != null) { "A time span needs at least one bound" }
    }

    /**
     * `10:00-18:00`, `10:00` для одного лишь начала, `-18:00` для одного лишь конца.
     * Начало без конца пишем без висящего дефиса: так короче и так же читается назад.
     */
    override fun toString(): String = when {
        start != null && end != null -> "$start-$end"
        start != null -> "$start"
        else -> "-$end"
    }

    companion object {
        fun parse(value: String): TaskTimeSpan? {
            val text = value.trim()
            if (text.isEmpty()) return null
            val separator = text.indexOfFirst { it == '-' || it == '–' || it == '—' }
            if (separator < 0) return TimeOfDay.parse(text)?.let { TaskTimeSpan(it, null) }
            val startText = text.substring(0, separator).trim()
            val endText = text.substring(separator + 1).trim()
            val start = if (startText.isEmpty()) null else TimeOfDay.parse(startText) ?: return null
            val end = if (endText.isEmpty()) null else TimeOfDay.parse(endText) ?: return null
            if (start == null && end == null) return null
            return TaskTimeSpan(start, end)
        }
    }
}
