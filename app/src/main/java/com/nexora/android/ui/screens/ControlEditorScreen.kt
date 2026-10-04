package com.nexora.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexora.android.R
import com.nexora.android.control.model.ControlAction
import com.nexora.android.control.model.ControlLayout
import com.nexora.android.control.model.HudControl
import com.nexora.android.ui.components.ControlActionChips
import com.nexora.android.ui.components.TouchControlsOverlay
import com.nexora.android.ui.theme.NexoraAccent
import com.nexora.android.ui.theme.NexoraBackground
import com.nexora.android.ui.theme.NexoraBorder
import com.nexora.android.ui.theme.NexoraSurface
import com.nexora.android.ui.theme.NexoraTextPrimary
import com.nexora.android.ui.theme.NexoraTextSecondary
import com.nexora.android.ui.theme.NexoraTheme

/** Gestos que guarda el registro del modo prueba. */
private const val MAX_EVENT_LOG = 6

/**
 * Editor de la disposición táctil de un juego.
 *
 * Es la misma pantalla para dos cosas, porque comparten el dibujo:
 * - **Editar**: los botones se arrastran y la capa no interpreta gestos.
 * - **Probar**: la capa interpreta gestos y el registro muestra lo que produce.
 *
 * Todo lo que se toca aquí se guarda en Room al pulsar "Guardar" (y también al
 * salir), y la disposición se queda asociada al juego.
 *
 * @param onMoveControl Desplazamiento normalizado del botón arrastrado.
 * @param onResizeControl Nuevo tamaño del botón seleccionado.
 * @param onToggleVisibility Muestra u oculta un botón.
 */
@Composable
fun ControlEditorScreen(
    gameName: String,
    layout: ControlLayout,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onOpacityChange: (Float) -> Unit,
    onDragBoxChange: (Boolean) -> Unit,
    onMoveControl: (ControlAction, Float, Float) -> Unit,
    onResizeControl: (ControlAction, Float) -> Unit,
    onToggleVisibility: (ControlAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isTesting by rememberSaveable { mutableStateOf(false) }
    var selectedAction by rememberSaveable { mutableStateOf<ControlAction?>(null) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val eventLog = remember { mutableStateListOf<String>() }

    val selected = selectedAction?.let { layout.controlFor(it) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NexoraBackground)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        EditorTopBar(gameName = gameName, onBack = onBack)

        Spacer(modifier = Modifier.height(12.dp))

        // Superficie: el HUD se dibuja exactamente igual que sobre el juego.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(color = NexoraSurface, shape = RoundedCornerShape(18.dp))
                .border(width = 1.dp, color = NexoraBorder, shape = RoundedCornerShape(18.dp)),
        ) {
            TouchControlsOverlay(
                layout = layout,
                gesturesEnabled = isTesting,
                editable = !isTesting,
                selectedAction = selectedAction,
                onSelectAction = { selectedAction = it },
                onMoveControl = onMoveControl,
                onEvent = { event -> eventLog.record(event.describe()) },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            ModeRow(
                isTesting = isTesting,
                onModeChange = { testing ->
                    isTesting = testing
                    if (testing) eventLog.clear()
                },
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(
                    if (isTesting) {
                        R.string.controls_editor_hint_test
                    } else {
                        R.string.controls_editor_hint_edit
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = NexoraTextSecondary,
            )

            if (isTesting) {
                Spacer(modifier = Modifier.height(12.dp))
                EventLogCard(log = eventLog)
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.controls_editor_actions),
                    style = MaterialTheme.typography.labelMedium,
                    color = NexoraTextSecondary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    ControlActionChips(
                        actions = ControlAction.ALL,
                        selected = selectedAction,
                        onSelect = { action ->
                            selectedAction = if (selectedAction == action) null else action
                        },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                SelectedControlPanel(
                    control = selected,
                    onResize = { size -> selectedAction?.let { onResizeControl(it, size) } },
                    onToggleVisibility = { selectedAction?.let(onToggleVisibility) },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(
                    R.string.controls_label_opacity,
                    (layout.overlayOpacity * 100).toInt(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = NexoraTextSecondary,
            )
            Slider(
                value = layout.overlayOpacity,
                onValueChange = onOpacityChange,
                valueRange = 0.2f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = NexoraAccent,
                    activeTrackColor = NexoraAccent,
                ),
            )

            SettingRow(
                label = stringResource(R.string.controls_label_dragbox),
                checked = layout.dragBoxEnabled,
                onCheckedChange = onDragBoxChange,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NexoraAccent,
                        contentColor = NexoraBackground,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.controls_editor_save),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                OutlinedButton(
                    onClick = { confirmReset = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.controls_editor_reset),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = {
                Text(
                    text = stringResource(R.string.controls_editor_reset_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = NexoraTextPrimary,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.controls_editor_reset_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NexoraTextSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        selectedAction = null
                        onReset()
                    },
                ) {
                    Text(text = stringResource(R.string.controls_editor_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(text = stringResource(R.string.profile_cancel))
                }
            },
        )
    }
}

@Suppress("DEPRECATION")
@Composable
private fun EditorTopBar(
    gameName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.detail_action_back),
                tint = NexoraTextPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = stringResource(R.string.controls_editor_title),
            style = MaterialTheme.typography.titleMedium,
            color = NexoraTextPrimary,
        )
        Spacer(modifier = Modifier.size(10.dp))
        Text(
            text = gameName,
            style = MaterialTheme.typography.bodySmall,
            color = NexoraTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
    }
}

/** Cambio entre editar la disposición y probar los gestos. */
@Composable
private fun ModeRow(
    isTesting: Boolean,
    onModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        ModeButton(
            label = stringResource(R.string.controls_mode_edit),
            isSelected = !isTesting,
            onClick = { onModeChange(false) },
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(10.dp))
        ModeButton(
            label = stringResource(R.string.controls_mode_test),
            isSelected = isTesting,
            onClick = { onModeChange(true) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModeButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) NexoraAccent else NexoraSurface,
            contentColor = if (isSelected) NexoraBackground else NexoraTextSecondary,
        ),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

/** Ajustes del botón seleccionado: tamaño, visibilidad y posición. */
@Composable
private fun SelectedControlPanel(
    control: HudControl?,
    onResize: (Float) -> Unit,
    onToggleVisibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(color = NexoraSurface, shape = RoundedCornerShape(14.dp))
            .border(width = 1.dp, color = NexoraBorder, shape = RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Text(
            text = stringResource(
                R.string.controls_editor_selected,
                control?.action?.label ?: stringResource(R.string.controls_editor_none),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = NexoraTextPrimary,
        )

        if (control == null) return@Column

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(
                R.string.controls_editor_position,
                (control.centerX * 100).toInt(),
                (control.centerY * 100).toInt(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = NexoraTextSecondary,
        )

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.controls_editor_size),
            style = MaterialTheme.typography.bodySmall,
            color = NexoraTextSecondary,
        )
        Slider(
            value = control.size,
            onValueChange = onResize,
            valueRange = ControlLayout.MIN_SIZE..ControlLayout.MAX_SIZE,
            colors = SliderDefaults.colors(
                thumbColor = NexoraAccent,
                activeTrackColor = NexoraAccent,
            ),
        )

        SettingRow(
            label = stringResource(R.string.controls_editor_visible),
            checked = control.visible,
            onCheckedChange = { onToggleVisibility() },
        )
    }
}

@Composable
private fun SettingRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = NexoraTextSecondary,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NexoraBackground,
                checkedTrackColor = NexoraAccent,
            ),
        )
    }
}

/** Registro de los últimos gestos reconocidos: la prueba de que el motor va. */
@Composable
private fun EventLogCard(
    log: SnapshotStateList<String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(color = NexoraSurface, shape = RoundedCornerShape(14.dp))
            .border(width = 1.dp, color = NexoraBorder, shape = RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Text(
            text = stringResource(R.string.controls_event_log),
            style = MaterialTheme.typography.labelMedium,
            color = NexoraTextSecondary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        if (log.isEmpty()) {
            Text(
                text = stringResource(R.string.controls_event_log_empty),
                style = MaterialTheme.typography.bodySmall,
                color = NexoraTextSecondary,
            )
        } else {
            log.forEach { entry ->
                Text(
                    text = "· $entry",
                    style = MaterialTheme.typography.bodySmall,
                    color = NexoraTextPrimary,
                )
            }
        }
    }
}

private fun SnapshotStateList<String>.record(entry: String) {
    add(0, entry)
    while (size > MAX_EVENT_LOG) removeAt(lastIndex)
}

@Preview(showBackground = true, widthDp = 420, heightDp = 900)
@Composable
private fun ControlEditorScreenPreview() {
    NexoraTheme {
        ControlEditorScreen(
            gameName = "Age of Empires II",
            layout = ControlLayout.defaults("preview"),
            onBack = {},
            onSave = {},
            onReset = {},
            onOpacityChange = {},
            onDragBoxChange = {},
            onMoveControl = { _, _, _ -> },
            onResizeControl = { _, _ -> },
            onToggleVisibility = {},
        )
    }
}
