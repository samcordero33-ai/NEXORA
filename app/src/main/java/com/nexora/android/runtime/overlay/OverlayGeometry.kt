package com.nexora.android.runtime.overlay

/**
 * Rectángulo ocupado por el HUD flotante, en píxeles de pantalla.
 *
 * Se usa para mover y sujetar la ventana del overlay: el `WindowManager` de
 * Android acepta posiciones fuera de pantalla sin protestar, y una ventana
 * arrastrada al borde negativo se vuelve irrecuperable para el usuario.
 */
data class HudBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    val right: Int get() = x + width
    val bottom: Int get() = y + height
}

/**
 * Tamaños del HUD.
 *
 * - **Compacto**: FPS y RAM. Es lo que se mira de reojo mientras se juega.
 * - **Completo**: añade CPU, temperaturas, pid y tiempo de sesión.
 *
 * Las medidas son en `dp`, no en píxeles: la conversión la hace el servicio
 * con la densidad real del dispositivo.
 */
enum class HudMode(
    val label: String,
    val widthDp: Int,
    val heightDp: Int,
) {
    COMPACT(label = "compacto", widthDp = 184, heightDp = 92),
    EXPANDED(label = "completo", widthDp = 248, heightDp = 236),
    ;

    /** Alterna entre los dos tamaños. */
    fun next(): HudMode = if (this == COMPACT) EXPANDED else COMPACT
}

/**
 * Aritmética de colocación del HUD.
 *
 * Es geometría pura sobre enteros: sin `Context`, sin `WindowManager`, sin
 * Android. Eso permite probarla en JVM y, sobre todo, garantiza que la ventana
 * nunca se queda fuera de la pantalla ni tapando la barra de estado.
 */
object OverlayGeometry {

    /** Margen por defecto respecto a los bordes, en píxeles. */
    const val DEFAULT_MARGIN_PX: Int = 12

    /**
     * Sujeta [x] para que el HUD quepa entero en el ancho de pantalla.
     *
     * Si el HUD es más ancho que la pantalla (pasa en pantallas pequeñas con
     * el modo completo), se ancla al margen izquierdo: es preferible que se
     * salga por la derecha a que se salga por la izquierda, donde no hay nada
     * que lo devuelva.
     */
    fun clampX(
        x: Int,
        width: Int,
        screenWidth: Int,
        margin: Int = DEFAULT_MARGIN_PX,
    ): Int {
        val max = screenWidth - width - margin
        if (max < margin) return margin
        return x.coerceIn(margin, max)
    }

    /** Sujeta [y] al alto de pantalla. Misma política que [clampX]. */
    fun clampY(
        y: Int,
        height: Int,
        screenHeight: Int,
        margin: Int = DEFAULT_MARGIN_PX,
    ): Int {
        val max = screenHeight - height - margin
        if (max < margin) return margin
        return y.coerceIn(margin, max)
    }

    /** Coloca el HUD en la posición deseada, ya sujeta a los bordes. */
    fun place(
        desiredX: Int,
        desiredY: Int,
        width: Int,
        height: Int,
        screenWidth: Int,
        screenHeight: Int,
        margin: Int = DEFAULT_MARGIN_PX,
    ): HudBounds = HudBounds(
        x = clampX(desiredX, width, screenWidth, margin),
        y = clampY(desiredY, height, screenHeight, margin),
        width = width,
        height = height,
    )

    /**
     * Aplica un desplazamiento de arrastre a [current].
     *
     * Los deltas llegan en `Float` porque vienen de un gesto, pero la ventana
     * se mueve en píxeles enteros: se trunca, no se redondea, para que un
     * arrastre lento no "vibree" entre dos posiciones.
     */
    fun move(
        current: HudBounds,
        dx: Float,
        dy: Float,
        screenWidth: Int,
        screenHeight: Int,
        margin: Int = DEFAULT_MARGIN_PX,
    ): HudBounds = place(
        desiredX = current.x + dx.toInt(),
        desiredY = current.y + dy.toInt(),
        width = current.width,
        height = current.height,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        margin = margin,
    )

    /**
     * Pega el HUD al borde horizontal más cercano.
     *
     * Se usa al soltar el arrastre: deja el HUD alineado y deja libre el
     * centro de la pantalla, que es donde está el juego.
     */
    fun snapToEdge(
        bounds: HudBounds,
        screenWidth: Int,
        margin: Int = DEFAULT_MARGIN_PX,
    ): HudBounds {
        val center = screenWidth / 2
        val targetX = if (bounds.x + bounds.width / 2 < center) {
            clampX(margin, bounds.width, screenWidth, margin)
        } else {
            clampX(screenWidth - bounds.width - margin, bounds.width, screenWidth, margin)
        }
        return bounds.copy(x = targetX)
    }

    /**
     * Posición inicial: arriba a la derecha, por debajo de la barra de estado
     * estimada ([topInsetPx]). Es la esquina que menos molesta en un juego y
     * la que el usuario espera encontrar.
     */
    fun initial(
        screenWidth: Int,
        screenHeight: Int,
        width: Int,
        height: Int,
        margin: Int = DEFAULT_MARGIN_PX,
        topInsetPx: Int = 0,
    ): HudBounds = place(
        desiredX = screenWidth - width - margin,
        desiredY = topInsetPx + margin,
        width = width,
        height = height,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        margin = margin,
    )
}
