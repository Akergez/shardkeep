package com.akergez.shardkeep.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecurrenceTextParserTest {
    private fun rrule(text: String, allDay: Boolean = true): String? =
        RecurrenceTextParser.parse(text)?.toRRule(allDay)

    @Test
    fun parsesPlainFrequencies() {
        assertEquals("FREQ=DAILY", rrule("every day"))
        assertEquals("FREQ=WEEKLY", rrule("every week"))
        assertEquals("FREQ=MONTHLY", rrule("every month"))
        assertEquals("FREQ=YEARLY", rrule("every year"))
    }

    @Test
    fun parsesInterval() {
        assertEquals("FREQ=DAILY;INTERVAL=3", rrule("every 3 days"))
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,TH", rrule("every 2 weeks on Monday, Thursday"))
    }

    @Test
    fun weekdayShorthandExpandsToWorkingWeek() {
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", rrule("every weekday"))
    }

    @Test
    fun bareWeekdayImpliesWeekly() {
        assertEquals("FREQ=WEEKLY;BYDAY=MO", rrule("every Monday"))
        assertEquals("FREQ=WEEKLY;BYDAY=SA,SU", rrule("every Saturday and Sunday"))
    }

    @Test
    fun bareMonthImpliesYearly() {
        assertEquals("FREQ=YEARLY;BYMONTH=1", rrule("every January"))
    }

    @Test
    fun parsesMonthDays() {
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=15", rrule("every month on the 15th"))
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=1,15", rrule("every month on the 1st and 15th"))
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=-1", rrule("every month on the last day"))
    }

    @Test
    fun parsesOrdinalWeekdays() {
        assertEquals("FREQ=MONTHLY;BYDAY=-1FR", rrule("every month on the last Friday"))
        assertEquals("FREQ=MONTHLY;BYDAY=2TU", rrule("every month on the 2nd Tuesday"))
        assertEquals("FREQ=MONTHLY;BYDAY=1MO", rrule("every month on the first Monday of the month"))
    }

    @Test
    fun parsesYearlyWithMonthAndDay() {
        assertEquals("FREQ=YEARLY;BYMONTH=1;BYMONTHDAY=1", rrule("every year in January on the 1st"))
    }

    @Test
    fun parsesCount() {
        assertEquals("FREQ=WEEKLY;COUNT=10", rrule("every week for 10 times"))
        assertEquals("FREQ=DAILY;COUNT=1", rrule("every day for 1 time"))
    }

    @Test
    fun untilMatchesTheTypeOfDtstart() {
        assertEquals("FREQ=DAILY;UNTIL=20261231", rrule("every day until 2026-12-31", allDay = true))
        assertEquals("FREQ=DAILY;UNTIL=20261231T235959", rrule("every day until 2026-12-31", allDay = false))
    }

    @Test
    fun swallowsWhenDoneSuffix() {
        assertEquals("FREQ=MONTHLY;INTERVAL=2", rrule("every 2 months when done"))
    }

    @Test
    fun rejectsTextItCannotFullyUnderstand() {
        assertNull(RecurrenceTextParser.parse("every fortnight"))
        assertNull(RecurrenceTextParser.parse("each day"))
        assertNull(RecurrenceTextParser.parse("every day at noon"))
        assertNull(RecurrenceTextParser.parse("every 0 days"))
        assertNull(RecurrenceTextParser.parse(""))
        assertNull(RecurrenceTextParser.parse("every day until forever"))
    }

    @Test
    fun rejectsCountAndUntilTogether() {
        assertNull(RecurrenceTextParser.parse("every day for 3 times until 2026-12-31"))
    }
}
