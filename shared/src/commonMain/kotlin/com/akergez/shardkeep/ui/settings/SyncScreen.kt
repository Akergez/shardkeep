package com.akergez.shardkeep.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.akergez.shardkeep.sync.ServerConnectFailure
import com.akergez.shardkeep.sync.connectServerFor
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import shardkeep.shared.generated.resources.Res
import shardkeep.shared.generated.resources.action_import
import shardkeep.shared.generated.resources.close
import shardkeep.shared.generated.resources.sync_connect_begin_failed
import shardkeep.shared.generated.resources.sync_export_copied
import shardkeep.shared.generated.resources.sync_export_copy
import shardkeep.shared.generated.resources.sync_export_description
import shardkeep.shared.generated.resources.sync_export_string
import shardkeep.shared.generated.resources.sync_import_done
import shardkeep.shared.generated.resources.sync_server_field
import shardkeep.shared.generated.resources.sync_server_offer_button
import shardkeep.shared.generated.resources.sync_server_offer_description
import shardkeep.shared.generated.resources.sync_string_description
import shardkeep.shared.generated.resources.sync_string_field
import shardkeep.shared.generated.resources.sync_tab_details
import shardkeep.shared.generated.resources.sync_tab_main
import shardkeep.shared.generated.resources.sync_title

/**
 * Экран синхронизации. Две вкладки: «Основное» — вход через сервер Tresse (адрес
 * вкомпилирован из TRESSE_SERVER_URL, его можно заменить), вставка строки
 * подключения и её экспорт; «Подробно» — все поля удалённого хранилища.
 */
@Composable
fun SyncScreen(
    callbacks: SettingsCallbacks?,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.sync_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        if (callbacks == null) return@Column

        SyncStatus(callbacks)

        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text(stringResource(Res.string.sync_tab_main)) },
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text(stringResource(Res.string.sync_tab_details)) },
            )
        }
        when (tab) {
            0 -> SyncMainTab(callbacks, onSaved)
            else -> SyncDetailsTab(callbacks, onSaved)
        }
    }
}

/** Статус и ручной запуск синка — над вкладками, чтобы были видны с любой из них. */
@Composable
private fun SyncStatus(callbacks: SettingsCallbacks) {
    val state = callbacks.syncState ?: return
    val scope = rememberCoroutineScope()
    var syncing by remember { mutableStateOf(false) }
    fun run(action: suspend () -> String): () -> Unit = {
        scope.launch {
            syncing = true
            runCatching { action() }
            syncing = false
        }
    }
    SyncStatusCard(
        stateFlow = state,
        syncing = syncing,
        onSyncNow = callbacks.onSync?.let(::run),
        onForcePull = callbacks.onForcePull?.let(::run),
    )
}

/** Вкладка «Основное»: вход через сервер, строка подключения, экспорт. */
@Composable
private fun ColumnScope.SyncMainTab(callbacks: SettingsCallbacks, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()

    // Сервер для входа: уже настроенный HTTP-сервер или тот, что из сборки.
    val suggestedServer = connectServerFor(callbacks.syncConfig, callbacks.serverConnectUrl)
    var server by rememberSaveable(suggestedServer) { mutableStateOf(suggestedServer) }
    var beginFailed by rememberSaveable { mutableStateOf(false) }
    SettingsField(
        value = server,
        onValueChange = { server = it; beginFailed = false },
        label = stringResource(Res.string.sync_server_field),
        placeholder = "https://…",
    )
    Button(
        onClick = {
            scope.launch {
                beginFailed = callbacks.onServerConnectBegin?.invoke(server.trim()) != true
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = callbacks.onServerConnectBegin != null && server.isNotBlank(),
    ) { Text(stringResource(Res.string.sync_server_offer_button)) }
    Hint(stringResource(Res.string.sync_server_offer_description))
    if (beginFailed) ErrorText(stringResource(Res.string.sync_connect_begin_failed))

    HorizontalDivider()

    var configString by rememberSaveable { mutableStateOf("") }
    var importFailure by remember { mutableStateOf<ServerConnectFailure?>(null) }
    var imported by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = configString,
        onValueChange = { configString = it; importFailure = null; imported = false },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(Res.string.sync_string_field)) },
        placeholder = { Text("tresse://http?url=…") },
        minLines = 2,
    )
    Hint(stringResource(Res.string.sync_string_description))
    importFailure?.let { ErrorText(connectFailureText(it)) }
    if (imported) Hint(stringResource(Res.string.sync_import_done))
    TextButton(
        onClick = {
            scope.launch {
                val failure = callbacks.onImportSyncConfig(configString)
                importFailure = failure
                if (failure == null) {
                    configString = ""
                    imported = true
                    onSaved()
                }
            }
        },
        enabled = configString.isNotBlank(),
        modifier = Modifier.align(Alignment.End),
    ) { Text(stringResource(Res.string.action_import)) }

    // Экспорт — когда настроено всё, каким бы ни был ремоут: строка нужна следующему
    // устройству, и новый ключ, созданный здесь, иначе негде сохранить.
    callbacks.syncConfigString?.let { exported ->
        HorizontalDivider()
        ExportConfigString(exported)
    }
}

@Composable
private fun ColumnScope.ExportConfigString(exported: String) {
    var shown by rememberSaveable { mutableStateOf(false) }
    Hint(stringResource(Res.string.sync_export_description))
    OutlinedButton(onClick = { shown = true }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.sync_export_string))
    }
    if (!shown) return
    @Suppress("DEPRECATION") // LocalClipboard в общем коде не умеет собрать ClipEntry из текста.
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { shown = false },
        title = { Text(stringResource(Res.string.sync_export_string)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Hint(stringResource(Res.string.sync_export_description))
                OutlinedTextField(
                    value = exported,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
                if (copied) Hint(stringResource(Res.string.sync_export_copied))
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(exported))
                    copied = true
                }) { Text(stringResource(Res.string.sync_export_copy)) }
                TextButton(onClick = { shown = false }) { Text(stringResource(Res.string.close)) }
            }
        },
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ErrorText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}
