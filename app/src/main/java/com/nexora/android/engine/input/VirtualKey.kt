package com.nexora.android.engine.input

/**
 * Teclas virtuales que NEXORA envía al juego cuando llegue el runtime.
 *
 * Los códigos son los de teclado virtual de Win32 (los mismos que usa la API
 * `SendInput` de Windows), de modo que el día que exista la capa de inyección
 * no haya que traducir nada: el valor ya es el que espera el sistema.
 *
 * Están en Kotlin puro: aquí no se inyecta nada ni se habla con el sistema,
 * solo se describe **qué tecla** representa cada botón del HUD.
 */
enum class VirtualKey(
    /** Código de tecla virtual de Win32. */
    val keyCode: Int,
    /** Etiqueta corta mostrada en la UI. */
    val label: String,
) {
    CONTROL(keyCode = 0x11, label = "Ctrl"),
    SHIFT(keyCode = 0x10, label = "Shift"),
    MENU(keyCode = 0x12, label = "Alt"),
    SPACE(keyCode = 0x20, label = "Espacio"),
    RETURN(keyCode = 0x0D, label = "Enter"),
    ESCAPE(keyCode = 0x1B, label = "Esc"),

    DIGIT_1(keyCode = 0x31, label = "1"),
    DIGIT_2(keyCode = 0x32, label = "2"),
    DIGIT_3(keyCode = 0x33, label = "3"),

    KEY_A(keyCode = 0x41, label = "A"),
    KEY_S(keyCode = 0x53, label = "S"),
    KEY_D(keyCode = 0x44, label = "D"),
    KEY_W(keyCode = 0x57, label = "W"),

    ARROW_UP(keyCode = 0x26, label = "▲"),
    ARROW_DOWN(keyCode = 0x28, label = "▼"),
    ARROW_LEFT(keyCode = 0x25, label = "◀"),
    ARROW_RIGHT(keyCode = 0x27, label = "▶"),
}

/**
 * Pulsación completa: una tecla principal y sus modificadoras.
 *
 * Se modela como lista y no como "tecla + modificadores" porque el orden
 * importa al reproducirla: primero se bajan las modificadoras y al final se
 * suben.
 */
data class KeyStroke(
    val keys: List<VirtualKey>,
) {

    constructor(vararg keys: VirtualKey) : this(keys.toList())

    /** Texto de la pulsación, tal y como se muestra en el HUD. */
    val label: String
        get() = keys.joinToString(" + ") { it.label }

    companion object {

        /** Atajo de un grupo de control: `Ctrl` + dígito. */
        fun controlGroup(digit: VirtualKey): KeyStroke = KeyStroke(VirtualKey.CONTROL, digit)
    }
}
