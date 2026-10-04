package com.nexora.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.nexora.android.control.model.ControlAction
import com.nexora.android.control.model.ControlLayout
import com.nexora.android.engine.input.GestureConfig
import com.nexora.android.engine.input.InputEvent
import com.nexora.android.engine.input.TouchGestureInterpreter
import com.nexora.android.engine.input.Vec2
import com.nexora.android.ui.theme.NexoraAccent
import com.nexora.android.ui.theme.NexoraTextDisabled
import kotlin.math.abs

/**
 * Capa flotante de controles táctiles (HUD).
 *
 * Es la pieza que convierte la pantalla en un ratón y un teclado de escritorio:
 * interpreta los gestos y dibuja encima los botones que el usuario ha colocado.
 * No depende del runtime: sirve igual para **probar** los gestos que para
 * **editar** la disposición.
 *
 * Los gestos se escuchan en el lienzo, que es la capa inferior: los botones se
 * prueban antes (están dibujados encima) y los dedos que caen sobre un botón se
 * descartan del intérprete, así que pulsar "Ctrl + 1" no mueve además el cursor
 * ni hace clic en el mapa.
 *
 * @param layout Disposición del HUD (botones, transparencia y recuadro).
 * @param gesturesEnabled `true` en partida y en modo prueba; `false` mientras se
 *   edita, para que arrastrar mueva los botones en vez de jugar.
 * @param editable Habilita el arrastre de los botones.
 * @param selectedAction Botón seleccionado en el editor; se marca con borde.
 * @param onMoveControl Desplazamiento **normalizado** de un botón (fracción del
 *   ancho y del alto de la superficie).
 * @param onEvent Cada gesto o pulsación que produce la capa.
 */
@Composable
fun TouchControlsOverlay(
    layout: ControlLayout,
    modifier: Modifier = Modifier,
    gesturesEnabled: Boolean = true,
    editable: Boolean = false,
    selectedAction: ControlAction? = null,
    onSelectAction: (ControlAction?) -> Unit = {},
    onMoveControl: (ControlAction, Float, Float) -> Unit = { _, _, _ -> },
    onEvent: (InputEvent) -> Unit = {},
) {
    // El intérprete se recrea si cambia el ajuste del recuadro: sus umbrales
    // dependen de él y no merece la pena mutarlos en caliente.
    val interpreter = remember(layout.dragBoxEnabled) {
        TouchGestureInterpreter(GestureConfig(dragBoxEnabled = layout.dragBoxEnabled))
    }

    var selectionBox by remember { mutableStateOf<InputEvent.SelectionRect?>(null) }
    var cursor by remember { mutableStateOf<Vec2?>(null) }
    var latchedActions by remember { mutableStateOf(emptySet<ControlAction>()) }

    LaunchedEffect(gesturesEnabled) {
        if (!gesturesEnabled) {
            interpreter.reset()
            selectionBox = null
            cursor = null
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val baseSize = minOf(maxWidth, maxHeight)
        val density = LocalDensity.current
        val buttonRects = layout.visibleControls.map { control ->
            val buttonSize = baseSize * control.size
            val leftPx = with(density) {
                (maxWidth * control.centerX - buttonSize / 2f).toPx()
            }
            val topPx = with(density) {
                (maxHeight * control.centerY - buttonSize / 2f).toPx()
            }
            val sizePx = with(density) { buttonSize.toPx() }
            Rect(leftPx, topPx, leftPx + sizePx, topPx + sizePx)
        }

        val gestureModifier = if (gesturesEnabled) {
            Modifier.pointerInput(interpreter, buttonRects) {
                interpretGestures(interpreter, buttonRects, onEvent) { box, point ->
                    selectionBox = box
                    cursor = point
                }
            }
        } else {
            Modifier
        }

        // Recuadro de selección y cursor: se dibujan en los mismos píxeles en
        // los que llegan los gestos.
        Canvas(modifier = Modifier.fillMaxSize().then(gestureModifier)) {
            selectionBox?.let { rect ->
                val left = minOf(rect.start.x, rect.end.x)
                val top = minOf(rect.start.y, rect.end.y)
                val rectSize = Size(
                    width = abs(rect.end.x - rect.start.x),
                    height = abs(rect.end.y - rect.start.y),
                )
                drawRect(
                    color = NexoraAccent.copy(alpha = 0.14f),
                    topLeft = Offset(left, top),
                    size = rectSize,
                )
                drawRect(
                    color = NexoraAccent,
                    topLeft = Offset(left, top),
                    size = rectSize,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            cursor?.let { point ->
                val center = Offset(point.x, point.y)
                drawCircle(
                    color = NexoraAccent.copy(alpha = 0.22f),
                    radius = 14.dp.toPx(),
                    center = center,
                )
                drawCircle(color = NexoraAccent, radius = 5.dp.toPx(), center = center)
            }
        }

        layout.visibleControls.forEach { control ->
            val buttonSize = baseSize * control.size
            val isSelected = control.action == selectedAction
            Box(
                modifier = Modifier
                    .offset(
                        x = maxWidth * control.centerX - buttonSize / 2f,
                        y = maxHeight * control.centerY - buttonSize / 2f,
                    )
                    .size(buttonSize)
                    .alpha(layout.overlayOpacity)
                    .then(
                        if (editable) {
                            Modifier
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) NexoraAccent else NexoraTextDisabled,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .pointerInput(control.action) {
                                    detectDragGestures(
                                        onDragStart = { onSelectAction(control.action) },
                                        onDrag = { _, drag ->
                                            onMoveControl(
                                                control.action,
                                                drag.x / size.width,
                                                drag.y / size.height,
                                            )
                                        },
                                    )
                                }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                HudControlBody(
                    control = control,
                    enabled = !editable,
                    isLatched = control.action in latchedActions,
                    onLatchChange = { latched ->
                        latchedActions = if (latched) {
                            latchedActions + control.action
                        } else {
                            latchedActions - control.action
                        }
                    },
                    onEvent = onEvent,
                )
            }
        }
    }
}

/**
 * Bucle de gestos: reparte los punteros al intérprete y publica su estado
 * (recuadro en curso y cursor) para que el lienzo lo dibuje.
 *
 * Se usa el bucle de bajo nivel en vez de `detectTransformGestures` porque hay
 * que distinguir cinco gestos —toque, pulsación larga, recuadro, pellizco y
 * paneo— y eso solo lo puede decidir una máquina de estados.
 *
 * @param buttonRects Zonas ocupadas por los botones: un dedo que empieza en una
 *   de ellas pertenece al HUD, no al juego.
 */
private suspend fun PointerInputScope.interpretGestures(
    interpreter: TouchGestureInterpreter,
    buttonRects: List<Rect>,
    onEvent: (InputEvent) -> Unit,
    onStateChanged: (InputEvent.SelectionRect?, Vec2?) -> Unit,
) {
    val pressed = mutableSetOf<Long>()
    val onSurface = mutableSetOf<Long>()
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            for (change in event.changes) {
                val id = change.id.value
                val position = Vec2(change.position.x, change.position.y)
                val nowMs = change.uptimeMillis
                val isPressed = change.pressed

                val events = when {
                    isPressed && id !in pressed -> {
                        pressed += id
                        if (buttonRects.any { it.contains(change.position) }) {
                            emptyList()
                        } else {
                            onSurface += id
                            interpreter.onPointerDown(id, position, nowMs)
                        }
                    }

                    !isPressed && id in pressed -> {
                        pressed -= id
                        if (id !in onSurface) {
                            emptyList()
                        } else {
                            onSurface -= id
                            interpreter.onPointerUp(id, position, nowMs)
                        }
                    }

                    isPressed && id in onSurface -> {
                        interpreter.onPointerMove(id, position, nowMs)
                    }

                    else -> emptyList()
                }

                events.forEach(onEvent)
            }
            onStateChanged(interpreter.selectionBox, interpreter.cursor)
        }
    }
}
