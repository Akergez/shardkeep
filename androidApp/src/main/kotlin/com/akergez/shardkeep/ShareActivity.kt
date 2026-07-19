package com.akergez.shardkeep

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class ShareActivity : ComponentActivity() {
    private val viewModel: ShareViewModel by viewModels {
        val registry = WorkspaceRegistry(applicationContext)
        ShareViewModel.factory(
            payload = SharePayload.from(intent),
            workspaces = registry.list(),
            activeWorkspaceId = registry.active()?.id,
            saver = AndroidShareNoteSaver(applicationContext),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.saved) {
                if (state.saved) finish()
            }
            ShardkeepTheme {
                ShareScreen(
                    state = state,
                    onTitleChange = viewModel::onTitleChange,
                    onTextChange = viewModel::onTextChange,
                    onWorkspaceSelected = viewModel::onWorkspaceSelected,
                    onSave = viewModel::save,
                    onCancel = ::finish,
                )
            }
        }
    }
}

data class SharePayload(val text: String, val title: String, val imageUris: List<Uri>) {
    companion object {
        fun from(intent: Intent): SharePayload {
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
            val title = intent.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString()
                ?: intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
            val isImageShare = intent.type?.startsWith("image/") == true
            val streams = if (isImageShare) when (intent.action) {
                Intent.ACTION_SEND_MULTIPLE -> intent.parcelableUriList(Intent.EXTRA_STREAM)
                Intent.ACTION_SEND -> listOfNotNull(intent.parcelableUri(Intent.EXTRA_STREAM))
                else -> emptyList()
            } else emptyList()
            val clipUris = if (isImageShare) buildList {
                val clip = intent.clipData ?: return@buildList
                repeat(clip.itemCount) { index -> clip.getItemAt(index).uri?.let(::add) }
            } else emptyList()
            return SharePayload(text, title, (streams + clipUris).distinct())
        }
    }
}

@Suppress("DEPRECATION")
private fun Intent.parcelableUri(key: String): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableExtra(key, Uri::class.java)
    else getParcelableExtra(key)

@Suppress("DEPRECATION")
private fun Intent.parcelableUriList(key: String): List<Uri> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayListExtra(key, Uri::class.java).orEmpty()
    } else {
        getParcelableArrayListExtra<Uri>(key).orEmpty()
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareScreen(
    state: ShareUiState,
    onTitleChange: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onWorkspaceSelected: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    var workspaceMenuExpanded by remember { mutableStateOf(false) }
    val selectedWorkspace = state.workspaces.firstOrNull { it.id == state.selectedWorkspaceId }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.share_to_shardkeep)) }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.workspaces.isEmpty()) {
                Text(stringResource(R.string.share_no_workspace))
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.share_cancel))
                }
                return@Column
            }

            ExposedDropdownMenuBox(
                expanded = workspaceMenuExpanded,
                onExpandedChange = { workspaceMenuExpanded = it },
            ) {
                OutlinedTextField(
                    value = selectedWorkspace?.name.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.share_workspace)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(workspaceMenuExpanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = workspaceMenuExpanded,
                    onDismissRequest = { workspaceMenuExpanded = false },
                ) {
                    state.workspaces.forEach { workspace ->
                        DropdownMenuItem(
                            text = { Text(workspace.name) },
                            onClick = {
                                onWorkspaceSelected(workspace.id)
                                workspaceMenuExpanded = false
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.share_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.text,
                onValueChange = onTextChange,
                label = { Text(stringResource(R.string.share_text)) },
                minLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.imageCount > 0) Text(stringResource(R.string.share_images, state.imageCount))
            if (state.saveFailed) Text(stringResource(R.string.share_error))
            Spacer(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !state.saving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.share_cancel))
                }
                Button(
                    enabled = state.canSave,
                    modifier = Modifier.weight(1f),
                    onClick = onSave,
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.share_save))
                    }
                }
            }
        }
    }
}
