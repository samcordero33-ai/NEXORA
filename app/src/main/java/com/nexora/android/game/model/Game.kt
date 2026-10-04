package com.nexora.android.game.model

import com.nexora.android.engine.profile.PerformanceProfile

/**
 * Modelo de datos de un juego de la biblioteca.
 *
 * Fase 1: solo la estructura.
 * Fase 2: el escáner de almacenamiento rellena [source], [sizeBytes] y
 * [documentUri] además de los campos básicos.
 * Fase 3: se añaden [iconPreset] e [iconUri] y el modelo se persiste en Room
 * (`game/data/local/GameEntity.kt`), de modo que la biblioteca sobrevive al
 * reinicio de la aplicación.
 * Fase 4: se añade [profile], el perfil de rendimiento fijado por el usuario.
 *
 * @property id Identificador estable. El escáner lo deriva de la ruta del
 *   ejecutable, de modo que volver a escanear no duplica la entrada.
 * @property name Nombre mostrado en la biblioteca.
 * @property executablePath Ruta del ejecutable dentro del prefijo del runtime.
 * @property installDirectory Directorio de instalación del juego.
 * @property iconPreset Icono predeterminado elegido para la tarjeta. Se guarda
 *   en base de datos y el usuario lo cambia desde "Ajustes del juego".
 * @property iconUri Icono personalizado opcional, como URI en texto. `null`
 *   significa que se usa el [iconPreset]. Está previsto para fases posteriores.
 * @property profile Perfil de rendimiento elegido por el usuario para este
 *   juego. `null` es **automático**: manda lo que recomiende el AutoOptimizer
 *   según el hardware y las pistas del juego.
 * @property addedAtMillis Momento de alta, en milisegundos de época.
 * @property source Procedencia del alta.
 * @property sizeBytes Tamaño del ejecutable en bytes; 0 si no se pudo leer.
 * @property documentUri `Uri` en texto del documento cuando el juego se detectó
 *   a través del Storage Access Framework. Es la única forma de volver a abrir
 *   ese archivo en Android 11+ sin permiso amplio de almacenamiento; `null`
 *   cuando el juego se detectó por ruta de sistema de archivos o se añadió a
 *   mano.
 */
data class Game(
    val id: String,
    val name: String,
    val executablePath: String,
    val installDirectory: String? = null,
    val iconPreset: GameIconPreset = GameIconPreset.DEFAULT,
    val iconUri: String? = null,
    val profile: PerformanceProfile? = null,
    val addedAtMillis: Long = 0L,
    val source: GameSource = GameSource.MANUAL,
    val sizeBytes: Long = 0L,
    val documentUri: String? = null,
)

/** Procedencia de un juego de la biblioteca. */
enum class GameSource {

    /** Añadido a mano por el usuario. */
    MANUAL,

    /** Detectado por el escáner de almacenamiento. */
    SCAN,
    ;

    companion object {

        /**
         * Traduce el valor guardado en base de datos a un origen.
         *
         * Un valor desconocido cae a [MANUAL] en vez de fallar.
         */
        fun fromName(name: String?): GameSource =
            entries.firstOrNull { it.name == name } ?: MANUAL
    }
}

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
