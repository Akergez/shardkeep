package com.akergez.shardkeep.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akergez.shardkeep.SyncPhase
import com.akergez.shardkeep.SyncState
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource
import shardkeep.shared.generated.resources.Res
import shardkeep.shared.generated.resources.force_pull
import shardkeep.shared.generated.resources.storage_http
import shardkeep.shared.generated.resources.storage_s3
import shardkeep.shared.generated.resources.sync_auto_indicator
import shardkeep.shared.generated.resources.sync_configured
import shardkeep.shared.generated.resources.sync_not_configured
import shardkeep.shared.generated.resources.sync_now
import shardkeep.shared.generated.resources.sync_phase_applying
import shardkeep.shared.generated.resources.sync_phase_committing
import shardkeep.shared.generated.resources.sync_phase_done
import shardkeep.shared.generated.resources.sync_phase_error
import shardkeep.shared.generated.resources.sync_phase_idle
import shardkeep.shared.generated.resources.sync_phase_preparing
import shardkeep.shared.generated.resources.sync_phase_transferring
import shardkeep.shared.generated.resources.sync_read
import shardkeep.shared.generated.resources.sync_written
import shardkeep.shared.generated.resources.syncing

/**
 * Prominent, self-contained sync hub — status, live stage + per-object progress,
 * storage state, last result, and the action button all in one card. Mirrors the
 * companion plugin's activity view; driven by the host's sync status store, so
 * manual and background syncs render identically.
 */
@Composable
fun SyncStatusCard(
    stateFlow: StateFlow<SyncState>,
    syncing: Boolean,
    onSyncNow: (() -> Unit)?,
    onForcePull: (() -> Unit)?,
) {
    val state by stateFlow.collectAsStateWithLifecycle()
    val accent = when (state.phase) {
        SyncPhase.Error -> MaterialTheme.colorScheme.error
        SyncPhase.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Status headline + trigger tag.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.active) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = phaseLabelOf(state.phase),
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                modifier = Modifier.weight(1f),
            )
            state.trigger?.takeIf { state.active }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Live progress: indeterminate bar + per-stage counters while active.
        if (state.active) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        progressLineOf(state)?.takeIf { state.active }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Result / error of the last finished sync.
        if (!state.active) {
            val message = state.error ?: state.lastResult
            if (message != null) {
                Text(
                    text = state.lastSyncLabel?.takeIf { state.error == null }
                        ?.let { "$message · $it" } ?: message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.error != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Storage status line.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (state.storageType == "s3") {
                    stringResource(Res.string.storage_s3)
                } else {
                    stringResource(Res.string.storage_http)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("·", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = if (state.configured) {
                    stringResource(Res.string.sync_configured)
                } else {
                    stringResource(Res.string.sync_not_configured)
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (state.configured) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
            )
            if (state.autoSyncEnabled) {
                Text(
                    stringResource(Res.string.sync_auto_indicator),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Action.
        if (onSyncNow != null || onForcePull != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                onSyncNow?.let { run ->
                    Button(
                        onClick = run,
                        enabled = !syncing && !state.active,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (syncing || state.active) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            if (syncing || state.active) {
                                stringResource(Res.string.syncing)
                            } else {
                                stringResource(Res.string.sync_now)
                            },
                        )
                    }
                }
                onForcePull?.let { run ->
                    OutlinedButton(
                        onClick = run,
                        enabled = !syncing && !state.active,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(Res.string.force_pull))
                    }
                }
            }
        }
    }
}

@Composable
private fun phaseLabelOf(phase: SyncPhase): String = when (phase) {
    SyncPhase.Idle -> stringResource(Res.string.sync_phase_idle)
    SyncPhase.Preparing -> stringResource(Res.string.sync_phase_preparing)
    SyncPhase.Committing -> stringResource(Res.string.sync_phase_committing)
    SyncPhase.Transferring -> stringResource(Res.string.sync_phase_transferring)
    SyncPhase.Applying -> stringResource(Res.string.sync_phase_applying)
    SyncPhase.Done -> stringResource(Res.string.sync_phase_done)
    SyncPhase.Error -> stringResource(Res.string.sync_phase_error)
}

@Composable
private fun progressLineOf(state: SyncState): String? {
    val readLabel = stringResource(Res.string.sync_read, state.committed)
    val writtenLabel = stringResource(Res.string.sync_written, state.applied)
    return buildList {
        if (state.committed > 0) add(readLabel)
        if (state.uploaded > 0 || state.downloaded > 0) add("↑${state.uploaded} ↓${state.downloaded}")
        if (state.applied > 0) add(writtenLabel)
    }.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
