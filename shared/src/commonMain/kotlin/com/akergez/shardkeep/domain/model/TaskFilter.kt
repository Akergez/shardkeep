package com.akergez.shardkeep.domain.model

enum class TimeRange {
    Day,
    Week,
}

data class TaskFilter(
    val range: TimeRange = TimeRange.Day,
    val folder: String? = null,
    val file: String? = null,
)
