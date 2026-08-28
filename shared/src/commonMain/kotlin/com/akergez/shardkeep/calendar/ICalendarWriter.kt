package com.akergez.shardkeep.calendar

import com.akergez.shardkeep.domain.model.CalendarDate
import com.akergez.shardkeep.domain.model.EventMoment
import com.akergez.shardkeep.domain.model.TaskEvent
import com.akergez.shardkeep.domain.model.calendarDateFromEpochDay

/**
 * Сборка `.ics` по RFC 5545.
 *
 * Время пишется плавающим — без `Z` и без `TZID`. В заметке стоит `⏰ 10:00`, и
 * это стенные часы: ни зоны, ни смещения markdown не хранит. Придумать зону мы
 * могли бы только из текущих настроек телефона, и тогда встреча, заведённая дома,
 * переехала бы на другой час у того, кто откроет тот же файл в другой стране.
 */
object ICalendarWriter {
    private const val CRLF = "\r\n"
    private const val MILLIS_PER_DAY = 86_400_000L

    /** [nowEpochMillis] уходит в `DTSTAMP` — момент, когда файл собран. */
    fun write(events: List<TaskEvent>, nowEpochMillis: Long): String {
        val stamp = utcStampOf(nowEpochMillis)
        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//Shardkeep//Tasks//RU",
            "CALSCALE:GREGORIAN",
            "METHOD:PUBLISH",
        )
        events.forEach { event -> lines += eventLines(event, stamp) }
        lines += "END:VCALENDAR"
        return lines.joinToString(CRLF, postfix = CRLF) { fold(it) }
    }

    private fun eventLines(event: TaskEvent, stamp: String): List<String> = buildList {
        add("BEGIN:VEVENT")
        add("UID:${escape(event.uid)}")
        add("DTSTAMP:$stamp")
        add("SUMMARY:${escape(event.title)}")
        add(momentLine("DTSTART", event.start, event.allDay))
        add(momentLine("DTEND", event.end, event.allDay))
        event.recurrence?.let { add("RRULE:${it.toRRule(event.allDay)}") }
        descriptionOf(event)?.let { add("DESCRIPTION:${escape(it)}") }
        add("TRANSP:${if (event.allDay) "TRANSPARENT" else "OPAQUE"}")
        add("END:VEVENT")
    }

    private fun momentLine(name: String, moment: EventMoment, allDay: Boolean): String =
        if (allDay) {
            "$name;VALUE=DATE:${compactDate(moment.date)}"
        } else {
            val time = moment.time
            val hour = time?.hour ?: 0
            val minute = time?.minute ?: 0
            "$name:${compactDate(moment.date)}T${pad2(hour)}${pad2(minute)}00"
        }

    /**
     * Откуда событие взялось, плюс текст 🔁, который разобрать не вышло. Последнее
     * важнее, чем кажется: без повтора событие встанет в календарь один раз, и
     * строчка в описании — единственный намёк, что в заметке просили иначе.
     */
    private fun descriptionOf(event: TaskEvent): String? {
        val parts = mutableListOf<String>()
        parts += listOfNotNull(event.notePath, event.heading).joinToString(" › ")
        if (event.recurrence == null && event.recurrenceText != null) {
            parts += "🔁 ${event.recurrenceText}"
        }
        return parts.joinToString("\n").ifEmpty { null }
    }

    private fun utcStampOf(epochMillis: Long): String {
        val epochDay = epochMillis.floorDiv(MILLIS_PER_DAY)
        val millisOfDay = epochMillis.mod(MILLIS_PER_DAY)
        val secondOfDay = (millisOfDay / 1000).toInt()
        val date = calendarDateFromEpochDay(epochDay)
        return compactDate(date) +
            "T" + pad2(secondOfDay / 3600) + pad2((secondOfDay / 60) % 60) + pad2(secondOfDay % 60) + "Z"
    }

    private fun compactDate(date: CalendarDate): String =
        date.year.toString().padStart(4, '0') + pad2(date.month) + pad2(date.day)

    private fun pad2(value: Int): String = value.toString().padStart(2, '0')

    /** RFC 5545 3.3.11: в тексте экранируются обратный слэш, точка с запятой, запятая и перевод строки. */
    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")

    /**
     * Складывание длинных строк (RFC 5545 3.1): не длиннее 75 октетов, продолжение
     * начинается с пробела. Считаем именно октеты UTF-8, а не символы, и не рвём
     * суррогатную пару — иначе эмодзи в заголовке развалится на два битых кода.
     */
    private fun fold(line: String): String {
        if (utf8Length(line) <= 75) return line
        val builder = StringBuilder()
        var octets = 0
        var index = 0
        while (index < line.length) {
            val char = line[index]
            val isHighSurrogate = char.isHighSurrogate() && index + 1 < line.length
            val chunk = if (isHighSurrogate) line.substring(index, index + 2) else char.toString()
            val chunkOctets = utf8Length(chunk)
            // 75 октетов на первую строку, дальше 74 — один занимает ведущий пробел.
            val limit = if (builder.isEmpty()) 75 else 74
            if (octets + chunkOctets > limit) {
                builder.append(CRLF).append(' ')
                octets = 1
            }
            builder.append(chunk)
            octets += chunkOctets
            index += chunk.length
        }
        return builder.toString()
    }

    private fun utf8Length(value: String): Int {
        var total = 0
        var index = 0
        while (index < value.length) {
            val code = value[index].code
            when {
                code < 0x80 -> total += 1
                code < 0x800 -> total += 2
                code in 0xD800..0xDBFF && index + 1 < value.length -> {
                    total += 4
                    index++
                }
                else -> total += 3
            }
            index++
        }
        return total
    }
}
