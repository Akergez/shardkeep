package com.akergez.shardkeep.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import shardkeep.shared.generated.resources.Res
import shardkeep.shared.generated.resources.access_token
import shardkeep.shared.generated.resources.action_import
import shardkeep.shared.generated.resources.auto_sync
import shardkeep.shared.generated.resources.cancel
import shardkeep.shared.generated.resources.config_import_error
import shardkeep.shared.generated.resources.encryption_key
import shardkeep.shared.generated.resources.ignore_patterns
import shardkeep.shared.generated.resources.import_config
import shardkeep.shared.generated.resources.import_config_hint
import shardkeep.shared.generated.resources.repo_id
import shardkeep.shared.generated.resources.s3_access_key_id
import shardkeep.shared.generated.resources.s3_bucket
import shardkeep.shared.generated.resources.s3_endpoint
import shardkeep.shared.generated.resources.s3_endpoint_hint
import shardkeep.shared.generated.resources.s3_region
import shardkeep.shared.generated.resources.s3_region_hint
import shardkeep.shared.generated.resources.s3_secret_access_key
import shardkeep.shared.generated.resources.save
import shardkeep.shared.generated.resources.server_url
import shardkeep.shared.generated.resources.storage_http
import shardkeep.shared.generated.resources.storage_s3
import shardkeep.shared.generated.resources.sync_config_hint
import shardkeep.shared.generated.resources.sync_debounce_seconds
import shardkeep.shared.generated.resources.sync_interval_minutes
import shardkeep.shared.generated.resources.sync_title
import shardkeep.shared.generated.resources.wipe_chunks
import shardkeep.shared.generated.resources.wipe_chunks_action
import shardkeep.shared.generated.resources.wipe_chunks_confirm
import shardkeep.shared.generated.resources.wipe_chunks_hint

/** Блок настроек синхронизации: статус, поля конфига, импорт и сброс локального хранилища. */
@Composable
fun ColumnScope.SyncSettingsSection(callbacks: SettingsCallbacks, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()

    Text(stringResource(Res.string.sync_title), style = MaterialTheme.typography.titleMedium)
    var syncing by remember { mutableStateOf(false) }
    callbacks.syncState?.let { state ->
        SyncStatusCard(
            stateFlow = state,
            syncing = syncing,
            onSyncNow = callbacks.onSync?.let { sync ->
                {
                    scope.launch {
                        syncing = true
                        runCatching { sync() }
                        syncing = false
                    }
                }
            },
            onForcePull = callbacks.onForcePull?.let { forcePull ->
                {
                    scope.launch {
                        syncing = true
                        runCatching { forcePull() }
                        syncing = false
                    }
                }
            },
        )
    }
    Text(
        stringResource(Res.string.sync_config_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val form = rememberSyncConfigFormState(callbacks.syncConfig ?: SyncConfigDraft())

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !form.isS3,
            onClick = { form.selectStorage(s3 = false) },
            label = { Text(stringResource(Res.string.storage_http)) },
        )
        FilterChip(
            selected = form.isS3,
            onClick = { form.selectStorage(s3 = true) },
            label = { Text(stringResource(Res.string.storage_s3)) },
        )
    }
    SettingsField(form.repo, { form.repo = it }, stringResource(Res.string.repo_id))
    SettingsField(form.encryptionKey, { form.encryptionKey = it }, stringResource(Res.string.encryption_key))
    if (form.isS3) {
        SettingsField(
            form.s3Endpoint, { form.s3Endpoint = it },
            stringResource(Res.string.s3_endpoint), stringResource(Res.string.s3_endpoint_hint),
        )
        SettingsField(
            form.s3Region, { form.s3Region = it },
            stringResource(Res.string.s3_region), stringResource(Res.string.s3_region_hint),
        )
        SettingsField(form.s3Bucket, { form.s3Bucket = it }, stringResource(Res.string.s3_bucket))
        SettingsField(form.s3AccessKeyId, { form.s3AccessKeyId = it }, stringResource(Res.string.s3_access_key_id))
        SettingsField(
            form.s3SecretAccessKey, { form.s3SecretAccessKey = it },
            stringResource(Res.string.s3_secret_access_key),
        )
    } else {
        SettingsField(form.server, { form.server = it }, stringResource(Res.string.server_url))
        SettingsField(form.token, { form.token = it }, stringResource(Res.string.access_token))
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(Res.string.auto_sync), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = form.autoSync, onCheckedChange = { form.autoSync = it })
    }
    SettingsField(
        value = form.interval,
        onValueChange = form::updateInterval,
        label = stringResource(Res.string.sync_interval_minutes),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    SettingsField(
        value = form.debounce,
        onValueChange = form::updateDebounce,
        label = stringResource(Res.string.sync_debounce_seconds),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    OutlinedTextField(
        form.ignore,
        { form.ignore = it },
        Modifier.fillMaxWidth(),
        label = { Text(stringResource(Res.string.ignore_patterns)) },
        minLines = 2,
    )

    var configString by rememberSaveable { mutableStateOf("") }
    var configImportFailed by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = configString,
        onValueChange = { configString = it; configImportFailed = false },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(Res.string.import_config)) },
        placeholder = { Text(stringResource(Res.string.import_config_hint)) },
        minLines = 5,
    )
    if (configImportFailed) {
        Text(stringResource(Res.string.config_import_error), color = MaterialTheme.colorScheme.error)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = {
            scope.launch {
                val imported = callbacks.onImportSyncConfig(configString)
                configImportFailed = !imported
                if (imported) {
                    configString = ""
                    onSaved()
                }
            }
        }) { Text(stringResource(Res.string.action_import)) }
        TextButton(onClick = {
            scope.launch {
                callbacks.onSyncConfigChange(form.toDraft())
                onSaved()
            }
        }) { Text(stringResource(Res.string.save)) }
    }

    callbacks.onWipeChunks?.let { wipe ->
        WipeChunksButton(onWipe = wipe, onSaved = onSaved)
    }
}

/** Сброс локального чанк-стора — операция без отмены, поэтому за подтверждением. */
@Composable
private fun ColumnScope.WipeChunksButton(onWipe: suspend () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var wiping by remember { mutableStateOf(false) }
    var confirmWipe by rememberSaveable { mutableStateOf(false) }
    Text(
        stringResource(Res.string.wipe_chunks_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(
        onClick = { confirmWipe = true },
        enabled = !wiping,
        modifier = Modifier.align(Alignment.End),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) { Text(stringResource(Res.string.wipe_chunks)) }
    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text(stringResource(Res.string.wipe_chunks)) },
            text = { Text(stringResource(Res.string.wipe_chunks_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    scope.launch {
                        wiping = true
                        runCatching { onWipe() }
                        wiping = false
                        onSaved()
                    }
                }) { Text(stringResource(Res.string.wipe_chunks_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

/** Однострочное поле настроек: их тут много и все одинаковые. */
@Composable
private fun SettingsField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = keyboardOptions,
    )
}
