package com.akergez.shardkeep.ui.settings


/**
 * What a notes directory ignores unless the user says otherwise. Hidden files and
 * folders stay out — Obsidian, which shares these directories with us, cannot read
 * them anyway. `.obsidian` is the one exception it does expose, so settings, themes
 * and plugins travel with the notes; inside it we drop what is device-local by
 * nature (the window layout differs between phone and desktop, the cache rebuilds).
 *
 * Order matters, the last matching pattern decides. The bare `!/.obsidian/` has to
 * come first: without it the directory itself stays excluded, nothing descends into
 * it, and the recursive negation on the next line has nothing left to bring back.
 * `.git`, `.syncthing` and `.trash` are named last — foreign state stores and the
 * local wastebasket, none of which may travel, whatever precedes them.
 *
 * This is app policy. `tresse-kmp` ships no defaults of its own: what a host wants
 * synced is the host's business, and the engine only honors what it is handed.
 */
val DEFAULT_IGNORE_PATTERNS: List<String> = listOf(
    ".*",
    "!/.obsidian/",
    "!/.obsidian/**",
    "/.obsidian/workspace.json",
    "/.obsidian/workspace-mobile.json",
    "/.obsidian/cache",
    ".git",
    ".syncthing",
    ".trash",
)

/**
 * Editable view of the Tresse sync config. The `tresse.toml` file in the notes-directory root
 * is the single source of truth — this draft is read from it and written straight back to it; there is
 * no separate internal store of these settings.
 */
data class SyncConfigDraft(
    val serverUrl: String = "",
    val repoId: String = "",
    val token: String = "",
    val encryptionKey: String = "",
    val storageType: String = "http",
    val s3Endpoint: String = "",
    val s3Region: String = "",
    val s3Bucket: String = "",
    val s3AccessKeyId: String = "",
    val s3SecretAccessKey: String = "",
    val autoSyncEnabled: Boolean = false,
    val autoSyncIntervalMinutes: Long = 15,
    val autoSyncDebounceSeconds: Long = 10,
    val ignorePatterns: List<String> = DEFAULT_IGNORE_PATTERNS,
)
