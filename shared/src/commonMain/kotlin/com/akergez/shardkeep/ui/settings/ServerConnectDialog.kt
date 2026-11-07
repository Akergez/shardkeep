package com.akergez.shardkeep.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akergez.shardkeep.sync.ServerConnectEvent
import com.akergez.shardkeep.sync.ServerConnectFailure
import org.jetbrains.compose.resources.stringResource
import shardkeep.shared.generated.resources.Res
import shardkeep.shared.generated.resources.cancel
import shardkeep.shared.generated.resources.config_import_error
import shardkeep.shared.generated.resources.ok
import shardkeep.shared.generated.resources.sync_connect_action
import shardkeep.shared.generated.resources.sync_connect_confirm_body
import shardkeep.shared.generated.resources.sync_connect_confirm_title
import shardkeep.shared.generated.resources.sync_connect_error_link
import shardkeep.shared.generated.resources.sync_connect_error_no_config
import shardkeep.shared.generated.resources.sync_connect_error_no_key
import shardkeep.shared.generated.resources.sync_connect_error_other_server
import shardkeep.shared.generated.resources.sync_connect_key_generated

/**
 * Диалог решения по возврату с серверной страницы: подтвердить подключение или
 * показать, почему возврат отклонён. Сам возврат и проверки — на платформе; здесь
 * только вопрос пользователю.
 */
@Composable
fun ServerConnectDialog(event: ServerConnectEvent, onResolve: (accept: Boolean) -> Unit) {
    when (event) {
        is ServerConnectEvent.Request -> AlertDialog(
            onDismissRequest = { onResolve(false) },
            title = { Text(stringResource(Res.string.sync_connect_confirm_title)) },
            text = {
                Column {
                    Text(
                        stringResource(
                            Res.string.sync_connect_confirm_body,
                            event.serverUrl,
                            event.repoId,
                        ),
                    )
                    if (event.generatedKey) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(Res.string.sync_connect_key_generated),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onResolve(true) }) {
                    Text(stringResource(Res.string.sync_connect_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { onResolve(false) }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )

        is ServerConnectEvent.Failed -> AlertDialog(
            onDismissRequest = { onResolve(false) },
            title = { Text(stringResource(Res.string.sync_connect_confirm_title)) },
            text = { Text(connectFailureText(event.failure)) },
            confirmButton = {
                TextButton(onClick = { onResolve(false) }) {
                    Text(stringResource(Res.string.ok))
                }
            },
        )
    }
}

@Composable
internal fun connectFailureText(failure: ServerConnectFailure): String = when (failure) {
    ServerConnectFailure.LinkMismatch ->
        stringResource(Res.string.sync_connect_error_link)
    ServerConnectFailure.NoConfig ->
        stringResource(Res.string.sync_connect_error_no_config)
    ServerConnectFailure.OtherServer ->
        stringResource(Res.string.sync_connect_error_other_server)
    ServerConnectFailure.NoKey ->
        stringResource(Res.string.sync_connect_error_no_key)
    ServerConnectFailure.Decode ->
        stringResource(Res.string.config_import_error)
}
