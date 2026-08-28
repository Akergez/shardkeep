package com.akergez.shardkeep.ui

/** Navigation intent produced by a home-screen widget tap. */
sealed interface AppDeepLink {
    data object Tasks : AppDeepLink
    data object Notes : AppDeepLink
    data class OpenNote(val path: String) : AppDeepLink
}
