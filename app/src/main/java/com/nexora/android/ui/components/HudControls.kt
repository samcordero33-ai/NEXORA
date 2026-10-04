package com.nexora.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nexora.android.control.model.ControlAction
import com.nexora.android.control.model.ControlKind
import com.nexora.android.control.model.DPAD_DIRECTIONS
import com.nexora.android.control.model.HudControl
import com.nexora.android.engine.input.InputEvent
import com.nexora.android.engine.input.KeyStroke
import com.nexora.android.engine.input.Vec2
import com.nexora.android.engine.input.VirtualKey
import com.nexora.android.ui.theme.NexoraAccent
import com.nexora.android.ui.theme.NexoraBackground
import com.nexora.android.ui.theme.NexoraBorder
import com.nexora.android.ui.theme.NexoraSurfaceElevated
import com.nexora.android.ui.theme.NexoraTextDisabled
import com.nexora.android.ui.theme.NexoraTextPrimary

/**
 * Cuerpo de un botón del HUD, según el tipo de control que sea.
 *
 * @param enabled `false` en el editor: el botón se ve, pero no reacciona, porque
 *   ahí el dedo sirve para moverlo.
 * @param isLatched Estado del interruptor ([ControlKind.TOGGLE]).
 */
@Composable
fun HudControlBody(
    control: HudControl,
    isLatched: Boolean,
    onLatchChange: (Boolean) -> Unit,
    onEvent: (InputEvent) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val action = control.action

    when (action.kind) {
        ControlKind.TAP, ControlKind.TOGGLE -> {
            val stroke = action.stroke ?: return
            val isToggle = action.kind == ControlKind.TOGGLE
            HudKeyButton(
                label = action.label,
                enabled = enabled,
                isLatched = isToggle && isLatched,
                onPress = {
                    if (isToggle) {
                        onLatchChange(!isLatched)
                        onEvent(InputEvent.KeyAction(stroke, !isLatched))
                    } else {
                        onEvent(InputEvent.KeyAction(stroke, true))
                    }
                },
                onRelease = {
                    if (!isToggle) onEvent(InputEvent.KeyAction(stroke, false))
                },
                modifier = modifier,
            )
        }

        ControlKind.DPAD -> HudDpad(
            enabled = enabled,
            onDirection = { key, pressed ->
                onEvent(InputEvent.KeyAction(KeyStroke(key), pressed))
            },
            modifier = modifier,
        )

        ControlKind.JOYSTICK -> HudJoystick(
            enabled = enabled,
            onPan = { delta -> onEvent(InputEvent.CameraPan(delta)) },
            modifier = modifier,
        )
    }
}

/**
 * Botón circular del HUD.
 *
 * La pulsación es de verdad, no un clic: se emite al tocar y se libera al
 * levantar el dedo. Es lo que hace falta para mantener pulsada una tecla
 * (desplazar el mapa con las flechas, por ejemplo), que un `clickable` no puede
 * expresar.
 */
@Composable
fun HudKeyButton(
    label: String,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLatched: Boolean = false,
) {
    var pressed by remember { mutableStateOf(false) }
    val active = pressed || isLatched

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (enabled) {
                    Modifier.pointerInput(label) { watchPress(onPress, onRelease) }
                } else {
                    Modifier
                },
            )
            .background(
                color = if (active) NexoraAccent else NexoraSurfaceElevated,
                shape = CircleShape,
            )
            .border(
                width = 2.dp,
                color = if (active) NexoraAccent else NexoraBorder,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) NexoraBackground else NexoraTextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/**
 * Bucle de pulsación: avisa al tocar y al soltar.
 *
 * Es el bucle de bajo nivel de `pointerInput` en vez de `detectTapGestures`
 * porque la liberación tiene que llegar siempre, también cuando el dedo sale del
 * botón o el sistema cancela el gesto: una tecla que se queda bajada dejaría el
 * juego con el modificador pegado.
 */
private suspend fun PointerInputScope.watchPress(
    onPress: () -> Unit,
    onRelease: () -> Unit,
) {
    var isDown = false
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            for (change in event.changes) {
                if (change.pressed && !isDown) {
                    isDown = true
                    onPress()
                } else if (!change.pressed && isDown) {
                    isDown = false
                    onRelease()
                }
            }
        }
    }
}

/** Cruz de cuatro direcciones: cada flecha es una tecla que se mantiene. */
@Composable
fun HudDpad(
    onDirection: (VirtualKey, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxSize()) {
        DpadRow(
            leading = null,
            center = DPAD_DIRECTIONS[0],
            trailing = null,
            enabled = enabled,
            onDirection = onDirection,
        )
        DpadRow(
            leading = DPAD_DIRECTIONS[1],
            center = null,
            trailing = DPAD_DIRECTIONS[2],
            enabled = enabled,
            onDirection = onDirection,
        )
        DpadRow(
            leading = null,
            center = DPAD_DIRECTIONS[3],
            trailing = null,
            enabled = enabled,
            onDirection = onDirection,
        )
    }
}

@Composable
private fun ColumnScope.DpadRow(
    leading: VirtualKey?,
    center: VirtualKey?,
    trailing: VirtualKey?,
    enabled: Boolean,
    onDirection: (VirtualKey, Boolean) -> Unit,
) {
    Row(modifier = Modifier.weight(1f)) {
        DpadCell(key = leading, enabled = enabled, onDirection = onDirection)
        if (center == null) {
            // Centro de la cruz: solo marca el sitio, no hace nada.
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Outlined.SportsEsports,
                    contentDescription = null,
                    tint = NexoraTextDisabled,
                    modifier = Modifier.fillMaxSize(0.45f),
                )
            }
        } else {
            DpadCell(key = center, enabled = enabled, onDirection = onDirection)
        }
        DpadCell(key = trailing, enabled = enabled, onDirection = onDirection)
    }
}

@Composable
private fun RowScope.DpadCell(
    key: VirtualKey?,
    enabled: Boolean,
    onDirection: (VirtualKey, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.weight(1f)) {
        if (key == null) return@Box
        HudKeyButton(
            label = key.label,
            enabled = enabled,
            onPress = { onDirection(key, true) },
            onRelease = { onDirection(key, false) },
            modifier = Modifier.padding(2.dp),
        )
    }
}

/**
 * Palanca analógica opcional.
 *
 * Se dibuja con `Canvas` porque el mando es un círculo con un mando interior que
 * sigue al dedo: en píxeles, que es la unidad en la que llegan los gestos.
 */
@Composable
fun HudJoystick(
    onPan: (Vec2) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var knob by remember { mutableStateOf(Vec2.ZERO) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (enabled) {
                    Modifier.pointerInput(JOYSTICK_KEY) {
                        detectDragGestures(
                            onDragStart = { knob = Vec2.ZERO },
                            onDragEnd = { knob = Vec2.ZERO },
                            onDragCancel = { knob = Vec2.ZERO },
                            onDrag = { _, drag ->
                                val radius = minOf(size.width, size.height) / 2f
                                knob = clampUnit(knob + Vec2(drag.x / radius, drag.y / radius))
                                onPan(Vec2(drag.x, drag.y))
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        val radius = minOf(size.width, size.height) / 2f
        drawCircle(
            color = NexoraSurfaceElevated.copy(alpha = 0.55f),
            radius = radius,
            center = center,
        )
        drawCircle(
            color = NexoraBorder,
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )
        drawCircle(
            color = NexoraAccent,
            radius = radius * KNOB_RATIO,
            center = Offset(
                x = center.x + knob.x * radius * KNOB_TRAVEL,
                y = center.y + knob.y * radius * KNOB_TRAVEL,
            ),
        )
    }
}

private const val JOYSTICK_KEY = "hud_joystick"
private const val KNOB_RATIO = 0.32f
private const val KNOB_TRAVEL = 0.6f

private fun clampUnit(value: Vec2): Vec2 = Vec2(
    x = value.x.coerceIn(-1f, 1f),
    y = value.y.coerceIn(-1f, 1f),
)

/** Fila de acciones del editor: sirve para elegir el botón que se va a ajustar. */
@Composable
fun ControlActionChips(
    actions: List<ControlAction>,
    selected: ControlAction?,
    onSelect: (ControlAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        actions.forEach { action ->
            ControlActionChip(
                label = action.label,
                isSelected = action == selected,
                onClick = { onSelect(action) },
            )
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.size(8.dp),
            )
        }
    }
}

@Composable
private fun ControlActionChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (isSelected) NexoraBackground else NexoraTextPrimary,
        modifier = modifier
            .background(
                color = if (isSelected) NexoraAccent else NexoraSurfaceElevated,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = if (isSelected) NexoraAccent else NexoraBorder,
                shape = shape,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
    )
}
