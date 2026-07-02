package com.akergez.shardkeep.db

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

fun createDatabase(factory: DatabaseDriverFactory): ShardkeepDatabase =
    ShardkeepDatabase(factory.createDriver())
