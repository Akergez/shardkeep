package com.akergez.shardkeep.calendar

import com.akergez.shardkeep.domain.model.CalendarDate
import com.akergez.shardkeep.domain.model.NotesDirectoryLocation
import com.akergez.shardkeep.domain.model.Task
import com.akergez.shardkeep.domain.model.TaskId
import com.akergez.shardkeep.domain.model.TaskStatus
import com.akergez.shardkeep.domain.model.TaskTimeSpan
import com.akergez.shardkeep.domain.model.TimeOfDay
import com.akergez.shardkeep.domain.model.toCalendarEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ICalendarWriterTest {
    /** 2026-08-28T12:34:56Z. */
    private val stampMillis = 1_787_920_496_000L

    @Test
    fun wrapsEventsIntoAValidEnvelope() {
        val lines = writeLines(task(start = CalendarDate(2026, 9, 1)))

        assertEquals("BEGIN:VCALENDAR", lines.first())
        assertEquals("END:VCALENDAR", lines.last())
        assertTrue("VERSION:2.0" in lines)
        assertTrue("PRODID:-//Shardkeep//Tasks//RU" in lines)
        assertEquals(1, lines.count { it == "BEGIN:VEVENT" })
        assertEquals(1, lines.count { it == "END:VEVENT" })
        assertTrue("DTSTAMP:20260828T123456Z" in lines)
    }

    @Test
    fun emptyCalendarStillHasAnEnvelope() {
        val text = ICalendarWriter.write(emptyList(), stampMillis)

        assertTrue(text.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(text.endsWith("END:VCALENDAR\r\n"))
        assertTrue("BEGIN:VEVENT" !in text)
    }

    @Test
    fun everyLineEndsWithCrLf() {
        val text = ICalendarWriter.write(
            listOfNotNull(task(start = CalendarDate(2026, 9, 1)).toCalendarEvent()),
            stampMillis,
        )

        assertTrue(text.endsWith("\r\n"))
        assertEquals(0, text.split("\r\n").dropLast(1).count { it.endsWith("\r") || "\n" in it })
    }

    @Test
    fun allDayEventUsesDateValuesAndExclusiveEnd() {
        val lines = writeLines(
            task(start = CalendarDate(2026, 9, 1), end = CalendarDate(2026, 9, 3)),
        )

        assertTrue("DTSTART;VALUE=DATE:20260901" in lines)
        assertTrue("DTEND;VALUE=DATE:20260904" in lines)
        assertTrue("TRANSP:TRANSPARENT" in lines)
    }

    @Test
    fun timedEventIsFloating() {
        val lines = writeLines(
            task(
                start = CalendarDate(2026, 9, 1),
                time = TaskTimeSpan(TimeOfDay(10, 0), TimeOfDay(18, 30)),
            ),
        )

        assertTrue("DTSTART:20260901T100000" in lines)
        assertTrue("DTEND:20260901T183000" in lines)
        assertTrue("TRANSP:OPAQUE" in lines)
        // Ни зоны, ни Z: markdown не хранит часовой пояс.
        assertTrue(lines.none { it.startsWith("DTSTART") && (it.endsWith("Z") || "TZID" in it) })
    }

    @Test
    fun recurrenceBecomesAnRRuleLine() {
        val lines = writeLines(
            task(start = CalendarDate(2026, 9, 1), recurrence = "every 2 weeks on Friday"),
        )

        assertTrue("RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=FR" in lines)
    }

    @Test
    fun unparseableRecurrenceGoesIntoTheDescription() {
        val lines = writeLines(
            task(start = CalendarDate(2026, 9, 1), recurrence = "every fortnight"),
        )

        assertTrue(lines.none { it.startsWith("RRULE:") })
        val description = lines.single { it.startsWith("DESCRIPTION:") }
        assertTrue("Notes/Plan.md" in description)
        assertTrue("every fortnight" in description)
        // Перевод строки в тексте экранируется, а не рвёт свойство пополам.
        assertTrue("\\n" in description)
    }

    @Test
    fun textIsEscapedPerRfc5545() {
        val lines = writeLines(task(title = "План: A, B; C \\ D", start = CalendarDate(2026, 9, 1)))

        assertTrue("SUMMARY:План: A\\, B\\; C \\\\ D" in lines)
    }

    @Test
    fun longLinesAreFoldedByOctets() {
        val title = "Совещание " + "о планировании ".repeat(8)
        val text = ICalendarWriter.write(
            listOfNotNull(task(title = title, start = CalendarDate(2026, 9, 1)).toCalendarEvent()),
            stampMillis,
        )

        val physicalLines = text.split("\r\n").dropLast(1)
        assertTrue(physicalLines.all { utf8Length(it) <= 75 })
        // Продолжения начинаются с пробела и склеиваются обратно в исходный текст.
        assertTrue(physicalLines.count { it.startsWith(" ") } > 0)
        assertTrue(unfold(text).any { it == "SUMMARY:$title" })
    }

    @Test
    fun foldingNeverSplitsASurrogatePair() {
        val title = "x".repeat(70) + "🎉".repeat(10)
        val text = ICalendarWriter.write(
            listOfNotNull(task(title = title, start = CalendarDate(2026, 9, 1)).toCalendarEvent()),
            stampMillis,
        )

        assertTrue(text.split("\r\n").none { it.isNotEmpty() && it.last().isHighSurrogate() })
        assertTrue(unfold(text).any { it == "SUMMARY:$title" })
    }

    @Test
    fun uidIsCarriedThrough() {
        val event = task(start = CalendarDate(2026, 9, 1)).toCalendarEvent()!!
        val lines = ICalendarWriter.write(listOf(event), stampMillis).split("\r\n")

        assertTrue("UID:${event.uid}" in lines)
    }

    private fun writeLines(task: Task): List<String> =
        ICalendarWriter.write(listOfNotNull(task.toCalendarEvent()), stampMillis)
            .split("\r\n")
            .dropLast(1)

    private fun unfold(text: String): List<String> {
        val result = mutableListOf<String>()
        text.split("\r\n").dropLast(1).forEach { line ->
            if (line.startsWith(" ") && result.isNotEmpty()) {
                result[result.lastIndex] = result.last() + line.drop(1)
            } else {
                result += line
            }
        }
        return result
    }

    private fun utf8Length(value: String): Int = value.encodeToByteArray().size

    private fun task(
        title: String = "Событие",
        start: CalendarDate? = null,
        end: CalendarDate? = null,
        time: TaskTimeSpan? = null,
        recurrence: String? = null,
    ) = Task(
        id = TaskId("Notes/Plan.md:0"),
        title = title,
        status = TaskStatus.Todo,
        dueDate = null,
        startDate = start,
        endDate = end,
        time = time,
        recurrence = recurrence,
        location = NotesDirectoryLocation(path = "Notes/Plan.md", heading = null, lineIndex = 0),
    )
}
