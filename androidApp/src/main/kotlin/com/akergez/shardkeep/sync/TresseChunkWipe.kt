package com.akergez.shardkeep.sync

import com.akergez.tresse.kmp.TresseVault

/** Object classes of the local CAS; everything under them is regenerable from a sync. */
private val STORE_DIRS = listOf(".tresse/meta", ".tresse/content")
private const val FILE_INDEX = ".tresse/file-index.json"

/**
 * Drops the replica's local chunk store — both object classes plus the file index —
 * leaving `.tresse/config.toml` and the notes themselves alone. Returns the number of
 * objects removed.
 */
internal fun wipeChunkStore(vault: TresseVault): Int {
    var removed = 0
    for (dir in STORE_DIRS) {
        val names = runCatching { vault.storeList(dir) }.getOrDefault(emptyList())
        for (name in names) {
            runCatching { vault.storeDelete("$dir/$name") }.onSuccess { removed += 1 }
        }
    }
    runCatching { vault.storeDelete(FILE_INDEX) }
    return removed
}

/** Forgets which repository this replica belonged to, keeping the storage coordinates. */
internal fun TresseConfig.withoutRepository(): TresseConfig = copy(repoId = "", encryptionKey = "")
