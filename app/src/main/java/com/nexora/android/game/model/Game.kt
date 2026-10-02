package com.nexora.android.game.model

/**
 * Modelo de datos de un juego de la biblioteca.
 *
 * Fase 1: solo la estructura. Todavía no hay persistencia ni lectura de
 * biblioteca; el campo [executablePath] queda preparado para cuando el runtime
 * tenga que lanzar el ejecutable.
 *
 * @property id Identificador estable generado al añadir el juego.
 * @property name Nombre mostrado en la biblioteca.
 * @property executablePath Ruta del ejecutable dentro del prefijo del runtime.
 * @property installDirectory Directorio de instalación del juego.
 * @property coverUri Portada opcional, como URI en texto.
 * @property addedAtMillis Momento de alta, en milisegundos de época.
 */
data class Game(
    val id: String,
    val name: String,
    val executablePath: String,
    val installDirectory: String? = null,
    val coverUri: String? = null,
    val addedAtMillis: Long = 0L,
)

/**
 * Estado de la biblioteca de juegos.
 *
 * Se modela como tipo sellado para que la UI distinga explícitamente "todavía
 * no se ha cargado nada" de "ya se cargó y está vacía": son dos pantallas
 * distintas y mezclarlas produce parpadeos.
 */
sealed interface GameLibraryState {

    /** No se ha consultado la biblioteca todavía. */
    data object Loading : GameLibraryState

    /** La biblioteca se consultó y no tiene juegos. */
    data object Empty : GameLibraryState

    /** La biblioteca tiene juegos. */
    data class Ready(val games: List<Game>) : GameLibraryState
}
