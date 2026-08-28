package com.akergez.shardkeep.domain.model

/** День и, если оно задано, время внутри него. */
data class EventMoment(val date: CalendarDate, val time: TimeOfDay?)

/**
 * Таска, развёрнутая до события календаря. Границы уже разрешены по умолчаниям,
 * так что ни iCal-писателю, ни Android-синку не приходится знать про 🛫🏁⏰ ничего.
 *
 * [end] исключающий: событие занимает `[start, end)`. Так требует и RFC 5545 для
 * `DTEND` события на весь день, и `CalendarContract`, и так пропадает вечный спор
 * о том, входит ли последняя минута.
 */
data class TaskEvent(
    val uid: String,
    val title: String,
    val allDay: Boolean,
    val start: EventMoment,
    val end: EventMoment,
    val recurrence: RecurrenceRule?,
    /** Исходный текст 🔁 — уходит в описание, когда правило разобрать не удалось. */
    val recurrenceText: String?,
    val notePath: String,
    val heading: String?,
)

/**
 * Разворачивает таску в событие или возвращает null, если она событием не является.
 *
 * Событие — это таска с 🛫. Правила границ:
 *  - конца нет → событие укладывается в день начала;
 *  - начала внутри дня нет → начинается в начале дня начала;
 *  - конца внутри дня нет → заканчивается в конце дня конца.
 *
 * Когда времени нет вовсе, получается событие на весь день — календарям так и надо
 * говорить, иначе встреча «на весь понедельник» встанет полоской в полночь.
 *
 * Бессмысленные границы (конец раньше начала) не отбрасывают таску целиком, а
 * игнорируются поштучно: в заметке опечатка, а событие всё равно должно доехать.
 */
fun Task.toCalendarEvent(): TaskEvent? {
    val from = startDate ?: return null
    val to = endDate?.takeIf { it >= from } ?: from

    val startTime = time?.start
    val endTime = time?.end?.takeIf { to > from || startTime == null || it > startTime }
    val allDay = startTime == null && endTime == null

    val start = EventMoment(from, if (allDay) null else startTime ?: TimeOfDay.Midnight)
    val end = when {
        allDay -> EventMoment(to.plusDays(1), null)
        endTime != null -> EventMoment(to, endTime)
        else -> EventMoment(to.plusDays(1), TimeOfDay.Midnight)
    }

    return TaskEvent(
        uid = taskEventUid(location.path, title),
        title = title,
        allDay = allDay,
        start = start,
        end = end,
        recurrence = recurrence?.let { RecurrenceTextParser.parse(it) },
        recurrenceText = recurrence,
        notePath = location.path,
        heading = location.heading,
    )
}

/**
 * Собирает события из индекса тасок.
 *
 * Выполненные таски пропускаются намеренно. Плагин Tasks, отмечая повторяющуюся
 * таску сделанной, оставляет строку с датой выполнения и заводит рядом новую — с тем
 * же заголовком и в том же файле, то есть с тем же [taskEventUid]. Брать обе значило
 * бы драться за один идентификатор на каждом синке; календарь при этом всё равно
 * показывает то, что ещё предстоит, а история остаётся в заметках.
 *
 * Одинаковые заголовки в одном файле разводятся суффиксом, иначе второе событие
 * затирало бы первое.
 */
fun List<Task>.toCalendarEvents(): List<TaskEvent> {
    val seen = mutableMapOf<String, Int>()
    return asSequence()
        .filter { it.status != TaskStatus.Done }
        .mapNotNull { it.toCalendarEvent() }
        .map { event ->
            val occurrence = seen.getOrElse(event.uid) { 0 } + 1
            seen[event.uid] = occurrence
            if (occurrence == 1) event else event.copy(uid = "${event.uid}-$occurrence")
        }
        .toList()
}

/**
 * Идентификатор события: путь заметки плюс заголовок таски, свёрнутые в FNV-1a.
 *
 * Намеренно не [TaskId] — в нём сидит номер строки, а он съезжает от любой правки
 * выше по файлу. Событие пересоздавалось бы на ровном месте, теряя напоминания и
 * приглашения, которые пользователь навесил на него в календаре.
 */
fun taskEventUid(notePath: String, title: String): String {
    var hash = -0x340d631b7bdddcdbL // FNV-1a offset basis, 0xcbf29ce484222325
    for (char in "$notePath $title") {
        hash = hash xor char.code.toLong()
        hash *= 0x100000001b3L
    }
    return "sk-${hash.toULong().toString(16).padStart(16, '0')}@shardkeep.app"
}
