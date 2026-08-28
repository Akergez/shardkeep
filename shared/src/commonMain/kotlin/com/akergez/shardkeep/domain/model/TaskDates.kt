package com.akergez.shardkeep.domain.model

/**
 * Дата, по которой таска попадает в ленту дня: запланированная, а если её нет — срок.
 * Одна на весь домен, чтобы фильтр, сортировка и группировка просроченных не разошлись.
 */
val Task.effectiveDate: CalendarDate? get() = scheduledDate ?: dueDate
