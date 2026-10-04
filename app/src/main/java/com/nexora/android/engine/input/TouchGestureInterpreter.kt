package com.nexora.android.engine.input

/** Dos dedos bastan para los gestos de NEXORA; el tercero se ignora. */
private const val MAX_TRACKED_POINTERS = 2

/** Estado del gesto multitáctil en curso. */
private enum class MultiTouchMode {
    NONE,
    ZOOM,
    PAN,
}

/**
 * Traduce los eventos táctiles de la pantalla a eventos de ratón y teclado de
 * escritorio ([InputEvent]).
 *
 * Es una máquina de estados **pura**: recibe `down`, `move` y `up` de cada
 * puntero (con su posición en píxeles y su marca de tiempo) y devuelve la lista
 * de eventos que eso produce, normalmente vacía. No importa Android ni Compose,
 * así que se prueba en JVM.
 *
 * | Gesto | Evento |
 * |---|---|
 * | Toque corto con un dedo | [InputEvent.CursorMove] + [InputEvent.PrimaryClick] |
 * | Arrastre con un dedo | [InputEvent.SelectionRect] al soltar (recuadro de selección) |
 * | Pulsación larga con un dedo | [InputEvent.SecondaryClick] |
 * | Toque con dos dedos | [InputEvent.SecondaryClick] en el centro |
 * | Pellizcar con dos dedos | [InputEvent.CameraZoom] incrementales |
 * | Dos dedos en paralelo | [InputEvent.CameraPan] |
 *
 * El recuadro en curso se expone en [selectionBox] para que la UI lo dibuje en
 * vivo; el evento [InputEvent.SelectionRect] solo se emite al soltar.
 */
class TouchGestureInterpreter(
    private val config: GestureConfig = GestureConfig(),
) {

    private val pointers = LinkedHashMap<Long, TrackedPointer>()

    private var selectionAnchor: Vec2? = null
    private var multiTouchMode = MultiTouchMode.NONE
    private var pinchDistance = 0f
    private var pinchCenter = Vec2.ZERO
    private var panAccumulator = Vec2.ZERO
    private var longPressDelivered = false
    private var releaseSuppressed = false

    /** Recuadro de selección en curso; `null` si no se está dibujando ninguno. */
    var selectionBox: InputEvent.SelectionRect? = null
        private set

    /** Última posición conocida del cursor virtual. */
    var cursor: Vec2? = null
        private set

    /** Dedos que el intérprete está siguiendo ahora mismo. */
    val trackedPointerCount: Int
        get() = pointers.size

    /**
     * Un dedo toca la pantalla.
     *
     * El primero mueve el cursor y ancla un posible recuadro; el segundo
     * abandona ese recuadro y prepara el clic derecho, el zoom o el paneo.
     */
    fun onPointerDown(id: Long, position: Vec2, nowMs: Long): List<InputEvent> {
        if (pointers.size >= MAX_TRACKED_POINTERS) return emptyList()
        pointers[id] = TrackedPointer(position, nowMs)

        return when (pointers.size) {
            1 -> {
                longPressDelivered = false
                releaseSuppressed = false
                selectionAnchor = position
                selectionBox = null
                multiTouchMode = MultiTouchMode.NONE
                cursor = position
                listOf(InputEvent.CursorMove(position))
            }

            2 -> {
                // Dos dedos cancelan el recuadro: nunca es selección múltiple.
                selectionAnchor = null
                selectionBox = null
                multiTouchMode = MultiTouchMode.NONE
                // El gesto de dos dedos arranca aquí: se reancla la referencia de
                // ambos para medir solo lo que ocurra a partir de este momento.
                pointers.values.forEach { it.startPosition = it.position }
                pinchDistance = pointerDistance()
                pinchCenter = pointerCentroid()
                panAccumulator = Vec2.ZERO
                emptyList()
            }

            else -> emptyList()
        }
    }

    /** Un dedo se mueve por la pantalla. */
    fun onPointerMove(id: Long, position: Vec2, nowMs: Long): List<InputEvent> {
        val pointer = pointers[id] ?: return emptyList()
        pointer.position = position
        val travel = (position - pointer.startPosition).length()
        if (travel > config.touchSlopPx) pointer.movedBeyondSlop = true

        val events = mutableListOf<InputEvent>()

        when (pointers.size) {
            1 -> {
                if (!longPressDelivered &&
                    !pointer.movedBeyondSlop &&
                    nowMs - pointer.startTimeMs >= config.longPressMs
                ) {
                    longPressDelivered = true
                    releaseSuppressed = true
                    selectionAnchor = null
                    selectionBox = null
                    cursor = position
                    events += InputEvent.CursorMove(position)
                    events += InputEvent.SecondaryClick(position)
                    return events
                }

                cursor = position
                val anchor = selectionAnchor
                if (pointer.movedBeyondSlop && anchor != null) {
                    if (config.dragBoxEnabled) {
                        selectionBox = InputEvent.SelectionRect(anchor, position)
                    } else {
                        events += InputEvent.CursorMove(position)
                    }
                }
            }

            2 -> {
                val distance = pointerDistance()
                val center = pointerCentroid()
                panAccumulator += center - pinchCenter

                if (multiTouchMode == MultiTouchMode.NONE) {
                    // Un movimiento de dos dedos se descompone en la parte que
                    // arrastra (los dos van a la vez) y la parte que separa (se
                    // abren o se cierran). Gana la que antes supere su umbral y,
                    // si empatan, se interpreta como zoom: un pellizco real
                    // siempre separa, mientras que un paneo apenas lo hace.
                    val translation = pointerTranslation()
                    val separation = pointerSeparation()
                    val panReached = panAccumulator.length() >= config.panSlopPx
                    val zoomReached = separation >= config.zoomSlopPx

                    multiTouchMode = when {
                        zoomReached && (!panReached || separation >= translation.length()) ->
                            MultiTouchMode.ZOOM
                        panReached -> MultiTouchMode.PAN
                        else -> MultiTouchMode.NONE
                    }
                }

                when (multiTouchMode) {
                    MultiTouchMode.ZOOM -> {
                        val ratio = if (pinchDistance > 0f) distance / pinchDistance else 1f
                        val factor = 1f + (ratio - 1f) * config.zoomSensitivity
                        events += InputEvent.CameraZoom(center, factor)
                        markBothPointersMoved()
                    }

                    MultiTouchMode.PAN -> {
                        if (panAccumulator.length() >= config.panSlopPx) {
                            events += InputEvent.CameraPan(panAccumulator * config.panSensitivity)
                            panAccumulator = Vec2.ZERO
                            markBothPointersMoved()
                        }
                    }

                    MultiTouchMode.NONE -> Unit
                }

                pinchDistance = distance
                pinchCenter = center
            }
        }

        return events
    }

    /** Un dedo deja la pantalla. Aquí se cierran los gestos. */
    fun onPointerUp(id: Long, position: Vec2, nowMs: Long): List<InputEvent> {
        val pointer = pointers[id] ?: return emptyList()
        pointer.position = position

        val events = mutableListOf<InputEvent>()

        when (pointers.size) {
            2 -> {
                val other = pointers.values.firstOrNull { it !== pointer }
                val isTwoFingerTap = multiTouchMode == MultiTouchMode.NONE &&
                    other != null &&
                    !pointer.movedBeyondSlop &&
                    !other.movedBeyondSlop &&
                    nowMs - pointer.startTimeMs <= config.tapMaxMs &&
                    nowMs - other.startTimeMs <= config.tapMaxMs

                if (isTwoFingerTap) {
                    val center = pointerCentroid()
                    cursor = center
                    events += InputEvent.CursorMove(center)
                    events += InputEvent.SecondaryClick(center)
                    releaseSuppressed = true
                }
            }

            1 -> {
                if (!longPressDelivered && !releaseSuppressed) {
                    val box = selectionBox
                    if (box != null) {
                        events += InputEvent.SelectionRect(box.start, position)
                    } else {
                        cursor = position
                        events += InputEvent.CursorMove(position)
                        events += InputEvent.PrimaryClick(position)
                    }
                }
            }
        }

        pointers.remove(id)

        if (pointers.isEmpty()) {
            clearTransientState()
        } else {
            // Queda un dedo en pantalla: se reancla para que al levantarlo no
            // dispare un clic que nadie pidió. El gesto ya era de dos dedos, así
            // que ese último dedo no puede cerrar nada por su cuenta.
            val remaining = pointers.values.first()
            remaining.startPosition = remaining.position
            remaining.startTimeMs = nowMs
            remaining.movedBeyondSlop = true
            clearTransientState()
            selectionAnchor = null
            releaseSuppressed = true
        }

        return events
    }

    /** El sistema cancela el gesto (por ejemplo al girar la pantalla). */
    fun onCancel() {
        pointers.clear()
        clearTransientState()
        selectionBox = null
    }

    /** Olvida el cursor y cualquier gesto a medias, sin emitir eventos. */
    fun reset() {
        onCancel()
        cursor = null
    }

    private fun clearTransientState() {
        multiTouchMode = MultiTouchMode.NONE
        pinchDistance = 0f
        pinchCenter = Vec2.ZERO
        panAccumulator = Vec2.ZERO
        longPressDelivered = false
        releaseSuppressed = false
        selectionBox = null
    }

    private fun markBothPointersMoved() {
        pointers.values.forEach { it.movedBeyondSlop = true }
    }

    private fun pointerDistance(): Float {
        val values = pointers.values.toList()
        if (values.size < 2) return 0f
        return (values[0].position - values[1].position).length()
    }

    /**
     * Desplazamiento medio de los dedos desde que empezó el gesto.
     *
     * Es la parte del movimiento que arrastra la cámara: si los dos dedos van a
     * la vez, este vector es exactamente ese arrastre.
     */
    private fun pointerTranslation(): Vec2 {
        if (pointers.isEmpty()) return Vec2.ZERO
        var sum = Vec2.ZERO
        pointers.values.forEach { sum += it.position - it.startPosition }
        return sum / pointers.size.toFloat()
    }

    /**
     * Cuánto se separan los dedos por encima del arrastre medio.
     *
     * Es la parte del movimiento que hace zoom: si los dedos se abren o se
     * cierran, este valor crece aunque el centro apenas se mueva.
     */
    private fun pointerSeparation(): Float {
        if (pointers.isEmpty()) return 0f
        val translation = pointerTranslation()
        var sum = 0f
        pointers.values.forEach { sum += (it.position - it.startPosition - translation).length() }
        return sum / pointers.size
    }

    private fun pointerCentroid(): Vec2 {
        if (pointers.isEmpty()) return Vec2.ZERO
        var x = 0f
        var y = 0f
        pointers.values.forEach { pointer ->
            x += pointer.position.x
            y += pointer.position.y
        }
        val count = pointers.size
        return Vec2(x / count, y / count)
    }

    private class TrackedPointer(
        initialPosition: Vec2,
        initialTimeMs: Long,
    ) {
        /** Posición de referencia del gesto: se reancla al cambiar de modo. */
        var startPosition: Vec2 = initialPosition
        var startTimeMs: Long = initialTimeMs
        var position: Vec2 = initialPosition
        var movedBeyondSlop: Boolean = false
    }
}
