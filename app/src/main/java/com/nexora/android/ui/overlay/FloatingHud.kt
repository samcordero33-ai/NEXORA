package com.nexora.android.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexora.android.runtime.overlay.FpsSource
import com.nexora.android.runtime.overlay.HudMode
import com.nexora.android.runtime.overlay.OverlayMetrics
import com.nexora.android.runtime.overlay.OverlayTone
import com.nexora.android.ui.theme.NexoraAccent
import com.nexora.android.ui.theme.NexoraBorder
import com.nexora.android.ui.theme.NexoraDanger
import com.nexora.android.ui.theme.NexoraSurface
import com.nexora.android.ui.theme.NexoraTextPrimary
import com.nexora.android.ui.theme.NexoraTextSecondary
import com.nexora.android.ui.theme.NexoraTheme
import com.nexora.android.ui.theme.NexoraWarning

/**
 * Contenido del HUD flotante (Fase 9).
 *
 * Es un composable **puro**: recibe las métricas ya calculadas y devuelve
 * callbacks. No conoce `WindowManager`, ni el servicio, ni permisos; eso vive
 * en `OverlayService`. Así se puede previsualizar y probar sin dispositivo.
 *
 * El arrastre se engancha **solo al asa** (el icono de puntos), no a toda la
 * tarjeta: si toda la superficie arrastrara, pulsar "cerrar" mientras el dedo
 * se mueve un píxel movería la ventana en vez de cerrarla.
 */
@Composable
fun FloatingHud(
    metrics: OverlayMetrics,
    mode: HudMode,
    onDrag: (Float, Float) -> Unit,
    onToggleMode: () -> Unit,
    onKill: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .width(mode.widthDp.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, NexoraBorder, RoundedCornerShape(16.dp)),
        color = NexoraSurface.copy(alpha = 0.94f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            HudHeader(
                mode = mode,
                onDrag = onDrag,
                onToggleMode = onToggleMode,
                onClose = onClose,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = metrics.fpsLabel,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = metrics.fpsTone.color(),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "FPS",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexoraTextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = metrics.fpsSource.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = NexoraTextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = metrics.summaryLabel,
                style = MaterialTheme.typography.bodySmall,
                color = NexoraTextPrimary,
            )

            if (mode == HudMode.EXPANDED) {
                Spacer(modifier = Modifier.height(6.dp))
                MetricBar(
                    label = "RAM",
                    value = "${metrics.ramPercent}% · ${metrics.ramLabel}",
                    fraction = metrics.ramPercent / 100f,
                    tone = metrics.ramTone,
                )
                Spacer(modifier = Modifier.height(4.dp))
                MetricRow(
                    label = "CPU",
                    value = "${metrics.cpuPercent}%",
                    tone = metrics.cpuTone,
                )
                MetricRow(
                    label = "TEMP",
                    value = "${metrics.cpuTempCelsius}° / ${metrics.gpuTempCelsius}°",
                    tone = metrics.tempTone,
                )
                MetricRow(
                    label = "SESIÓN",
                    value = metrics.sessionLabel,
                    tone = if (metrics.sessionActive) OverlayTone.GOOD else OverlayTone.NEUTRAL,
                )
                MetricRow(
                    label = "TIEMPO",
                    value = metrics.elapsedLabel,
                    tone = OverlayTone.NEUTRAL,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(NexoraDanger.copy(alpha = 0.16f))
                        .clickable(onClick = onKill),
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PowerSettingsNew,
                            contentDescription = null,
                            tint = NexoraDanger,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CERRAR JUEGO",
                            style = MaterialTheme.typography.labelMedium,
                            color = NexoraDanger,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HudHeader(
    mode: HudMode,
    onDrag: (Float, Float) -> Unit,
    onToggleMode: () -> Unit,
    onClose: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Outlined.DragIndicator,
            contentDescription = "Arrastrar HUD",
            tint = NexoraTextSecondary,
            modifier = Modifier
                .size(20.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                },
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "NEXORA",
            style = MaterialTheme.typography.labelSmall,
            color = NexoraAccent,
        )
        Spacer(modifier = Modifier.weight(1f))
        HudIconButton(
            icon = if (mode == HudMode.COMPACT) {
                Icons.Outlined.OpenInFull
            } else {
                Icons.Outlined.CloseFullscreen
            },
            description = if (mode == HudMode.COMPACT) "Maximizar HUD" else "Minimizar HUD",
            onClick = onToggleMode,
        )
        HudIconButton(
            icon = Icons.Outlined.Close,
            description = "Cerrar HUD",
            onClick = onClose,
        )
    }
}

@Composable
private fun HudIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(26.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = NexoraTextSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    tone: OverlayTone,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NexoraTextSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = tone.color(),
        )
    }
}

@Composable
private fun MetricBar(
    label: String,
    value: String,
    fraction: Float,
    tone: OverlayTone,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = NexoraTextSecondary,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = tone.color(),
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(NexoraBorder),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(tone.color()),
            )
        }
    }
}

/** Traduce el semáforo del HUD a color de la paleta. */
private fun OverlayTone.color(): Color = when (this) {
    OverlayTone.GOOD -> NexoraAccent
    OverlayTone.WARN -> NexoraWarning
    OverlayTone.BAD -> NexoraDanger
    OverlayTone.NEUTRAL -> NexoraTextSecondary
}

@Preview(showBackground = true, widthDp = 260, heightDp = 260)
@Composable
private fun FloatingHudPreview() {
    NexoraTheme {
        FloatingHud(
            metrics = OverlayMetrics(
                fps = 58,
                fpsSource = FpsSource.ENGINE,
                ramUsedBytes = 3_200_000_000L,
                ramTotalBytes = 8_000_000_000L,
                cpuPercent = 42,
                cpuTempCelsius = 55,
                gpuTempCelsius = 58,
                enginePid = 4821,
                sessionActive = true,
                elapsedMs = 754_000L,
            ),
            mode = HudMode.EXPANDED,
            onDrag = { _, _ -> },
            onToggleMode = {},
            onKill = {},
            onClose = {},
        )
    }
}
