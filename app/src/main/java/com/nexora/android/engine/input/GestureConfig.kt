package com.nexora.android.engine.input

/**
 * Umbrales del motor de gestos.
 *
 * Están separados del intérprete porque son los que se calibran en un
 * dispositivo real: un panel grande necesita más tolerancia al arrastre que uno
 * pequeño, y hay juegos en los que el recuadro de selección estorba (por ejemplo
 * los que ya traen su propia selección táctil).
 */
data class GestureConfig(

    /**
     * Desplazamiento mínimo, en píxeles, para que un dedo deje de considerarse
     * un toque y pase a ser un arrastre. Por debajo de este valor el dedo
     * "tiembla" y el gesto sigue siendo un toque.
     */
    val touchSlopPx: Float = 14f,

    /** Tiempo que un dedo quieto aguanta antes de convertirse en clic derecho. */
    val longPressMs: Long = 420L,

    /** Duración máxima de un toque para que cuente como clic (no como arrastre). */
    val tapMaxMs: Long = 400L,

    /**
     * Si el arrastre con un dedo dibuja un **recuadro de selección**.
     *
     * Es el gesto clásico de los RTS: arrastrar marca varias unidades de golpe.
     * Desactivado, el arrastre con un dedo solo mueve el cursor.
     */
    val dragBoxEnabled: Boolean = true,

    /** Separación que deben cambiar dos dedos, en píxeles, para empezar un zoom. */
    val zoomSlopPx: Float = 18f,

    /** Acumulación de arrastre con dos dedos, en píxeles, antes de emitir paneo. */
    val panSlopPx: Float = 16f,

    /** Multiplicador del zoom. `1f` es fidelidad al gesto del dedo. */
    val zoomSensitivity: Float = 1f,

    /** Multiplicador del paneo. */
    val panSensitivity: Float = 1f,
)
