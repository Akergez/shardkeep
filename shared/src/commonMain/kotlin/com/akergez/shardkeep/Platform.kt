package com.akergez.shardkeep

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform