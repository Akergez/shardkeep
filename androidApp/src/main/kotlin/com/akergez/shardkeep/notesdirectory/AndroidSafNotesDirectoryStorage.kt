package com.akergez.shardkeep.notesdirectory

import android.content.Context
import android.net.Uri
import com.akergez.tresse.kmp.TresseVault
import com.akergez.tresse.kmp.safTresseVault

/**
 * Android binding from Shardkeep's markdown repositories to tresse-kmp's SAF
 * filesystem implementation. No SAF traversal or file IO is implemented here.
 */
class AndroidSafNotesDirectoryStorage private constructor(
    delegate: NotesDirectoryStorage,
) : NotesDirectoryStorage by delegate {
    constructor(context: Context, treeUri: Uri) :
        this(safTresseVault(context, treeUri).asNotesDirectoryStorage())
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
