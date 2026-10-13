package com.nexora.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexora.android.R
import com.nexora.android.ui.components.MessageCard
import com.nexora.android.ui.components.NexoraCard
import com.nexora.android.ui.components.SectionHeader
import com.nexora.android.ui.saves.SavesViewModel
import com.nexora.android.ui.theme.NexoraAccent
import com.nexora.android.ui.theme.NexoraBackground
import com.nexora.android.ui.theme.NexoraTextPrimary
import com.nexora.android.ui.theme.NexoraTextSecondary

@Composable
fun SavesManagerRoute(
    gameId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SavesViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(gameId) { viewModel.load(gameId) }

    SavesManagerScreen(
        gameName = state.gameName,
        totalBytes = state.totalBytes,
        infoMessage = state.infoMessage,
        errorMessage = state.errorMessage,
        onBack = onBack,
        onExport = { /* TODO: pass actual export path */ },
        onImport = { /* TODO: pass actual import path */ },
        onCleanShaders = viewModel::cleanShaderCache,
        onCleanTemp = viewModel::cleanTemporary,
        onDismissMessages = viewModel::dismissMessages,
        modifier = modifier,
    )
}

@Composable
private fun SavesManagerScreen(
    gameName: String,
    totalBytes: Long,
    infoMessage: String?,
    errorMessage: String?,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onCleanShaders: () -> Unit,
    onCleanTemp: () -> Unit,
    onDismissMessages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = NexoraBackground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = null)
                }
                Spacer(modifier = Modifier.size(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.saves_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = NexoraTextPrimary,
                    )
                    Text(
                        text = gameName,
                        style = MaterialTheme.typography.bodySmall,
                        color = NexoraTextSecondary,
                    )
                }
            }

            if (infoMessage != null || errorMessage != null) {
                MessageCard(
                    message = infoMessage ?: errorMessage.orEmpty(),
                    isError = errorMessage != null,
                    onDismiss = onDismissMessages,
                )
            }

            SectionHeader(title = stringResource(R.string.saves_summary_title))
            NexoraCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.saves_prefix_size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NexoraTextSecondary,
                    )
                    Text(
                        text = formatBytes(totalBytes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NexoraTextPrimary,
                    )
                }
            }

            SectionHeader(title = stringResource(R.string.saves_backup_title))
            NexoraCard {
                Text(
                    text = stringResource(R.string.saves_backup_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NexoraTextSecondary,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onExport,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NexoraAccent,
                            contentColor = NexoraBackground,
                        ),
                    ) {
                        Icon(Icons.Outlined.FileUpload, contentDescription = null)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(stringResource(R.string.saves_export))
                    }
                    OutlinedButton(onClick = onImport) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(stringResource(R.string.saves_import))
                    }
                }
            }

            SectionHeader(title = stringResource(R.string.saves_cache_title))
            NexoraCard {
                Text(
                    text = stringResource(R.string.saves_cache_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NexoraTextSecondary,
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(onClick = onCleanShaders) {
                    Icon(Icons.Outlined.Memory, contentDescription = null)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(stringResource(R.string.saves_clean_shaders))
                }
            }

            SectionHeader(title = stringResource(R.string.saves_temp_title))
            NexoraCard {
                Text(
                    text = stringResource(R.string.saves_temp_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NexoraTextSecondary,
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(onClick = onCleanTemp) {
                    Icon(Icons.Outlined.CleaningServices, contentDescription = null)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(stringResource(R.string.saves_clean_temp))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.size - 1) {
        value /= 1024.0
        unitIndex += 1
    }
    return "%.2f %s".format(value, units[unitIndex])
}