package com.akergez.shardkeep.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class ServerConnectFailureMappingTest {

    @Test
    fun mapsLibraryAcceptMessagesToScreenFailures() {
        assertEquals(
            ServerConnectFailure.LinkMismatch,
            mapServerConnectFailure("this link does not belong to the connection started here; start again from the app"),
        )
        assertEquals(
            ServerConnectFailure.NoConfig,
            mapServerConnectFailure("no config in the returned link"),
        )
        assertEquals(
            ServerConnectFailure.OtherServer,
            mapServerConnectFailure("the link points at another server than the one opened"),
        )
        assertEquals(
            ServerConnectFailure.NoKey,
            mapServerConnectFailure("this device has no encryption key for that repository; import the full config string from a device that already syncs it"),
        )
        assertEquals(
            ServerConnectFailure.Decode,
            mapServerConnectFailure("unknown backend 'ftp' in config string (expected 's3' or 'http')"),
        )
        assertEquals(ServerConnectFailure.Decode, mapServerConnectFailure(null))
    }
}
