package com.akergez.shardkeep.domain.model

data class TaskId(val value: String)

enum class TaskStatus {
    Todo,
    Done,
}

enum class TaskPriority {
    Low,
    Normal,
    High;

    val emoji: String get() = when (this) {
        Low -> "🔽"
        Normal -> ""
        High -> "🔼"
    }

    val dataviewName: String get() = when (this) {
        Low -> "low"
        Normal -> "normal"
        High -> "high"
    }
}

data class NotesDirectoryLocation(
    val path: String,
    val heading: String? = null,
    val lineIndex: Int? = null,
    val sourceLine: String? = null,
)

data class Task(
    val id: TaskId,
    val title: String,
    val status: TaskStatus,
    val dueDate: CalendarDate?,
    val scheduledDate: CalendarDate? = null,
    /** 🛫 `[start:: …]` — день, когда событие начинается. Он же делает таску событием. */
    val startDate: CalendarDate? = null,
    /** 🏁 `[end:: …]` — последний день события; без него событие укладывается в [startDate]. */
    val endDate: CalendarDate? = null,
    /** ⏰ `[time:: …]` — границы внутри дня; каждая половина необязательна. */
    val time: TaskTimeSpan? = null,
    /**
     * 🔁 `[repeat:: …]` — текст правила как он записан в заметке. Хранится сырым:
     * при перезаписи строки он должен вернуться в файл слово в слово, даже если
     * [RecurrenceTextParser] его не понял.
     */
    val recurrence: String? = null,
    val priority: TaskPriority = TaskPriority.Normal,
    val location: NotesDirectoryLocation,
)
