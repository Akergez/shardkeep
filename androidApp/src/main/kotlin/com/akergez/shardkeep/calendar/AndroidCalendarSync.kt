package com.akergez.shardkeep.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.akergez.shardkeep.R
import com.akergez.shardkeep.domain.model.EventMoment
import com.akergez.shardkeep.domain.model.TaskEvent
import java.util.Calendar
import java.util.TimeZone

data class CalendarSyncResult(val added: Int, val updated: Int, val removed: Int) {
    val changed: Boolean get() = added > 0 || updated > 0 || removed > 0
}

/**
 * Выгрузка событий в системный календарь через [CalendarContract].
 *
 * Приложение заводит собственный локальный календарь (`ACCOUNT_TYPE_LOCAL`): аккаунт
 * для него не нужен, он не уезжает ни в какой Google, и всё, что в нём лежит, —
 * наше. Чужие события мы не трогаем вовсе, а удалить всё разом можно, снеся один
 * календарь. Отдельный календарь на воркспейс: у каждого каталога заметок свои
 * таски, и смешивать их в одной ленте нельзя — при удалении воркспейса его события
 * должны уйти вместе с ним.
 *
 * Пишем через sync-adapter URI. Так провайдер разрешает трогать служебные колонки:
 * `_SYNC_ID` держит наш UID события, `SYNC_DATA1` — отпечаток содержимого, по
 * которому видно, изменилось ли событие с прошлого раза, и не переписывать то, что
 * и так совпадает.
 */
object AndroidCalendarSync {
    private const val ACCOUNT_NAME = "Shardkeep"
    private const val CALENDAR_COLOR = 0xFF6750A4.toInt()

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED

    val permissions: Array<String> =
        arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    /**
     * Приводит календарь воркспейса к списку [events]: новое вставляет, изменённое
     * обновляет, пропавшее удаляет. Возвращает, сколько чего произошло.
     */
    fun sync(
        context: Context,
        workspaceId: String,
        workspaceName: String,
        events: List<TaskEvent>,
    ): CalendarSyncResult {
        val calendarId = ensureCalendar(context, workspaceId, workspaceName)
        val existing = existingEvents(context, calendarId)
        val resolver = context.contentResolver

        var added = 0
        var updated = 0
        val kept = mutableSetOf<String>()

        events.forEach { event ->
            kept += event.uid
            val values = valuesOf(event, calendarId)
            val known = existing[event.uid]
            when {
                known == null -> {
                    resolver.insert(syncUri(CalendarContract.Events.CONTENT_URI), values)
                    added++
                }
                known.signature != values.getAsString(CalendarContract.Events.SYNC_DATA1) -> {
                    resolver.update(
                        syncUri(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, known.id)),
                        values,
                        null,
                        null,
                    )
                    updated++
                }
            }
        }

        var removed = 0
        existing.forEach { (uid, known) ->
            if (uid in kept) return@forEach
            resolver.delete(
                syncUri(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, known.id)),
                null,
                null,
            )
            removed++
        }

        return CalendarSyncResult(added, updated, removed)
    }

    /** Убирает календарь воркспейса целиком — вместе со всеми его событиями. */
    fun removeCalendar(context: Context, workspaceId: String) {
        val id = findCalendar(context, workspaceId) ?: return
        context.contentResolver.delete(
            syncUri(ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, id)),
            null,
            null,
        )
    }

    private data class KnownEvent(val id: Long, val signature: String?)

    private fun existingEvents(context: Context, calendarId: Long): Map<String, KnownEvent> {
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events._SYNC_ID,
            CalendarContract.Events.SYNC_DATA1,
        )
        val result = mutableMapOf<String, KnownEvent>()
        context.contentResolver.query(
            syncUri(CalendarContract.Events.CONTENT_URI),
            projection,
            "${CalendarContract.Events.CALENDAR_ID} = ?",
            arrayOf(calendarId.toString()),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val uid = cursor.getString(1) ?: continue
                result[uid] = KnownEvent(cursor.getLong(0), cursor.getString(2))
            }
        }
        return result
    }

    private fun valuesOf(event: TaskEvent, calendarId: Long): ContentValues {
        val zone = if (event.allDay) UTC else TimeZone.getDefault()
        val startMillis = event.start.toEpochMillis(zone)
        val endMillis = event.end.toEpochMillis(zone)

        return ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events._SYNC_ID, event.uid)
            put(CalendarContract.Events.UID_2445, event.uid)
            put(CalendarContract.Events.TITLE, event.title)
            put(CalendarContract.Events.DESCRIPTION, descriptionOf(event))
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
            put(CalendarContract.Events.HAS_ALARM, 0)
            val rrule = event.recurrence?.toRRule(event.allDay)
            if (rrule == null) {
                put(CalendarContract.Events.DTEND, endMillis)
                putNull(CalendarContract.Events.DURATION)
                putNull(CalendarContract.Events.RRULE)
            } else {
                // У повторяющегося события провайдер требует длительность вместо
                // конца: конкретный конец у каждого повтора свой.
                put(CalendarContract.Events.RRULE, rrule)
                put(CalendarContract.Events.DURATION, durationOf(event, startMillis, endMillis))
                putNull(CalendarContract.Events.DTEND)
            }
            put(CalendarContract.Events.SYNC_DATA1, signatureOf(this))
        }
    }

    /**
     * Длительность события на весь день считается в днях: провайдер иначе сдвигает
     * повторы на границе перехода на зимнее время, когда сутки не 24 часа.
     */
    private fun durationOf(event: TaskEvent, startMillis: Long, endMillis: Long): String =
        if (event.allDay) {
            val days = ((endMillis - startMillis) / MILLIS_PER_DAY).coerceAtLeast(1)
            "P${days}D"
        } else {
            val minutes = ((endMillis - startMillis) / 60_000L).coerceAtLeast(1)
            "PT${minutes}M"
        }

    private fun descriptionOf(event: TaskEvent): String = buildList {
        add(listOfNotNull(event.notePath, event.heading).joinToString(" › "))
        // Правило, которое не разобралось, календарь повторить не сможет. Пусть
        // хотя бы будет видно, что в заметке просили повтор.
        if (event.recurrence == null && event.recurrenceText != null) {
            add("🔁 ${event.recurrenceText}")
        }
    }.joinToString("\n")

    /** Отпечаток полей события: по нему видно, надо ли трогать строку в провайдере. */
    private fun signatureOf(values: ContentValues): String = SIGNED_COLUMNS
        .joinToString("|") { values.get(it)?.toString().orEmpty() }
        .hashCode()
        .toString()

    private fun ensureCalendar(context: Context, workspaceId: String, workspaceName: String): Long {
        findCalendar(context, workspaceId)?.let { return it }

        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, calendarName(workspaceId))
            put(
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                context.getString(R.string.calendar_display_name) + " · " + workspaceName,
            )
            put(CalendarContract.Calendars.CALENDAR_COLOR, CALENDAR_COLOR)
            put(
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.CAL_ACCESS_OWNER,
            )
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        }
        val uri = context.contentResolver.insert(syncUri(CalendarContract.Calendars.CONTENT_URI), values)
        return uri?.lastPathSegment?.toLongOrNull()
            ?: findCalendar(context, workspaceId)
            ?: error("Could not create the Shardkeep calendar")
    }

    private fun findCalendar(context: Context, workspaceId: String): Long? =
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND ${CalendarContract.Calendars.NAME} = ?",
            arrayOf(CalendarContract.ACCOUNT_TYPE_LOCAL, calendarName(workspaceId)),
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

    private fun calendarName(workspaceId: String) = "shardkeep-$workspaceId"

    private fun syncUri(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    /**
     * Время в заметке плавающее — «десять утра» без зоны. Событию на весь день
     * провайдер велит хранить полночь по UTC, событию со временем — момент в зоне
     * устройства, то есть те самые стенные часы здесь и сейчас.
     */
    private fun EventMoment.toEpochMillis(zone: TimeZone): Long {
        val day = date
        val at = time
        return Calendar.getInstance(zone).apply {
            clear()
            set(day.year, day.month - 1, day.day, at?.hour ?: 0, at?.minute ?: 0, 0)
        }.timeInMillis
    }

    private const val MILLIS_PER_DAY = 86_400_000L
    private val UTC: TimeZone = TimeZone.getTimeZone("UTC")
    private val SIGNED_COLUMNS = listOf(
        CalendarContract.Events.TITLE,
        CalendarContract.Events.DESCRIPTION,
        CalendarContract.Events.DTSTART,
        CalendarContract.Events.DTEND,
        CalendarContract.Events.DURATION,
        CalendarContract.Events.ALL_DAY,
        CalendarContract.Events.EVENT_TIMEZONE,
        CalendarContract.Events.RRULE,
    )
}
