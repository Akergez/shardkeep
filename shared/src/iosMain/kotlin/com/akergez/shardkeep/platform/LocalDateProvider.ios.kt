package com.akergez.shardkeep.platform

import com.akergez.shardkeep.domain.model.CalendarDate
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

actual fun currentLocalDate(): CalendarDate {
    val parts = NSDateFormatter().apply { dateFormat = "yyyy-MM-dd" }
        .stringFromDate(NSDate())
        .split('-')
    return CalendarDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
}
