package com.nexora.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexora.android.R
import com.nexora.android.runtime.diagnostics.DiagnosticsEvent
import com.nexora.android.runtime.diagnostics.DiagnosticsLevel
import com.nexora.android.ui.components.MessageCard
import com.nexora.android.ui.diagnostics.DiagnosticsViewModel
import com.nexora.android.ui.theme.NexoraAccent
import com.nexora.android.ui.theme.NexoraBackground
import com.nexora.android.ui.theme.NexoraTextPrimary
import com.nexora.android.ui.theme.NexoraTextSecondary
import com.nexora.android.ui.theme.NexoraWarning

@Composable
fun DiagnosticsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiagnosticsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shared = remember(state.exportedFile) { viewModel.shareIntent() }

    DiagnosticsScreen(
        events = state.events,
        filterLevel = state.filterLevel,
        infoMessage = state.infoMessage,
        errorMessage = state.errorMessage,
        onBack = onBack,
        onFilterChange = viewModel::setFilter,
        onExport = viewModel::exportLog,
        onShare = { shared?.let { context.startActivity(it) } },
        onClear = viewModel::clearLog,
        onDismissMessages = viewModel::dismissMessages,
        canShare = shared != null,
        modifier = modifier,
    )
}

@Composable
private fun DiagnosticsScreen(
    events: List<DiagnosticsEvent>,
    filterLevel: DiagnosticsLevel,
    infoMessage: String?,
    errorMessage: String?,
    onBack: () -> Unit,
    onFilterChange: (DiagnosticsLevel) -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
    onDismissMessages: () -> Unit,
    canShare: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val filtered = remember(events, filterLevel) {
        events.filter { it.level.ordinal >= filterLevel.ordinal }
    }
    androidx.compose.runtime.LaunchedEffect(filtered.size) {
        if (filtered.isNotEmpty()) listState.scrollToItem(filtered.lastIndex)
    }

    Surface(modifier = modifier.fillMaxSize(), color = NexoraBackground) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = null)
                }
                Spacer(modifier = Modifier.size(12.dp))
                Text(
                    text = stringResource(R.string.diagnostics_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = NexoraTextPrimary,
                )
            }

            if (infoMessage != null || errorMessage != null) {
                MessageCard(
                    message = infoMessage ?: errorMessage.orEmpty(),
                    isError = errorMessage != null,
                    onDismiss = onDismissMessages,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DiagnosticsLevel.values().forEach { level ->
                    AssistChip(
                        onClick = { onFilterChange(level) },
                        label = { Text(level.tag) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (level == filterLevel) {
                                NexoraAccent
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            labelColor = if (level == filterLevel) {
                                NexoraBackground
                            } else {
                                NexoraTextPrimary
                            },
                        ),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onExport,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NexoraAccent,
                        contentColor = NexoraBackground,
                    ),
                ) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(stringResource(R.string.diagnostics_export))
                }
                OutlinedButton(onClick = onShare, enabled = canShare) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(stringResource(R.string.diagnostics_share))
                }
                OutlinedButton(onClick = onClear) {
                    Text(stringResource(R.string.diagnostics_clear))
                }
            }

            Text(
                text = stringResource(R.string.diagnostics_count, filtered.size, events.size),
                style = MaterialTheme.typography.bodySmall,
                color = NexoraTextSecondary,
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.diagnostics_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = NexoraTextSecondary,
                        )
                    }
                } else {
                    items(items = filtered, key = { it.timestampMs.toString() + it.message }) { ev ->
                        DiagnosticsLine(ev)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                    )
                    .padding(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.diagnostics_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = NexoraTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsLine(event: DiagnosticsEvent) {
    val color = when (event.level) {
        DiagnosticsLevel.ERROR -> NexoraWarning
        DiagnosticsLevel.WARN -> NexoraWarning
        DiagnosticsLevel.INFO -> NexoraTextPrimary
    }
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = DiagnosticsEvent.formatTimestamp(event.timestampMs),
            style = MaterialTheme.typography.labelSmall,
            color = NexoraTextSecondary,
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = event.level.tag,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = event.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = NexoraTextPrimary,
        )
    }
}