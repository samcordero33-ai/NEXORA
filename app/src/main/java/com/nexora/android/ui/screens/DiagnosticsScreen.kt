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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexora.android.R
import com.nexora.android.runtime.diagnostics.CrashFinding
import com.nexora.android.runtime.diagnostics.DiagnosticsEvent
import com.nexora.android.runtime.diagnostics.DiagnosticsLevel
import com.nexora.android.runtime.diagnostics.LogFileInfo
import com.nexora.android.ui.components.MessageCard
import com.nexora.android.ui.components.NexoraCard
import com.nexora.android.ui.components.SectionHeader
import com.nexora.android.ui.diagnostics.DiagnosticsTab
import com.nexora.android.ui.diagnostics.DiagnosticsViewModel
import com.nexora.android.ui.theme.NexoraAccent
import com.nexora.android.ui.theme.NexoraBackground
import com.nexora.android.ui.theme.NexoraBorder
import com.nexora.android.ui.theme.NexoraDanger
import com.nexora.android.ui.theme.NexoraSurface
import com.nexora.android.ui.theme.NexoraTextPrimary
import com.nexora.android.ui.theme.NexoraTextSecondary
import com.nexora.android.ui.theme.NexoraWarning

/**
 * Consola de diagnóstico con dos vistas (Fase 9).
 *
 * - **En vivo**: el ring buffer en memoria, útil mientras el juego corre.
 * - **Sesión**: los `.log` que deja [com.nexora.android.runtime.diagnostics.SessionLogRecorder],
 *   con el diagnóstico automático de [com.nexora.android.runtime.diagnostics.CrashLogAnalyzer].
 *
 * La segunda es la que sirve cuando la app se ha cerrado sola: el buffer en
 * memoria se fue con el proceso, el archivo no.
 */
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
        tab = state.tab,
        logs = state.logs,
        selectedLog = state.selectedLog,
        logLines = state.logLines,
        finding = state.finding,
        infoMessage = state.infoMessage,
        errorMessage = state.errorMessage,
        onBack = onBack,
        onTabChange = viewModel::setTab,
        onFilterChange = viewModel::setFilter,
        onExport = viewModel::exportLog,
        onShare = { shared?.let { context.startActivity(it) } },
        onClear = viewModel::clearLog,
        onRefreshLogs = viewModel::refreshLogs,
        onOpenLog = viewModel::openLog,
        onCloseLog = viewModel::closeLog,
        onDeleteLog = viewModel::deleteLog,
        onShareLog = { info -> viewModel.shareIntentFor(info)?.let { context.startActivity(it) } },
        onDismissMessages = viewModel::dismissMessages,
        canShare = shared != null,
        modifier = modifier,
    )
}

@Composable
private fun DiagnosticsScreen(
    events: List<DiagnosticsEvent>,
    filterLevel: DiagnosticsLevel,
    tab: DiagnosticsTab,
    logs: List<LogFileInfo>,
    selectedLog: LogFileInfo?,
    logLines: List<String>,
    finding: CrashFinding?,
    infoMessage: String?,
    errorMessage: String?,
    onBack: () -> Unit,
    onTabChange: (DiagnosticsTab) -> Unit,
    onFilterChange: (DiagnosticsLevel) -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
    onRefreshLogs: () -> Unit,
    onOpenLog: (LogFileInfo) -> Unit,
    onCloseLog: () -> Unit,
    onDeleteLog: (LogFileInfo) -> Unit,
    onShareLog: (LogFileInfo) -> Unit,
    onDismissMessages: () -> Unit,
    canShare: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = NexoraBackground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
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
                DiagnosticsTab.values().forEach { candidate ->
                    AssistChip(
                        onClick = { onTabChange(candidate) },
                        label = { Text(candidate.label) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (candidate == tab) {
                                NexoraAccent
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            labelColor = if (candidate == tab) NexoraBackground else NexoraTextPrimary,
                        ),
                    )
                }
            }

            when (tab) {
                DiagnosticsTab.LIVE -> LiveTab(
                    events = events,
                    filterLevel = filterLevel,
                    onFilterChange = onFilterChange,
                    onExport = onExport,
                    onShare = onShare,
                    onClear = onClear,
                    canShare = canShare,
                )

                DiagnosticsTab.SESSION -> SessionTab(
                    logs = logs,
                    selectedLog = selectedLog,
                    logLines = logLines,
                    finding = finding,
                    onRefresh = onRefreshLogs,
                    onOpen = onOpenLog,
                    onClose = onCloseLog,
                    onDelete = onDeleteLog,
                    onShare = onShareLog,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Pestaña "En vivo"
// ---------------------------------------------------------------------------

@Composable
private fun LiveTab(
    events: List<DiagnosticsEvent>,
    filterLevel: DiagnosticsLevel,
    onFilterChange: (DiagnosticsLevel) -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
    canShare: Boolean,
) {
    val listState = rememberLazyListState()
    val filtered = remember(events, filterLevel) {
        events.filter { it.level.ordinal >= filterLevel.ordinal }
    }
    LaunchedEffect(filtered.size) {
        if (filtered.isNotEmpty()) listState.scrollToItem(filtered.lastIndex)
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
                    labelColor = if (level == filterLevel) NexoraBackground else NexoraTextPrimary,
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
            Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(6.dp))
            Text(stringResource(R.string.diagnostics_export))
        }
        OutlinedButton(onClick = onShare, enabled = canShare) {
            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
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
            .background(color = NexoraSurface, shape = RoundedCornerShape(12.dp))
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
            items(items = filtered, key = { it.timestampMs.toString() + it.message }) { event ->
                DiagnosticsLine(event)
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = NexoraSurface, shape = RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Text(
            text = stringResource(R.string.diagnostics_notice),
            style = MaterialTheme.typography.bodySmall,
            color = NexoraTextSecondary,
        )
    }
}

// ---------------------------------------------------------------------------
// Pestaña "Sesión"
// ---------------------------------------------------------------------------

@Composable
private fun SessionTab(
    logs: List<LogFileInfo>,
    selectedLog: LogFileInfo?,
    logLines: List<String>,
    finding: CrashFinding?,
    onRefresh: () -> Unit,
    onOpen: (LogFileInfo) -> Unit,
    onClose: () -> Unit,
    onDelete: (LogFileInfo) -> Unit,
    onShare: (LogFileInfo) -> Unit,
) {
    if (selectedLog != null) {
        LogViewer(
            info = selectedLog,
            lines = logLines,
            finding = finding,
            onBack = onClose,
            onShare = { onShare(selectedLog) },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRefresh) {
                Text("Actualizar lista")
            }
        }

        if (logs.isEmpty()) {
            NexoraCard {
                Text(
                    text = "Todavía no hay registros de sesión. Se crean al lanzar un juego: " +
                        "la salida de Wine y Box64 se guarda en filesDir/logs para poder " +
                        "diagnosticar el fallo aunque la app se cierre.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NexoraTextSecondary,
                )
            }
        } else {
            logs.forEach { info ->
                LogRow(
                    info = info,
                    onOpen = { onOpen(info) },
                    onDelete = { onDelete(info) },
                    onShare = { onShare(info) },
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Cada archivo corresponde a una ejecución. Se conservan aunque la app se " +
                "cierre de golpe: el registro se vuelca a disco línea a línea.",
            style = MaterialTheme.typography.bodySmall,
            color = NexoraTextSecondary,
        )
    }
}

@Composable
private fun LogRow(
    info: LogFileInfo,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    NexoraCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Terminal,
                contentDescription = null,
                tint = NexoraAccent,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NexoraTextPrimary,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = formatBytes(info.bytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = NexoraTextSecondary,
                )
            }
            IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = "Compartir registro",
                    tint = NexoraTextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Borrar registro",
                    tint = NexoraWarning,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
            Text("Abrir y analizar")
        }
    }
}

@Composable
private fun LogViewer(
    info: LogFileInfo,
    lines: List<String>,
    finding: CrashFinding?,
    onBack: () -> Unit,
    onShare: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = info.name,
                style = MaterialTheme.typography.bodyMedium,
                color = NexoraTextPrimary,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = "Compartir",
                    tint = NexoraTextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        if (finding != null) {
            CrashCard(finding)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(color = NexoraSurface, shape = RoundedCornerShape(12.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (lines.isEmpty()) {
                item {
                    Text(
                        text = "El archivo está vacío.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NexoraTextSecondary,
                    )
                }
            } else {
                items(items = lines) { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = lineColor(line),
                    )
                }
            }
        }
    }
}

/**
 * Tarjeta de diagnóstico del fallo.
 *
 * Cuando no se reconoce nada lo dice explícitamente: un "sin fallo detectado"
 * es información, y evita que el usuario dé por bueno un análisis que no se ha
 * hecho.
 */
@Composable
private fun CrashCard(finding: CrashFinding) {
    val detected = finding.detected
    NexoraCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (detected) Icons.Outlined.WarningAmber else Icons.Outlined.Terminal,
                contentDescription = null,
                tint = if (detected) NexoraDanger else NexoraTextSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = finding.signature.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (detected) NexoraDanger else NexoraTextPrimary,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = finding.suggestion,
            style = MaterialTheme.typography.bodySmall,
            color = NexoraTextSecondary,
        )

        if (finding.evidence.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader(title = "Evidencia")
            finding.evidence.forEach { line ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .background(color = NexoraBackground, shape = RoundedCornerShape(6.dp))
                        .padding(6.dp),
                ) {
                    Text(
                        text = line,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = NexoraTextPrimary,
                    )
                }
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

/** Color de una línea del log según lo que contenga. */
private fun lineColor(line: String): androidx.compose.ui.graphics.Color {
    val lower = line.lowercase()
    return when {
        lower.contains("err:") || lower.contains("error") || lower.contains("fault") -> NexoraWarning
        line.startsWith("#") -> NexoraTextSecondary
        else -> NexoraTextPrimary
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index += 1
    }
    return "%.2f %s".format(value, units[index])
}
