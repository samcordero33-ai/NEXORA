package com.nexora.android.runtime.input

import com.nexora.android.engine.input.Vec2
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Traduce píxeles de la pantalla táctil a coordenadas del escritorio virtual
 * del juego.
 *
 * El HUD trabaja en píxeles del dispositivo (por ejemplo 1080 × 2400); Wine
 * trabaja en un escritorio de, digamos, 1280 × 720, y el kernel de entrada en
 * un rango absoluto de 0 a 32767. Sin esta conversión, un toque en el centro
 * de la pantalla no cae en el centro del juego.
 *
 * Todo el cálculo es puro y **con recorte**: un dedo que sale del área del
 * juego se sujeta al borde en vez de generar un valor fuera de rango, que el
 * kernel rechazaría.
 *
 * @property surfaceWidth Ancho de la superficie de juego, en píxeles.
 * @property surfaceHeight Alto de la superficie de juego, en píxeles.
 * @property desktopWidth Ancho del escritorio virtual de Wine.
 * @property desktopHeight Alto del escritorio virtual de Wine.
 * @property absMax Máximo del eje absoluto (`ABS_MAX_VALUE` por defecto).
 */
data class ViewportMapping(
    val surfaceWidth: Float,
    val surfaceHeight: Float,
    val desktopWidth: Int,
    val desktopHeight: Int,
    val absMax: Int = Uinput.ABS_MAX_VALUE,
) {

    /** `true` si hay una superficie válida donde mapear. */
    val isValid: Boolean
        get() = surfaceWidth > 0f && surfaceHeight > 0f && desktopWidth > 0 && desktopHeight > 0

    /** Píxeles de pantalla → píxeles del escritorio virtual, con recorte. */
    fun toDesktop(point: Vec2): Vec2 {
        if (!isValid) return Vec2.ZERO
        val x = (point.x / surfaceWidth * desktopWidth).coerceIn(0f, desktopWidth.toFloat())
        val y = (point.y / surfaceHeight * desktopHeight).coerceIn(0f, desktopHeight.toFloat())
        return Vec2(x, y)
    }

    /** Píxeles de pantalla → eje absoluto `evdev` (0..absMax). */
    fun toAbsolute(point: Vec2): Pair<Int, Int> {
        if (!isValid) return 0 to 0
        val x = (point.x / surfaceWidth * absMax).roundToInt().coerceIn(0, absMax)
        val y = (point.y / surfaceHeight * absMax).roundToInt().coerceIn(0, absMax)
        return x to y
    }

    /**
     * Desplazamiento en píxeles de pantalla → delta absoluto, para el paneo.
     *
     * Un `CameraPan` de 100 px en una pantalla de 1080 px es un 9,26 % del
     * eje: eso es lo que se suma al absoluto anterior.
     */
    fun deltaToAbsolute(delta: Vec2): Pair<Int, Int> {
        if (!isValid) return 0 to 0
        val dx = (delta.x / surfaceWidth * absMax).roundToInt()
        val dy = (delta.y / surfaceHeight * absMax).roundToInt()
        return dx to dy
    }

    /**
     * Pellizco → muescas de rueda.
     *
     * `factor > 1` (acercar) equivale a girar la rueda hacia arriba (+1) por
     * cada 10 % de zoom; se limita a ±3 muescas por gesto para no disparar
     * quince eventos de golpe cuando el usuario pellizca rápido.
     */
    fun zoomToWheelNotches(factor: Float): Int {
        if (abs(factor - 1f) < 0.05f) return 0
        val notches = ((factor - 1f) * 10f).roundToInt()
        return notches.coerceIn(-3, 3)
    }
}
