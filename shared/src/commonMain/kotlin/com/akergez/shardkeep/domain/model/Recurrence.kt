package com.akergez.shardkeep.domain.model

enum class RecurrenceFrequency(val rruleName: String) {
    Daily("DAILY"),
    Weekly("WEEKLY"),
    Monthly("MONTHLY"),
    Yearly("YEARLY"),
}

enum class Weekday(val rruleName: String, val isoNumber: Int) {
    Monday("MO", 1),
    Tuesday("TU", 2),
    Wednesday("WE", 3),
    Thursday("TH", 4),
    Friday("FR", 5),
    Saturday("SA", 6),
    Sunday("SU", 7),
}

/**
 * Элемент `BYDAY`. Без порядкового номера это просто «пятница», с ним — «вторая
 * пятница месяца», а `-1` считает от конца: `-1FR` — последняя пятница.
 */
data class WeekdayOccurrence(val weekday: Weekday, val ordinal: Int? = null) {
    override fun toString(): String =
        if (ordinal == null) weekday.rruleName else "$ordinal${weekday.rruleName}"
}

/**
 * Правило повторения в терминах RFC 5545. Промежуточное представление между
 * почти-английским текстом плагина Tasks и строкой `RRULE`, которую ждут и iCal,
 * и `CalendarContract`.
 */
data class RecurrenceRule(
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val byMonth: List<Int> = emptyList(),
    val byMonthDay: List<Int> = emptyList(),
    val byDay: List<WeekdayOccurrence> = emptyList(),
    val count: Int? = null,
    val until: CalendarDate? = null,
) {
    /**
     * `UNTIL` обязан совпадать по типу с `DTSTART` (RFC 5545, 3.3.10): у события на
     * весь день — голая дата, у события со временем — дата со временем. Наш `DTSTART`
     * плавающий, поэтому и здесь никакого `Z`.
     */
    fun toRRule(allDay: Boolean): String = buildString {
        append("FREQ=").append(frequency.rruleName)
        if (interval > 1) append(";INTERVAL=").append(interval)
        if (byMonth.isNotEmpty()) append(";BYMONTH=").append(byMonth.joinToString(","))
        if (byMonthDay.isNotEmpty()) append(";BYMONTHDAY=").append(byMonthDay.joinToString(","))
        if (byDay.isNotEmpty()) append(";BYDAY=").append(byDay.joinToString(","))
        count?.let { append(";COUNT=").append(it) }
        until?.let {
            append(";UNTIL=")
            append(it.year.toString().padStart(4, '0'))
            append(it.month.toString().padStart(2, '0'))
            append(it.day.toString().padStart(2, '0'))
            if (!allDay) append("T235959")
        }
    }
}

/**
 * Разбирает текст после 🔁 (он же `[repeat:: …]`). Плагин Tasks кормит этой строкой
 * `rrule.js`, чей человеческий синтаксис мы и повторяем.
 *
 * Разбор намеренно всё-или-ничего: встретив незнакомое слово, парсер возвращает
 * null, и вызывающий код выгружает таску одиночным событием. Наполовину понятое
 * правило разложило бы встречи по неверным дням, а это хуже, чем не повторять их
 * вовсе — молчаливо неправильный календарь дороже очевидно неполного.
 */
object RecurrenceTextParser {
    fun parse(text: String): RecurrenceRule? {
        val tokens = tokenize(text)
        if (tokens.firstOrNull() != "every") return null

        var cursor = 1
        var interval = 1
        tokens.getOrNull(cursor)?.toIntOrNull()?.let { parsed ->
            if (parsed < 1) return null
            interval = parsed
            cursor++
        }

        val byMonth = mutableListOf<Int>()
        val byMonthDay = mutableListOf<Int>()
        val byDay = mutableListOf<WeekdayOccurrence>()

        val unit = tokens.getOrNull(cursor) ?: return null
        val frequency = when (unit) {
            "day", "days" -> RecurrenceFrequency.Daily.also { cursor++ }
            "week", "weeks" -> RecurrenceFrequency.Weekly.also { cursor++ }
            "month", "months" -> RecurrenceFrequency.Monthly.also { cursor++ }
            "year", "years" -> RecurrenceFrequency.Yearly.also { cursor++ }
            "weekday", "weekdays" -> RecurrenceFrequency.Weekly.also {
                byDay += WORKING_WEEK.map { day -> WeekdayOccurrence(day) }
                cursor++
            }
            else -> when {
                // «every monday», «every january» — единица измерения подразумевается днём.
                WEEKDAYS.containsKey(unit) -> RecurrenceFrequency.Weekly.also {
                    cursor = readWeekdays(tokens, cursor, byDay) ?: return null
                }
                MONTHS.containsKey(unit) -> RecurrenceFrequency.Yearly.also {
                    cursor = readMonths(tokens, cursor, byMonth) ?: return null
                }
                else -> return null
            }
        }

        var count: Int? = null
        var until: CalendarDate? = null

        while (cursor < tokens.size) {
            when (tokens[cursor]) {
                "on" -> {
                    cursor++
                    cursor = if (tokens.getOrNull(cursor) == "the") {
                        readOrdinals(tokens, cursor + 1, byMonthDay, byDay) ?: return null
                    } else {
                        readWeekdays(tokens, cursor, byDay) ?: return null
                    }
                }
                "in" -> cursor = readMonths(tokens, cursor + 1, byMonth) ?: return null
                "for" -> {
                    val amount = tokens.getOrNull(cursor + 1)?.toIntOrNull() ?: return null
                    if (amount < 1) return null
                    count = amount
                    cursor += 2
                    if (tokens.getOrNull(cursor) in TIMES_WORDS) cursor++
                }
                "until" -> {
                    until = tokens.getOrNull(cursor + 1)?.let { CalendarDate.parse(it) } ?: return null
                    cursor += 2
                }
                // Хвост Tasks «when done»: отсчёт от дня выполнения, а не от плана.
                // На форму самого правила это не влияет, поэтому просто проглатываем.
                "when" -> {
                    if (tokens.getOrNull(cursor + 1) != "done") return null
                    cursor += 2
                }
                // «on the last friday of the month»: «of» уже выброшено как шум, а
                // хвост лишь повторяет период правила. Глотаем его только когда он
                // и вправду совпадает с частотой — «every day of the week» пусть
                // лучше не разберётся, чем разберётся не так.
                "the" -> {
                    val unit = tokens.getOrNull(cursor + 1) ?: return null
                    if (UNIT_FREQUENCIES[unit] != frequency) return null
                    cursor += 2
                }
                in UNIT_FREQUENCIES.keys -> {
                    if (UNIT_FREQUENCIES[tokens[cursor]] != frequency) return null
                    cursor++
                }
                else -> return null
            }
        }

        if (count != null && until != null) return null

        return RecurrenceRule(
            frequency = frequency,
            interval = interval,
            byMonth = byMonth.distinct(),
            byMonthDay = byMonthDay.distinct(),
            byDay = byDay.distinct(),
            count = count,
            until = until,
        )
    }

    /** Считывает подряд идущие названия дней недели. */
    private fun readWeekdays(
        tokens: List<String>,
        start: Int,
        out: MutableList<WeekdayOccurrence>,
    ): Int? {
        var cursor = start
        while (cursor < tokens.size) {
            val weekday = WEEKDAYS[tokens[cursor]] ?: break
            out += WeekdayOccurrence(weekday)
            cursor++
        }
        return cursor.takeIf { it > start }
    }

    private fun readMonths(tokens: List<String>, start: Int, out: MutableList<Int>): Int? {
        var cursor = start
        while (cursor < tokens.size) {
            val month = MONTHS[tokens[cursor]] ?: break
            out += month
            cursor++
        }
        return cursor.takeIf { it > start }
    }

    /**
     * Хвост после «on the»: либо числа месяца («15th», «1st»), либо порядковый день
     * недели («last friday», «2nd tuesday»). Что именно — решает слово следом.
     */
    private fun readOrdinals(
        tokens: List<String>,
        start: Int,
        byMonthDay: MutableList<Int>,
        byDay: MutableList<WeekdayOccurrence>,
    ): Int? {
        var cursor = start
        while (cursor < tokens.size) {
            val ordinal = parseOrdinal(tokens[cursor]) ?: break
            cursor++
            val weekday = tokens.getOrNull(cursor)?.let { WEEKDAYS[it] }
            when {
                weekday != null -> {
                    byDay += WeekdayOccurrence(weekday, ordinal)
                    cursor++
                }
                // «on the last day» — последнее число месяца, какое бы оно ни было.
                ordinal == -1 && tokens.getOrNull(cursor) in DAY_WORDS -> {
                    byMonthDay += -1
                    cursor++
                }
                ordinal in 1..31 -> byMonthDay += ordinal
                else -> return null
            }
        }
        return cursor.takeIf { it > start }
    }

    private fun parseOrdinal(token: String): Int? {
        WORD_ORDINALS[token]?.let { return it }
        val match = NUMERIC_ORDINAL.matchEntire(token) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    /**
     * Запятые и «and» — только разделители перечислений, а «of» встречается в
     * «on the last friday of the month»; всё это выкидываем, чтобы дальше разбирать
     * ровный поток значимых слов.
     */
    private fun tokenize(text: String): List<String> = text
        .lowercase()
        .split(' ', ',', '\t', '\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() && it !in NOISE_WORDS }

    private val NOISE_WORDS = setOf("and", "of")
    private val UNIT_FREQUENCIES = mapOf(
        "day" to RecurrenceFrequency.Daily,
        "days" to RecurrenceFrequency.Daily,
        "week" to RecurrenceFrequency.Weekly,
        "weeks" to RecurrenceFrequency.Weekly,
        "month" to RecurrenceFrequency.Monthly,
        "months" to RecurrenceFrequency.Monthly,
        "year" to RecurrenceFrequency.Yearly,
        "years" to RecurrenceFrequency.Yearly,
    )
    private val TIMES_WORDS = setOf("time", "times")
    private val DAY_WORDS = setOf("day", "days")
    private val WORKING_WEEK = listOf(
        Weekday.Monday,
        Weekday.Tuesday,
        Weekday.Wednesday,
        Weekday.Thursday,
        Weekday.Friday,
    )
    private val NUMERIC_ORDINAL = Regex("(\\d{1,2})(?:st|nd|rd|th)?")
    private val WORD_ORDINALS = mapOf(
        "first" to 1,
        "second" to 2,
        "third" to 3,
        "fourth" to 4,
        "fifth" to 5,
        "last" to -1,
    )
    private val WEEKDAYS: Map<String, Weekday> = buildMap {
        put("monday", Weekday.Monday); put("mon", Weekday.Monday)
        put("tuesday", Weekday.Tuesday); put("tue", Weekday.Tuesday); put("tues", Weekday.Tuesday)
        put("wednesday", Weekday.Wednesday); put("wed", Weekday.Wednesday)
        put("thursday", Weekday.Thursday); put("thu", Weekday.Thursday); put("thur", Weekday.Thursday)
        put("friday", Weekday.Friday); put("fri", Weekday.Friday)
        put("saturday", Weekday.Saturday); put("sat", Weekday.Saturday)
        put("sunday", Weekday.Sunday); put("sun", Weekday.Sunday)
    }
    private val MONTHS: Map<String, Int> = buildMap {
        val names = listOf(
            "january" to "jan", "february" to "feb", "march" to "mar", "april" to "apr",
            "may" to "may", "june" to "jun", "july" to "jul", "august" to "aug",
            "september" to "sep", "october" to "oct", "november" to "nov", "december" to "dec",
        )
        names.forEachIndexed { index, (full, short) ->
            put(full, index + 1)
            put(short, index + 1)
        }
    }
}
