package com.nexora.android.control.model

/**
 * Un botón flotante del HUD, ya colocado.
 *
 * Las coordenadas son **normalizadas** (0..1) y el tamaño va en fracciones del
 * lado menor de la superficie: así la misma disposición se ve igual en una
 * tableta apaisada y en un móvil vertical, y se puede guardar en base de datos
 * sin guardar densidades ni orientación.
 *
 * @property centerX Centro horizontal, fracción del ancho.
 * @property centerY Centro vertical, fracción del alto.
 * @property size Diámetro, fracción del lado menor.
 */
data class HudControl(
    val action: ControlAction,
    val centerX: Float = action.defaultCenterX,
    val centerY: Float = action.defaultCenterY,
    val size: Float = action.defaultSize,
    val visible: Boolean = action.defaultVisible,
) {

    /** Centro dentro de la superficie y tamaño dentro de los límites útiles. */
    fun clamped(): HudControl = copy(
        centerX = centerX.coerceIn(0f, 1f),
        centerY = centerY.coerceIn(0f, 1f),
        size = size.coerceIn(ControlLayout.MIN_SIZE, ControlLayout.MAX_SIZE),
    )

    companion object {

        /** Un control con la posición y el tamaño que le tocan por defecto. */
        fun defaultOf(action: ControlAction): HudControl = HudControl(action = action)
    }
}

/**
 * Perfil de control de un juego: cómo se ve y cómo se comporta su HUD.
 *
 * @property gameId Juego al que pertenece.
 * @property overlayOpacity Transparencia de la capa flotante, de 0 (invisible)
 *   a 1 (opaca). Se guarda porque el HUD tapa parte del juego: cada usuario
 *   encuentra su punto entre "veo los botones" y "veo el campo de batalla".
 * @property dragBoxEnabled Si el arrastre con un dedo dibuja el recuadro de
 *   selección. Es el gesto que define a los RTS, pero estorba en otros géneros.
 * @property controls Botones del HUD, en orden estable.
 * @property updatedAtMillis Última vez que se guardó.
 */
data class ControlLayout(
    val gameId: String,
    val overlayOpacity: Float = DEFAULT_OPACITY,
    val dragBoxEnabled: Boolean = true,
    val controls: List<HudControl> = ControlAction.ALL.map { HudControl.defaultOf(it) },
    val updatedAtMillis: Long = 0L,
) {

    /** Botones que se dibujan realmente. */
    val visibleControls: List<HudControl>
        get() = controls.filter { it.visible }

    /** Cuántos botones hay activos, para el resumen del detalle. */
    val visibleCount: Int
        get() = visibleControls.size

    /** Busca el botón de una acción; `null` si no está en la disposición. */
    fun controlFor(action: ControlAction): HudControl? =
        controls.firstOrNull { it.action == action }

    /**
     * Sustituye un botón conservando la posición que ocupa en la lista.
     *
     * El orden importa: es el del editor y el de dibujado, así que no se
     * reordena al guardar.
     */
    fun withControl(control: HudControl): ControlLayout {
        val index = controls.indexOfFirst { it.action == control.action }
        val updated = if (index < 0) {
            controls + control
        } else {
            controls.toMutableList().also { it[index] = control }
        }
        return copy(controls = updated)
    }

    /** Aplica los límites a todos los botones: nadie puede quedar fuera. */
    fun clamped(): ControlLayout = copy(
        overlayOpacity = overlayOpacity.coerceIn(0f, 1f),
        controls = controls.map { it.clamped() },
    )

    companion object {

        const val DEFAULT_OPACITY = 0.78f
        const val MIN_SIZE = 0.06f
        const val MAX_SIZE = 0.45f

        /** Disposición de fábrica de un juego. */
        fun defaults(gameId: String): ControlLayout = ControlLayout(gameId = gameId)
    }
}
