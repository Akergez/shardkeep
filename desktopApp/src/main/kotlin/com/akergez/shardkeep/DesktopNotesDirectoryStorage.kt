package com.akergez.shardkeep

import com.akergez.shardkeep.notesdirectory.NotesDirectoryStorage
import com.akergez.shardkeep.notesdirectory.VaultFileMetadata
import com.akergez.shardkeep.notesdirectory.VaultNotesDirectoryStorage
import com.akergez.tresse.kmp.TresseVault
import com.akergez.tresse.kmp.fileTresseVault
import java.io.File

/**
 * Desktop binding from Shardkeep's markdown repositories to tresse-kmp's file
 * implementation. No filesystem traversal or file IO is implemented here.
 */
class DesktopNotesDirectoryStorage private constructor(
    delegate: NotesDirectoryStorage,
) : NotesDirectoryStorage by delegate {
    constructor(root: File) : this(fileTresseVault(root).asNotesDirectoryStorage())
}

private fun TresseVault.asNotesDirectoryStorage(): NotesDirectoryStorage =
    VaultNotesDirectoryStorage(
        listFiles = {
            listFiles().map {
                VaultFileMetadata(it.path, it.size, it.modifiedAtMillis)
            }
        },
        readBytes = ::read,
        writeText = ::writeText,
        writeBytes = ::writeBinary,
        deleteFile = ::delete,
    )
