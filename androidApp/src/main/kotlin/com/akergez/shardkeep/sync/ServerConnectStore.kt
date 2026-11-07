package com.akergez.shardkeep.sync

import android.content.SharedPreferences
import com.akergez.tresse.kmp.TresseServerLink

/**
 * Начатое подключение к серверу, переживающее выгрузку процесса: пока открыт браузер,
 * Android может убить приложение, а возврат всё равно должен сматчиться. Срок жизни —
 * свой, библиотечная попытка его не хранит.
 */
internal data class PendingConnect(val attempt: TresseServerLink.Attempt, val expiresAtMs: Long)

internal object ServerConnectStore {
    private const val KEY_SERVER = "tresse_connect_server"
    private const val KEY_STATE = "tresse_connect_state"
    private const val KEY_URL = "tresse_connect_url"
    private const val KEY_EXPIRES = "tresse_connect_expires"

    fun load(prefs: SharedPreferences): PendingConnect? {
        val server = prefs.getString(KEY_SERVER, null) ?: return null
        val state = prefs.getString(KEY_STATE, null) ?: return null
        val url = prefs.getString(KEY_URL, null) ?: return null
        val expires = prefs.getLong(KEY_EXPIRES, 0L)
        if (expires <= System.currentTimeMillis()) return null
        return PendingConnect(TresseServerLink.Attempt(server, state, url), expires)
    }

    fun save(prefs: SharedPreferences, connect: PendingConnect) {
        prefs.edit()
            .putString(KEY_SERVER, connect.attempt.serverUrl)
            .putString(KEY_STATE, connect.attempt.state)
            .putString(KEY_URL, connect.attempt.url)
            .putLong(KEY_EXPIRES, connect.expiresAtMs)
            .apply()
    }

    fun clear(prefs: SharedPreferences) {
        prefs.edit()
            .remove(KEY_SERVER)
            .remove(KEY_STATE)
            .remove(KEY_URL)
            .remove(KEY_EXPIRES)
            .apply()
    }
}
