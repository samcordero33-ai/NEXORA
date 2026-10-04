package com.nexora.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nexora.android.runtime.model.LogStream
import com.nexora.android.runtime.model.RuntimeLog
import com.nexora.android.ui.theme.NexoraAccent
import com.nexora.android.ui.theme.NexoraBackground
import com.nexora.android.ui.theme.NexoraBorder
import com.nexora.android.ui.theme.NexoraSurface
import com.nexora.android.ui.theme.NexoraTextPrimary
import com.nexora.android.ui.theme.NexoraTextSecondary
import com.nexora.android.ui.theme.NexoraWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Panel de logs del runtime.
 *
 * Pinta las últimas N líneas del proceso. Cada línea lleva un sello
 * coloreado (stdout vs stderr vs sistema) y la marca de tiempo local. Si la
 * lista se queda atrás del ritmo de producción, hace **auto-scroll** solo
 * cuando el usuario ya estaba abajo: nunca le quita el control.
 */
@Composable
fun RuntimeLogPanel(
    logs: List<RuntimeLog>,
    modifier: Modifier = Modifier,
    maxLines: Int = 200,
) {
    val visible = if (logs.size > maxLines) logs.takeLast(maxLines) else logs
    val listState = rememberLazyListState()
    var wasAtEnd by remember { mutableStateOf(true) }
    LaunchedEffect(visible.size) {
        if (wasAtEnd && visible.isNotEmpty()) {
            listState.scrollToItem(visible.lastIndex)
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { /* nada: el snapshot es estático */ }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(NexoraBackground)
            .border(width = 1.dp, color = NexoraBorder, shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        if (visible.isEmpty()) {
            Text(
                text = "Sin logs todavía. Lanza el juego para empezar a ver la salida del motor.",
                style = MaterialTheme.typography.bodySmall,
                color = NexoraTextSecondary,
            )
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(visible, key = { it.timestampMs.toString() + it.message.hashCode() }) { log ->
                    LogRow(log)
                }
            }
        }
    }
}

@Composable
private fun LogRow(log: RuntimeLog) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = formatTime(log.timestampMs),
            style = MaterialTheme.typography.labelSmall,
            color = NexoraTextSecondary,
            modifier = Modifier.padding(top = 1.dp),
        )
        Text(
            text = log.stream.label,
            style = MaterialTheme.typography.labelSmall,
            color = colorFor(log.stream),
        )
        Text(
            text = log.message,
            style = MaterialTheme.typography.bodySmall,
            color = NexoraTextPrimary,
        )
    }
}

private fun colorFor(stream: LogStream): Color = when (stream) {
    LogStream.STDOUT -> NexoraAccent
    LogStream.STDERR -> NexoraWarning
    LogStream.SYSTEM -> NexoraTextSecondary
}

private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.ROOT)

private fun formatTime(epochMs: Long): String =
    if (epochMs <= 0L) "--:--:--" else TIME_FORMAT.format(Date(epochMs))

/** Resumen textual del panel: número de líneas por canal. */
@Composable
fun RuntimeLogSummary(
    logs: List<RuntimeLog>,
    modifier: Modifier = Modifier,
) {
    val stdout = logs.count { it.stream == LogStream.STDOUT }
    val stderr = logs.count { it.stream == LogStream.STDERR }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "stdout $stdout",
            style = MaterialTheme.typography.labelSmall,
            color = NexoraAccent,
        )
        Text(
            text = "stderr $stderr",
            style = MaterialTheme.typography.labelSmall,
            color = NexoraWarning,
        )
        Spacer(modifier = Modifier.height(0.dp))
        Text(
            text = "total ${logs.size}",
            style = MaterialTheme.typography.labelSmall,
            color = NexoraTextSecondary,
        )
    }
    // Ocultamos la cabecera del resumen cuando no hay logs: el panel muestra
    // su propio mensaje vacío.
    if (logs.isEmpty()) {
        Column {
            Text(
                text = "Aún no se ha capturado salida del motor.",
                style = MaterialTheme.typography.bodySmall,
                color = NexoraTextSecondary,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}

/** Versión compacta: una sola fila, sin `LazyColumn`. */
@Composable
fun RuntimeLogOneLine(logs: List<RuntimeLog>) {
    val last = logs.lastOrNull() ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = formatTime(last.timestampMs),
            style = MaterialTheme.typography.labelSmall,
            color = NexoraTextSecondary,
        )
        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
        Text(
            text = last.stream.label,
            style = MaterialTheme.typography.labelSmall,
            color = colorFor(last.stream),
        )
        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
        Text(
            text = last.message,
            style = MaterialTheme.typography.bodySmall,
            color = NexoraTextPrimary,
        )
    }
}

/**
 * Reutilizable: tarjeta con un título y un slot. Se usa para envolver la
 * salida del motor y la cabecera del monitor con la misma forma visual.
 */
@Composable
fun RuntimeSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(NexoraSurface)
            .padding(14.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = NexoraTextPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}