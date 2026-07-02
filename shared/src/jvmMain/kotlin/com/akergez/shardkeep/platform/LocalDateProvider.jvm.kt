package com.akergez.shardkeep.platform

import com.akergez.shardkeep.domain.model.CalendarDate
import java.time.LocalDate

actual fun currentLocalDate(): CalendarDate = LocalDate.now().let {
    CalendarDate(it.year, it.monthValue, it.dayOfMonth)
}
