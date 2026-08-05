package com.akergez.shardkeep.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.akergez.shardkeep.domain.model.CalendarDate
import kotlinx.coroutines.delay

expect fun currentLocalDate(): CalendarDate

/**
 * Today's date as state that keeps up with the calendar. Reading [currentLocalDate]
 * once at first composition freezes the whole app in the day it was opened: overdue
 * tasks stop turning overdue, "today" filters keep querying yesterday, and new tasks
 * are dated into the past. Sessions here outlive a day — the desktop window stays
 * open, the Android process survives in the background — so the date has to be
 * observed, not captured.
 *
 * The date is re-read on a poll rather than by sleeping until the next midnight.
 * A single long sleep assumes a clock that only moves forward at its own pace, and
 * none of ours do: the process is frozen while backgrounded and thaws late, the
 * device leaves doze with the delay overdue, the user crosses a timezone or sets the
 * clock by hand. Re-reading the platform date settles all of those the same way,
 * within one interval. Recomposition is not the cost it looks like — [CalendarDate]
 * compares by value, so all but the one poll that crosses midnight store an equal
 * value and invalidate nothing.
 */
@Composable
fun rememberCurrentLocalDate(): CalendarDate {
    var date by remember { mutableStateOf(currentLocalDate()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(DATE_POLL_INTERVAL_MILLIS)
            date = currentLocalDate()
        }
    }
    return date
}

private const val DATE_POLL_INTERVAL_MILLIS = 30_000L
