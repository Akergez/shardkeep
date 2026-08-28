package com.akergez.shardkeep.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskEventTest {
    @Test
    fun taskWithoutStartIsNotAnEvent() {
        assertNull(task(title = "Просто дело", due = CalendarDate(2026, 9, 1)).toCalendarEvent())
    }

    @Test
    fun recurringTaskWithoutStartIsNotAnEvent() {
        assertNull(task(title = "Стендап", recurrence = "every weekday").toCalendarEvent())
    }

    @Test
    fun startOnlyBecomesSingleAllDayEvent() {
        val event = task(start = CalendarDate(2026, 9, 1)).toCalendarEvent()!!

        assertTrue(event.allDay)
        assertEquals(EventMoment(CalendarDate(2026, 9, 1), null), event.start)
        // DTEND исключающий: один день заканчивается началом следующего.
        assertEquals(EventMoment(CalendarDate(2026, 9, 2), null), event.end)
    }

    @Test
    fun endDateExtendsTheAllDaySpan() {
        val event = task(
            start = CalendarDate(2026, 9, 1),
            end = CalendarDate(2026, 9, 3),
        ).toCalendarEvent()!!

        assertTrue(event.allDay)
        assertEquals(EventMoment(CalendarDate(2026, 9, 4), null), event.end)
    }

    @Test
    fun bothTimeBoundsGiveATimedEvent() {
        val event = task(
            start = CalendarDate(2026, 9, 1),
            time = TaskTimeSpan(TimeOfDay(10, 0), TimeOfDay(18, 30)),
        ).toCalendarEvent()!!

        assertEquals(false, event.allDay)
        assertEquals(EventMoment(CalendarDate(2026, 9, 1), TimeOfDay(10, 0)), event.start)
        assertEquals(EventMoment(CalendarDate(2026, 9, 1), TimeOfDay(18, 30)), event.end)
    }

    @Test
    fun missingStartTimeMeansBeginningOfTheStartDay() {
        val event = task(
            start = CalendarDate(2026, 9, 1),
            time = TaskTimeSpan(null, TimeOfDay(12, 0)),
        ).toCalendarEvent()!!

        assertEquals(false, event.allDay)
        assertEquals(EventMoment(CalendarDate(2026, 9, 1), TimeOfDay.Midnight), event.start)
        assertEquals(EventMoment(CalendarDate(2026, 9, 1), TimeOfDay(12, 0)), event.end)
    }

    @Test
    fun missingEndTimeMeansEndOfTheEndDay() {
        val event = task(
            start = CalendarDate(2026, 9, 1),
            end = CalendarDate(2026, 9, 3),
            time = TaskTimeSpan(TimeOfDay(9, 0), null),
        ).toCalendarEvent()!!

        assertEquals(false, event.allDay)
        assertEquals(EventMoment(CalendarDate(2026, 9, 1), TimeOfDay(9, 0)), event.start)
        // Конец дня 3 сентября — это полночь 4-го.
        assertEquals(EventMoment(CalendarDate(2026, 9, 4), TimeOfDay.Midnight), event.end)
    }

    @Test
    fun timeSpanCrossingDaysKeepsBothBounds() {
        val event = task(
            start = CalendarDate(2026, 9, 1),
            end = CalendarDate(2026, 9, 2),
            time = TaskTimeSpan(TimeOfDay(22, 0), TimeOfDay(6, 0)),
        ).toCalendarEvent()!!

        assertEquals(EventMoment(CalendarDate(2026, 9, 1), TimeOfDay(22, 0)), event.start)
        assertEquals(EventMoment(CalendarDate(2026, 9, 2), TimeOfDay(6, 0)), event.end)
    }

    @Test
    fun endDateBeforeStartIsIgnoredInsteadOfDroppingTheTask() {
        val event = task(
            start = CalendarDate(2026, 9, 5),
            end = CalendarDate(2026, 9, 1),
        ).toCalendarEvent()!!

        assertEquals(EventMoment(CalendarDate(2026, 9, 5), null), event.start)
        assertEquals(EventMoment(CalendarDate(2026, 9, 6), null), event.end)
    }

    @Test
    fun endTimeBeforeStartTimeOnTheSameDayFallsBackToEndOfDay() {
        val event = task(
            start = CalendarDate(2026, 9, 1),
            time = TaskTimeSpan(TimeOfDay(18, 0), TimeOfDay(9, 0)),
        ).toCalendarEvent()!!

        assertEquals(EventMoment(CalendarDate(2026, 9, 1), TimeOfDay(18, 0)), event.start)
        assertEquals(EventMoment(CalendarDate(2026, 9, 2), TimeOfDay.Midnight), event.end)
    }

    @Test
    fun recurrenceTextIsParsedAndKept() {
        val event = task(
            start = CalendarDate(2026, 9, 1),
            recurrence = "every weekday",
        ).toCalendarEvent()!!

        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", event.recurrence?.toRRule(allDay = true))
        assertEquals("every weekday", event.recurrenceText)
    }

    @Test
    fun unparseableRecurrenceKeepsTheRawText() {
        val event = task(
            start = CalendarDate(2026, 9, 1),
            recurrence = "every fortnight",
        ).toCalendarEvent()!!

        assertNull(event.recurrence)
        assertEquals("every fortnight", event.recurrenceText)
    }

    @Test
    fun uidSurvivesLineMovesAndStatusOfNeighbours() {
        val first = task(start = CalendarDate(2026, 9, 1), line = 3).toCalendarEvent()!!
        val moved = task(start = CalendarDate(2026, 9, 1), line = 42).toCalendarEvent()!!

        assertEquals(first.uid, moved.uid)
    }

    @Test
    fun differentTasksGetDifferentUids() {
        val here = task(title = "Созвон", start = CalendarDate(2026, 9, 1)).toCalendarEvent()!!
        val there = task(title = "Созвон", start = CalendarDate(2026, 9, 1), path = "Other.md")
            .toCalendarEvent()!!

        assertTrue(here.uid != there.uid)
    }

    @Test
    fun doneTasksAreSkipped() {
        val events = listOf(
            task(title = "Сделано", start = CalendarDate(2026, 9, 1), status = TaskStatus.Done),
            task(title = "Осталось", start = CalendarDate(2026, 9, 1)),
        ).toCalendarEvents()

        assertEquals(listOf("Осталось"), events.map { it.title })
    }

    @Test
    fun duplicateTitlesInOneNoteGetDistinctUids() {
        val events = listOf(
            task(title = "Созвон", start = CalendarDate(2026, 9, 1), line = 1),
            task(title = "Созвон", start = CalendarDate(2026, 9, 8), line = 2),
        ).toCalendarEvents()

        assertEquals(2, events.size)
        assertEquals(events[0].uid + "-2", events[1].uid)
    }

    private fun task(
        title: String = "Событие",
        status: TaskStatus = TaskStatus.Todo,
        due: CalendarDate? = null,
        start: CalendarDate? = null,
        end: CalendarDate? = null,
        time: TaskTimeSpan? = null,
        recurrence: String? = null,
        path: String = "Notes/Plan.md",
        line: Int = 0,
    ) = Task(
        id = TaskId("$path:$line"),
        title = title,
        status = status,
        dueDate = due,
        startDate = start,
        endDate = end,
        time = time,
        recurrence = recurrence,
        location = NotesDirectoryLocation(path = path, heading = "Неделя", lineIndex = line),
    )
}
