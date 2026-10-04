package com.nexora.android.engine.input

import java.util.Locale

/**
 * Evento de entrada producido por la capa táctil de NEXORA.
 *
 * Es el **contrato** entre el motor de gestos y el runtime: aquí se describe
 * qué ha hecho el dedo en términos de ratón y teclado de escritorio (mover el
 * cursor, clic izquierdo, clic derecho, recuadro de selección, zoom, paneo o
 * una pulsación de tecla). Ninguna de estas clases ejecuta nada: son datos que
 * el runtime consumirá cuando se integre.
 *
 * Kotlin puro: no dependen de Android ni de Compose, así que el intérprete se
 * prueba en JVM.
 */
sealed interface InputEvent {

    /** El cursor se mueve a `position`. */
    data class CursorMove(val position: Vec2) : InputEvent {
        override fun describe(): String = "Cursor → ${position.shortText()}"
    }

    /** Clic izquierdo: seleccionar, confirmar, mover la vista. */
    data class PrimaryClick(val position: Vec2) : InputEvent {
        override fun describe(): String = "Clic izquierdo en ${position.shortText()}"
    }

    /** Clic derecho: en un RTS es la orden de mover o atacar. */
    data class SecondaryClick(val position: Vec2) : InputEvent {
        override fun describe(): String = "Clic derecho en ${position.shortText()}"
    }

    /**
     * Recuadro de selección ("drag-box") terminado.
     *
     * `start` y `end` son esquinas opuestas sin normalizar: el consumidor decide
     * si necesita ordenarlas.
     */
    data class SelectionRect(val start: Vec2, val end: Vec2) : InputEvent {
        override fun describe(): String =
            "Recuadro de selección ${start.shortText()} → ${end.shortText()}"
    }

    /** Zoom de cámara por pellizco. `factor` > 1 acerca, < 1 aleja. */
    data class CameraZoom(val focus: Vec2, val factor: Float) : InputEvent {
        override fun describe(): String =
            String.format(Locale.US, "Zoom ×%.2f en %s", factor, focus.shortText())
    }

    /** Desplazamiento de la cámara, en píxeles. */
    data class CameraPan(val delta: Vec2) : InputEvent {
        override fun describe(): String = String.format(
            Locale.US,
            "Mover cámara (%.0f, %.0f)",
            delta.x,
            delta.y,
        )
    }

    /** Pulsación (`isPressed`) o liberación de una tecla. */
    data class KeyAction(val stroke: KeyStroke, val isPressed: Boolean) : InputEvent {
        override fun describe(): String =
            "${stroke.label} ${if (isPressed) "pulsada" else "liberada"}"
    }

    /** Descripción corta en español, para el registro de gestos de la UI. */
    fun describe(): String
}

private fun Vec2.shortText(): String = String.format(Locale.US, "(%.0f, %.0f)", x, y)
