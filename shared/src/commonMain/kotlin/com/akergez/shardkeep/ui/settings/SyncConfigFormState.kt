package com.akergez.shardkeep.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

private const val STORAGE_S3 = "s3"
private const val STORAGE_HTTP = "http"

/**
 * Черновик настроек синхронизации в том виде, в каком его правят: всё строками.
 *
 * Числа и список игнорируемых путей разбираются только в [toDraft]; пока пользователь
 * печатает, поле может быть пустым или недописанным, и это нормально.
 */
@Stable
class SyncConfigFormState(private val current: SyncConfigDraft) {
    var storageType by mutableStateOf(current.storageType)
    var server by mutableStateOf(current.serverUrl)
    var repo by mutableStateOf(current.repoId)
    var token by mutableStateOf(current.token)
    var encryptionKey by mutableStateOf(current.encryptionKey)
    var s3Endpoint by mutableStateOf(current.s3Endpoint)
    var s3Region by mutableStateOf(current.s3Region)
    var s3Bucket by mutableStateOf(current.s3Bucket)
    var s3AccessKeyId by mutableStateOf(current.s3AccessKeyId)
    var s3SecretAccessKey by mutableStateOf(current.s3SecretAccessKey)
    var autoSync by mutableStateOf(current.autoSyncEnabled)
    var interval by mutableStateOf(current.autoSyncIntervalMinutes.toString())
    var debounce by mutableStateOf(current.autoSyncDebounceSeconds.toString())
    var ignore by mutableStateOf(current.ignorePatterns.joinToString("\n"))

    val isS3: Boolean get() = storageType == STORAGE_S3

    fun selectStorage(s3: Boolean) {
        storageType = if (s3) STORAGE_S3 else STORAGE_HTTP
    }

    /** Интервалы — только цифры: минуты и секунды нельзя набрать буквами. */
    fun updateInterval(value: String) {
        interval = value.filter(Char::isDigit)
    }

    fun updateDebounce(value: String) {
        debounce = value.filter(Char::isDigit)
    }

    fun toDraft(): SyncConfigDraft = SyncConfigDraft(
        serverUrl = server.trim(),
        repoId = repo.trim(),
        token = token.trim(),
        encryptionKey = encryptionKey.trim(),
        storageType = if (isS3) STORAGE_S3 else STORAGE_HTTP,
        s3Endpoint = s3Endpoint.trim(),
        s3Region = s3Region.trim(),
        s3Bucket = s3Bucket.trim(),
        s3AccessKeyId = s3AccessKeyId.trim(),
        s3SecretAccessKey = s3SecretAccessKey.trim(),
        autoSyncEnabled = autoSync,
        autoSyncIntervalMinutes = interval.toLongOrNull() ?: current.autoSyncIntervalMinutes,
        autoSyncDebounceSeconds = debounce.toLongOrNull() ?: current.autoSyncDebounceSeconds,
        ignorePatterns = ignore.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
    )
}

/** Недосохранённые настройки переживают поворот экрана — иначе их пришлось бы вбивать заново. */
private fun syncConfigFormSaver(current: SyncConfigDraft): Saver<SyncConfigFormState, Any> = listSaver(
    save = { state ->
        listOf(
            state.storageType, state.server, state.repo, state.token, state.encryptionKey,
            state.s3Endpoint, state.s3Region, state.s3Bucket, state.s3AccessKeyId,
            state.s3SecretAccessKey, state.autoSync.toString(), state.interval,
            state.debounce, state.ignore,
        )
    },
    restore = { saved ->
        SyncConfigFormState(current).apply {
            storageType = saved[0]
            server = saved[1]
            repo = saved[2]
            token = saved[3]
            encryptionKey = saved[4]
            s3Endpoint = saved[5]
            s3Region = saved[6]
            s3Bucket = saved[7]
            s3AccessKeyId = saved[8]
            s3SecretAccessKey = saved[9]
            autoSync = saved[10].toBooleanStrict()
            interval = saved[11]
            debounce = saved[12]
            ignore = saved[13]
        }
    },
)

@Composable
fun rememberSyncConfigFormState(current: SyncConfigDraft): SyncConfigFormState =
    rememberSaveable(current, saver = syncConfigFormSaver(current)) { SyncConfigFormState(current) }
