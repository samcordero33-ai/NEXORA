package com.nexora.android.control.model

import com.nexora.android.engine.input.KeyStroke
import com.nexora.android.engine.input.VirtualKey

/**
 * Forma en la que un botón del HUD se comporta al tocarlo.
 */
enum class ControlKind {

    /** Pulsación simple: se baja la tecla al tocar y se suelta al levantar. */
    TAP,

    /** Interruptor: el primer toque baja la tecla y el segundo la suelta. */
    TOGGLE,

    /** Cruz de cuatro direcciones. */
    DPAD,

    /** Palanca analógica: emite desplazamientos de cámara. */
    JOYSTICK,
}

/**
 * Acciones del HUD táctil de NEXORA.
 *
 * Cada entrada es un botón flotante (o un pad) que el usuario puede mover,
 * redimensionar y ocultar. Las seis primeras son las teclas que un RTS de
 * escritorio usa sin descanso: grupos de control, modificadora de órdenes,
 * ataque en movimiento e ir al último evento.
 *
 * @property id Identificador estable: es lo que se guarda en base de datos, así
 *   que no se puede renombrar sin migración.
 * @property label Texto del botón.
 * @property kind Comportamiento táctil.
 * @property stroke Pulsación que enviará al juego; `null` en los controles
 *   direccionales, que producen otras acciones.
 * @property defaultCenterX/defaultCenterY Centro del botón en la disposición
 *   predeterminada, como fracción del ancho y del alto de la superficie (0..1).
 *   Se normaliza porque el HUD se dibuja igual en cualquier pantalla.
 * @property defaultSize Diámetro, como fracción del lado menor de la superficie.
 */
enum class ControlAction(
    val id: String,
    val label: String,
    val kind: ControlKind,
    val stroke: KeyStroke?,
    val defaultCenterX: Float,
    val defaultCenterY: Float,
    val defaultSize: Float,
    val defaultVisible: Boolean,
) {

    CONTROL_GROUP_1(
        id = "group_1",
        label = "Ctrl + 1",
        kind = ControlKind.TAP,
        stroke = KeyStroke.controlGroup(VirtualKey.DIGIT_1),
        defaultCenterX = 0.62f,
        defaultCenterY = 0.84f,
        defaultSize = 0.13f,
        defaultVisible = true,
    ),

    CONTROL_GROUP_2(
        id = "group_2",
        label = "Ctrl + 2",
        kind = ControlKind.TAP,
        stroke = KeyStroke.controlGroup(VirtualKey.DIGIT_2),
        defaultCenterX = 0.74f,
        defaultCenterY = 0.84f,
        defaultSize = 0.13f,
        defaultVisible = true,
    ),

    CONTROL_GROUP_3(
        id = "group_3",
        label = "Ctrl + 3",
        kind = ControlKind.TAP,
        stroke = KeyStroke.controlGroup(VirtualKey.DIGIT_3),
        defaultCenterX = 0.86f,
        defaultCenterY = 0.84f,
        defaultSize = 0.13f,
        defaultVisible = true,
    ),

    SHIFT(
        id = "shift",
        label = "Shift",
        kind = ControlKind.TOGGLE,
        stroke = KeyStroke(VirtualKey.SHIFT),
        defaultCenterX = 0.62f,
        defaultCenterY = 0.70f,
        defaultSize = 0.13f,
        defaultVisible = true,
    ),

    ATTACK_MOVE(
        id = "attack_move",
        label = "A",
        kind = ControlKind.TAP,
        stroke = KeyStroke(VirtualKey.KEY_A),
        defaultCenterX = 0.74f,
        defaultCenterY = 0.70f,
        defaultSize = 0.13f,
        defaultVisible = true,
    ),

    GOTO_EVENT(
        id = "goto_event",
        label = "Espacio",
        kind = ControlKind.TAP,
        stroke = KeyStroke(VirtualKey.SPACE),
        defaultCenterX = 0.86f,
        defaultCenterY = 0.70f,
        defaultSize = 0.13f,
        defaultVisible = true,
    ),

    DPAD(
        id = "dpad",
        label = "D-Pad",
        kind = ControlKind.DPAD,
        stroke = null,
        defaultCenterX = 0.17f,
        defaultCenterY = 0.78f,
        defaultSize = 0.30f,
        defaultVisible = true,
    ),

    JOYSTICK(
        id = "joystick",
        label = "Joystick",
        kind = ControlKind.JOYSTICK,
        stroke = null,
        defaultCenterX = 0.17f,
        defaultCenterY = 0.78f,
        defaultSize = 0.30f,
        // Ocupa el mismo sitio que la cruz: solo uno de los dos suele estar
        // visible, y se cambia desde el editor.
        defaultVisible = false,
    ),
    ;

    companion object {

        /** Todas las acciones, en el orden en que se muestran en el editor. */
        val ALL: List<ControlAction> = entries.toList()

        /**
         * Traduce el identificador guardado a una acción; `null` si no existe.
         *
         * Un identificador desconocido (de una versión futura) se ignora en vez
         * de romper la lectura de la base de datos.
         */
        fun fromId(id: String?): ControlAction? =
            if (id.isNullOrBlank()) null else entries.firstOrNull { it.id == id }
    }
}

/** Flechas del D-Pad, en el orden en que se dibuja la cruz. */
val DPAD_DIRECTIONS: List<VirtualKey> = listOf(
    VirtualKey.ARROW_UP,
    VirtualKey.ARROW_LEFT,
    VirtualKey.ARROW_RIGHT,
    VirtualKey.ARROW_DOWN,
)
