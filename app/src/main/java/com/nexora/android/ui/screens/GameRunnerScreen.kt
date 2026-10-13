package com.nexora.android.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexora.android.R
import com.nexora.android.control.model.ControlLayout
import com.nexora.android.engine.input.InputEvent
import com.nexora.android.runtime.env.Box64DynarecSize
import com.nexora.android.runtime.env.Box64LogLevel
import com.nexora.android.runtime.env.ComposedEnvironment
import com.nexora.android.runtime.env.DxvkHudPreset
import com.nexora.android.runtime.env.EnvironmentSettings
import com.nexora.android.runtime.env.GalliumDriver
import com.nexora.android.runtime.env.MesaVkWinsys
import com.nexora.android.runtime.model.RuntimeStatus
import com.nexora.android.runtime.model.label
import com.nexora.android.ui.components.NexoraCard
import com.nexora.android.ui.components.RuntimeLogPanel
import com.nexora.android.ui.components.RuntimeLogSummary
import com.nexora.android.ui.components.RuntimeMonitorCard
import com.nexora.android.ui.components.RuntimeSectionCard
import com.nexora.android.ui.components.TouchControlsOverlay
import com.nexora.android.ui.runner.RuntimeViewModel
import com.nexora.android.ui.theme.NexoraAccent
import com.nexora.android.ui.theme.NexoraAccentMuted
import com.nexora.android.ui.theme.NexoraBackground
import com.nexora.android.ui.theme.NexoraBorder
import com.nexora.android.ui.theme.NexoraSurface
import com.nexora.android.ui.theme.NexoraSurfaceElevated
import com.nexora.android.ui.theme.NexoraTextPrimary
import com.nexora.android.ui.theme.NexoraTextSecondary
import com.nexora.android.ui.theme.NexoraWarning

/**
 * Ruta de la pantalla de ejecución.
 *
 * Se compone de tres zonas:
 * 1. Una superficie que **representa** el juego: aquí vivirá la imagen del
 *    `.exe` cuando llegue el renderizado real. Sobre esa superficie se monta
 *    la capa de controles táctil (Fase 5) y, si el usuario lo activa, un
 *    monitor técnico flotante (Fase 6).
 * 2. Una columna con los controles de orquestación (preparar, lanzar,
 *    pausar, reanudar, cerrar) y los ajustes del entorno.
 * 3. Un panel con la salida en vivo del motor (stdout/stderr).
 *
 * La pantalla se monta encima de la biblioteca cuando el usuario pulsa
 * "Jugar" en el detalle. Volver sale del motor.
 */
@Composable
fun GameRunnerRoute(
    gameId: String,
    controlLayout: ControlLayout?,
    modifier: Modifier = Modifier,
    viewModel: RuntimeViewModel = viewModel(),
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(gameId) {
        viewModel.loadGame(gameId)
    }

    BackHandler(enabled = true) {
        viewModel.shutdown()
        onBack()
    }

    GameRunnerScreen(
        uiState = uiState,
        controlLayout = controlLayout,
        onBack = {
            viewModel.shutdown()
            onBack()
        },
        onPrepare = viewModel::prepareEnvironment,
        onLaunch = viewModel::launch,
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        onKill = viewModel::kill,
        onToggleMonitor = viewModel::setMonitorVisible,
        onToggleHud = viewModel::setHudVisible,
        onSaveSettings = viewModel::saveSettings,
        onResetSettings = viewModel::resetSettings,
        onSaveAudio = viewModel::saveAudioSettings,
        onResetAudio = viewModel::resetAudioSettings,
        onGesture = { /* los gestos se envían al runtime en una fase posterior */ },
        onDismissMessage = viewModel::dismissMessages,
        modifier = modifier,
    )
}

/**
 * Pantalla pura de ejecución. Toma todo por parámetro: así se puede previsualizar
 * con un [RuntimeUiState] fijo sin ViewModel ni base de datos.
 */
@Composable
fun GameRunnerScreen(
    uiState: com.nexora.android.ui.runner.RuntimeUiState,
    controlLayout: ControlLayout?,
    onBack: () -> Unit,
    onPrepare: () -> Unit,
    onLaunch: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onKill: () -> Unit,
    onToggleMonitor: (Boolean) -> Unit,
    onToggleHud: (Boolean) -> Unit,
    onSaveSettings: (EnvironmentSettings) -> Unit,
    onResetSettings: () -> Unit,
    onSaveAudio: (com.nexora.android.runtime.audio.AudioSettings) -> Unit,
    onResetAudio: () -> Unit,
    onGesture: (InputEvent) -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val effectiveControlLayout = controlLayout ?: ControlLayout.defaults(uiState.game?.id.orEmpty())
    val isRunning = uiState.status is RuntimeStatus.Running ||
        uiState.status is RuntimeStatus.Paused

    Surface(modifier = modifier.fillMaxSize(), color = NexoraBackground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            TopBar(
                title = uiState.game?.name ?: "Ejecutando…",
                status = uiState.status,
                onBack = onBack,
            )

            Spacer(modifier = Modifier.height(12.dp))

            GameSurface(
                layout = effectiveControlLayout,
                hudVisible = uiState.hudVisible,
                monitorVisible = uiState.monitorVisible,
                monitor = uiState.monitor,
                isRunning = isRunning,
                onGesture = onGesture,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Toggles flotantes
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleChip(
                    label = "HUD",
                    on = uiState.hudVisible,
                    icon = Icons.Outlined.SportsEsports,
                    onToggle = { onToggleHud(it) },
                )
                ToggleChip(
                    label = "Monitor",
                    on = uiState.monitorVisible,
                    icon = Icons.Outlined.MonitorHeart,
                    onToggle = { onToggleMonitor(it) },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LifecycleButtons(
                status = uiState.status,
                onPrepare = onPrepare,
                onLaunch = onLaunch,
                onPause = onPause,
                onResume = onResume,
                onKill = onKill,
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.warnings.isNotEmpty()) {
                WarningsCard(warnings = uiState.warnings)
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (uiState.composed != null) {
                EnvironmentSummaryCard(composed = uiState.composed, settings = uiState.settings)
                Spacer(modifier = Modifier.height(12.dp))
                EnvironmentSettingsCard(
                    settings = uiState.settings,
                    onSave = onSaveSettings,
                    onReset = onResetSettings,
                )
                Spacer(modifier = Modifier.height(12.dp))
                AudioSettingsCard(
                    settings = uiState.audioSettings,
                    onSave = onSaveAudio,
                    onReset = onResetAudio,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            RuntimeSectionCard(title = "Salida del motor") {
                RuntimeLogSummary(logs = uiState.logs)
                Spacer(modifier = Modifier.height(8.dp))
                RuntimeLogPanel(
                    logs = uiState.logs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                )
            }

            if (uiState.infoMessage != null || uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                MessageCard(
                    message = uiState.infoMessage ?: uiState.errorMessage.orEmpty(),
                    isError = uiState.errorMessage != null,
                    onDismiss = onDismissMessage,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    status: RuntimeStatus,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Outlined.ArrowBack,
                contentDescription = null,
                tint = NexoraTextPrimary,
            )
        }
        Spacer(modifier = Modifier.size(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = NexoraTextPrimary,
            )
            Text(
                text = status.label,
                style = MaterialTheme.typography.bodySmall,
                color = colorForStatus(status),
            )
        }
        StatusChip(status = status)
    }
}

@Composable
private fun StatusChip(status: RuntimeStatus) {
    val (label, color) = when (status) {
        is RuntimeStatus.Idle -> "Inactivo" to NexoraTextSecondary
        is RuntimeStatus.Preparing -> "Preparando" to NexoraAccent
        is RuntimeStatus.Running -> "En ejecución" to NexoraAccent
        is RuntimeStatus.Paused -> "Pausado" to NexoraWarning
        is RuntimeStatus.Stopped -> "Detenido" to NexoraTextSecondary
        is RuntimeStatus.Failed -> "Error" to NexoraWarning
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun colorForStatus(status: RuntimeStatus): Color = when (status) {
    is RuntimeStatus.Idle, is RuntimeStatus.Stopped -> NexoraTextSecondary
    is RuntimeStatus.Preparing, is RuntimeStatus.Running -> NexoraAccent
    is RuntimeStatus.Paused, is RuntimeStatus.Failed -> NexoraWarning
}

@Composable
private fun GameSurface(
    layout: ControlLayout,
    hudVisible: Boolean,
    monitorVisible: Boolean,
    monitor: com.nexora.android.runtime.monitor.RuntimeMonitorSnapshot,
    isRunning: Boolean,
    onGesture: (InputEvent) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF02050A))
            .border(1.dp, NexoraBorder, RoundedCornerShape(16.dp)),
    ) {
        // Patrón de cuadrícula para que se vea que es una "superficie de juego".
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF07111C),
                            Color(0xFF02050A),
                            Color(0xFF0A1422),
                        ),
                    ),
                ),
        )

        // Mensaje central mientras no hay renderizado real.
        if (!isRunning) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.SportsEsports,
                        contentDescription = null,
                        tint = NexoraAccentMuted,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "La superficie del juego aparecerá aquí cuando llegue el renderizado real.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NexoraTextSecondary,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
        }

        if (hudVisible) {
            TouchControlsOverlay(
                layout = layout,
                gesturesEnabled = true,
                editable = false,
                onEvent = onGesture,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(layout.overlayOpacity.coerceIn(0f, 1f)),
            )
        }

        if (monitorVisible) {
            Box(modifier = Modifier.padding(8.dp)) {
                RuntimeMonitorCard(snapshot = monitor)
            }
        }
    }
}

@Composable
private fun ToggleChip(
    label: String,
    on: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onToggle: (Boolean) -> Unit,
) {
    OutlinedButton(
        onClick = { onToggle(!on) },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (on) NexoraAccent.copy(alpha = 0.16f) else NexoraSurface,
            contentColor = if (on) NexoraAccent else NexoraTextPrimary,
        ),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun LifecycleButtons(
    status: RuntimeStatus,
    onPrepare: () -> Unit,
    onLaunch: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onKill: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Primera fila: preparar y lanzar.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onPrepare,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.size(6.dp))
                Text("Preparar entorno", style = MaterialTheme.typography.labelMedium)
            }
            Button(
                onClick = onLaunch,
                enabled = status is RuntimeStatus.Idle ||
                    status is RuntimeStatus.Preparing ||
                    status is RuntimeStatus.Stopped,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NexoraAccent,
                    contentColor = NexoraBackground,
                    disabledContainerColor = NexoraAccentMuted,
                    disabledContentColor = NexoraTextSecondary,
                ),
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.size(6.dp))
                Text("Lanzar", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(modifier = Modifier.size(8.dp))

        // Segunda fila: pausar, reanudar, cerrar.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onPause,
                enabled = status is RuntimeStatus.Running,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Pausar", style = MaterialTheme.typography.labelMedium) }

            OutlinedButton(
                onClick = onResume,
                enabled = status is RuntimeStatus.Paused,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Reanudar", style = MaterialTheme.typography.labelMedium) }

            OutlinedButton(
                onClick = onKill,
                enabled = status is RuntimeStatus.Running ||
                    status is RuntimeStatus.Paused ||
                    status is RuntimeStatus.Preparing,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text("Cerrar", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun WarningsCard(warnings: List<String>) {
    NexoraCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.runtime_warnings_title),
            style = MaterialTheme.typography.titleSmall,
            color = NexoraWarning,
        )
        Spacer(modifier = Modifier.height(6.dp))
        warnings.forEach { line ->
            Text(
                text = "• $line",
                style = MaterialTheme.typography.bodySmall,
                color = NexoraTextSecondary,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun EnvironmentSummaryCard(composed: ComposedEnvironment, settings: EnvironmentSettings) {
    RuntimeSectionCard(title = stringResource(R.string.runtime_env_title)) {
        SummaryRow(label = "WINEPREFIX", value = composed.winePrefixPath)
        SummaryRow(
            label = "DXVK_HUD",
            value = "${settings.dxvkHud.wireValue} (${settings.dxvkHud.label})",
        )
        SummaryRow(
            label = "BOX64_DYNAREC / LOG",
            value = "${settings.box64Dynarec.wireValue} · ${settings.box64Log.wireValue}",
        )
        SummaryRow(
            label = "GALLIUM_DRIVER",
            value = settings.galliumDriver.wireValue.ifEmpty { "(por defecto)" },
        )
        SummaryRow(
            label = "MESA_VK_WINSYS",
            value = settings.mesaVkWinsys.wireValue.ifEmpty { "(por defecto)" },
        )
        if (settings.wineDebug.isNotBlank()) {
            SummaryRow(label = "WINEDEBUG", value = settings.wineDebug)
        }
        if (settings.winetricksVerbs.isNotEmpty()) {
            SummaryRow(label = "winetricks", value = settings.winetricksVerbs.joinToString(", "))
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = NexoraTextSecondary,
            modifier = Modifier.width(140.dp),
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = NexoraTextPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun EnvironmentSettingsCard(
    settings: EnvironmentSettings,
    onSave: (EnvironmentSettings) -> Unit,
    onReset: () -> Unit,
) {
    var draft by remember(settings) { androidx.compose.runtime.mutableStateOf(settings) }

    RuntimeSectionCard(title = stringResource(R.string.runtime_env_adjust_title)) {
        EnumRow(
            label = "DXVK_HUD",
            current = draft.dxvkHud.label,
            options = DxvkHudPreset.entries.map { it.label },
            onSelect = { idx -> draft = draft.copy(dxvkHud = DxvkHudPreset.entries[idx]) },
        )
        Spacer(modifier = Modifier.height(6.dp))
        EnumRow(
            label = "BOX64_LOG",
            current = draft.box64Log.label,
            options = Box64LogLevel.entries.map { it.label },
            onSelect = { idx -> draft = draft.copy(box64Log = Box64LogLevel.entries[idx]) },
        )
        Spacer(modifier = Modifier.height(6.dp))
        EnumRow(
            label = "BOX64_DYNAREC",
            current = draft.box64Dynarec.label,
            options = Box64DynarecSize.entries.map { it.label },
            onSelect = { idx -> draft = draft.copy(box64Dynarec = Box64DynarecSize.entries[idx]) },
        )
        Spacer(modifier = Modifier.height(6.dp))
        EnumRow(
            label = "GALLIUM_DRIVER",
            current = draft.galliumDriver.label,
            options = GalliumDriver.entries.map { it.label },
            onSelect = { idx -> draft = draft.copy(galliumDriver = GalliumDriver.entries[idx]) },
        )
        Spacer(modifier = Modifier.height(6.dp))
        EnumRow(
            label = "MESA_VK_WINSYS",
            current = draft.mesaVkWinsys.label,
            options = MesaVkWinsys.entries.map { it.label },
            onSelect = { idx -> draft = draft.copy(mesaVkWinsys = MesaVkWinsys.entries[idx]) },
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onSave(draft) },
                modifier = Modifier.weight(1f).height(42.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NexoraAccent,
                    contentColor = NexoraBackground,
                ),
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text("Guardar ajustes", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f).height(42.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text("Restablecer", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun EnumRow(
    label: String,
    current: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = NexoraTextSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = current,
                style = MaterialTheme.typography.bodySmall,
                color = NexoraAccent,
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            options.forEachIndexed { index, option ->
                val isSelected = option == current
                TextButton(
                    onClick = { onSelect(index) },
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp),
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) NexoraAccent else NexoraTextSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Tarjeta de ajustes de audio del juego (Fase 7).
 *
 * Es paralela a [EnvironmentSettingsCard], pero separada: el audio es ortogonal
 * al resto del entorno y queremos poder restablecerlo independientemente.
 */
@Composable
private fun AudioSettingsCard(
    settings: com.nexora.android.runtime.audio.AudioSettings,
    onSave: (com.nexora.android.runtime.audio.AudioSettings) -> Unit,
    onReset: () -> Unit,
) {
    var draft by androidx.compose.runtime.remember(settings) {
        androidx.compose.runtime.mutableStateOf(settings)
    }

    RuntimeSectionCard(title = stringResource(R.string.audio_section_title)) {
        Text(
            text = stringResource(R.string.audio_section_body),
            style = MaterialTheme.typography.bodySmall,
            color = NexoraTextSecondary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        EnumRow(
            label = stringResource(R.string.audio_backend_label),
            current = draft.backend.label,
            options = com.nexora.android.runtime.audio.AudioBackend.entries.map { it.label },
            onSelect = { idx ->
                draft = draft.copy(
                    backend = com.nexora.android.runtime.audio.AudioBackend.entries[idx],
                )
            },
        )
        Spacer(modifier = Modifier.height(6.dp))
        EnumRow(
            label = stringResource(R.string.audio_rate_label),
            current = draft.sampleRate.label,
            options = com.nexora.android.runtime.audio.AudioSampleRate.entries.map { it.label },
            onSelect = { idx ->
                draft = draft.copy(
                    sampleRate = com.nexora.android.runtime.audio.AudioSampleRate.entries[idx],
                )
            },
        )
        Spacer(modifier = Modifier.height(6.dp))
        EnumRow(
            label = stringResource(R.string.audio_buffer_label),
            current = draft.bufferFrames.label,
            options = com.nexora.android.runtime.audio.AudioBufferFrames.entries.map { it.label },
            onSelect = { idx ->
                draft = draft.copy(
                    bufferFrames = com.nexora.android.runtime.audio.AudioBufferFrames.entries[idx],
                )
            },
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onSave(draft) },
                modifier = Modifier.weight(1f).height(42.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NexoraAccent,
                    contentColor = NexoraBackground,
                ),
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text("Guardar audio", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f).height(42.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text("Restablecer", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun MessageCard(message: String, isError: Boolean, onDismiss: () -> Unit) {
    NexoraCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) NexoraWarning else NexoraTextPrimary,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.size(8.dp))
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                Text("OK", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}