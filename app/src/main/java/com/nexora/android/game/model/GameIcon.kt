package com.nexora.android.game.model

/**
 * Iconos predeterminados de la biblioteca.
 *
 * Fase 3: un juego detectado no trae carátula, y tampoco se va a leer el icono
 * embebido del `.exe` (eso exigiría interpretar un binario de Windows, cosa que
 * NEXORA no hace). En su lugar cada juego guarda **qué icono predeterminado
 * usa**; el usuario puede cambiarlo desde "Ajustes del juego" y la elección se
 * conserva en base de datos.
 *
 * Se persiste el `ordinal`, así que el orden de las constantes es parte del
 * contrato de la base de datos: **añadir al final, nunca reordenar**.
 */
enum class GameIconPreset {

    /** Mando de consola: el predeterminado de la biblioteca. */
    JOYPAD,

    /** Máquina recreativa. */
    ARCADE,

    /** Dados. */
    DICE,
    ;

    companion object {

        /** Icono que se asigna a un juego recién detectado. */
        val DEFAULT: GameIconPreset = JOYPAD

        /** Iconos disponibles, en orden de persistencia. */
        val ALL: List<GameIconPreset> get() = entries.toList()

        /**
         * Traduce el valor guardado en base de datos a un preset.
         *
         * Un ordinal desconocido (base de datos de una versión futura, o datos
         * corruptos) cae al predeterminado en vez de romper la UI.
         */
        fun fromOrdinal(ordinal: Int): GameIconPreset =
            entries.getOrElse(ordinal) { DEFAULT }

        /** Siguiente preset de la lista, en ciclo. */
        fun next(current: GameIconPreset): GameIconPreset {
            val all = ALL
            val index = all.indexOf(current)
            return all[(index + 1) % all.size]
        }
    }
}
