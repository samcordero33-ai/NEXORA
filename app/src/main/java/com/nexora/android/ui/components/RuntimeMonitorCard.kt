package com.nexora.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nexora.android.runtime.monitor.RuntimeMonitorSnapshot
import com.nexora.android.ui.theme.NexoraAccent
import com.nexora.android.ui.theme.NexoraBackground
import com.nexora.android.ui.theme.NexoraSurfaceElevated
import com.nexora.android.ui.theme.NexoraTextPrimary
import com.nexora.android.ui.theme.NexoraTextSecondary
import com.nexora.android.ui.theme.NexoraWarning

/**
 * Monitor técnico flotante: FPS, uso de RAM y temperaturas estimadas.
 *
 * Es un "panel dentro de la pantalla": está pensado para colocarse en una
 * esquina de la superficie de juego. La información de temperatura es
 * **estimada**: Android no expone un sensor térmico de CPU/GPU sin permisos
 * extra, así que la etiqueta "(estimado)" va siempre presente para no mentir.
 */
@Composable
fun RuntimeMonitorCard(
    snapshot: RuntimeMonitorSnapshot,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(NexoraSurfaceElevated),
        color = NexoraSurfaceElevated,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "MONITOR (estimado)",
                style = MaterialTheme.typography.labelSmall,
                color = NexoraTextSecondary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            MonitorStat(
                icon = Icons.Outlined.Timer,
                label = "FPS",
                value = if (snapshot.fps > 0) snapshot.fps.toString() else "—",
                tone = if (snapshot.fps >= 30) NexoraAccent else NexoraWarning,
            )
            Spacer(modifier = Modifier.height(4.dp))
            MonitorStat(
                icon = Icons.Outlined.Memory,
                label = "RAM",
                value = "${snapshot.ramUsagePercent}% · ${formatBytes(snapshot.usedRamBytes)}",
                tone = if (snapshot.ramUsagePercent < 75) NexoraAccent else NexoraWarning,
            )
            Spacer(modifier = Modifier.height(4.dp))
            MonitorStat(
                icon = Icons.Outlined.Thermostat,
                label = "CPU/GPU",
                value = "${snapshot.cpuTempCelsius}° / ${snapshot.gpuTempCelsius}°",
                tone = if (snapshot.cpuTempCelsius < 75) NexoraAccent else NexoraWarning,
            )
        }
    }
}

@Composable
private fun MonitorStat(
    icon: ImageVector,
    label: String,
    value: String,
    tone: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tone,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NexoraTextSecondary,
            modifier = Modifier.size(width = 56.dp, height = 16.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = NexoraTextPrimary,
        )
    }
}

/**
 * Formato corto de bytes: 1.4 GB, 720 MB, 8 KB. Para el monitor y los tamaños
 * pequeños del log.
 */
internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.1f GB", gb)
        mb >= 1.0 -> String.format("%.0f MB", mb)
        kb >= 1.0 -> String.format("%.0f KB", kb)
        else -> "$bytes B"
    }
}