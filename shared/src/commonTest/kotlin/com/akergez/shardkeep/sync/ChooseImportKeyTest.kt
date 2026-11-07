package com.akergez.shardkeep.sync

import com.akergez.shardkeep.ui.settings.SyncConfigDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChooseImportKeyTest {
    private val configured = SyncConfigDraft(
        serverUrl = "https://sync.example.org",
        repoId = "notes",
        token = "t1",
        encryptionKey = "k1",
    )

    @Test
    fun stringWithItsOwnKeyUsesIt() {
        assertEquals(
            ImportKey.Incoming,
            chooseImportKey(configured.copy(repoId = "other", encryptionKey = "k2"), false, configured),
        )
    }

    @Test
    fun newRepositoryGetsAFreshKey() {
        assertEquals(
            ImportKey.Generate,
            chooseImportKey(configured.copy(repoId = "fresh", encryptionKey = ""), true, configured),
        )
    }

    @Test
    fun sameRepositoryKeepsTheDeviceKey() {
        val fromServer = configured.copy(serverUrl = "https://sync.example.org/", token = "t2", encryptionKey = "")
        assertEquals(ImportKey.Current, chooseImportKey(fromServer, false, configured))
    }

    @Test
    fun foreignRepositoryWithoutKeyIsRefused() {
        assertNull(chooseImportKey(configured.copy(repoId = "other", encryptionKey = ""), false, configured))
        assertNull(chooseImportKey(configured.copy(encryptionKey = ""), false, null))
        assertNull(
            chooseImportKey(
                configured.copy(serverUrl = "https://elsewhere.example.org", encryptionKey = ""),
                false,
                configured,
            ),
        )
    }

    @Test
    fun serverOfferPrefersTheConfiguredHttpServer() {
        assertEquals("https://sync.example.org", connectServerFor(configured, "https://default.example.org"))
        assertEquals("https://default.example.org", connectServerFor(null, "https://default.example.org"))
        assertEquals(
            "https://default.example.org",
            connectServerFor(configured.copy(storageType = "s3"), "https://default.example.org"),
        )
    }
}
