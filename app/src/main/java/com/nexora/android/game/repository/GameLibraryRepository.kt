package com.nexora.android.game.repository

import com.nexora.android.game.model.Game
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Biblioteca de juegos de NEXORA.
 *
 * Fase 2: la biblioteca vive **solo en memoria**. No hay persistencia todavía
 * porque el contrato de almacenamiento (y con él el identificador canónico de un
 * juego) se fija en la Fase 3, junto con la capa de datos. Esta clase ya expone
 * la interfaz que tendrá entonces, así que sustituirla por una implementación
 * respaldada en Room no obliga a tocar la UI.
 *
 * El deduplicado se hace por [Game.executablePath]: es lo que identifica
 * inequívocamente un juego dentro de un mismo volumen.
 */
class GameLibraryRepository {

    private val mutableGames = MutableStateFlow<List<Game>>(emptyList())

    /** Juegos de la biblioteca, ordenados alfabéticamente por nombre. */
    val games: StateFlow<List<Game>> = mutableGames.asStateFlow()

    /**
     * Incorpora los juegos detectados en un escaneo.
     *
     * @return número de juegos realmente nuevos; los repetidos se descartan.
     */
    fun addAll(detected: List<Game>): Int {
        if (detected.isEmpty()) return 0
        var added = 0
        mutableGames.update { current ->
            val knownPaths = current.mapTo(HashSet()) { it.executablePath }
            val merged = current.toMutableList()
            for (game in detected) {
                if (game.executablePath in knownPaths) continue
                knownPaths += game.executablePath
                merged += game
                added++
            }
            merged.sortedBy { it.name.lowercase() }
        }
        return added
    }

    /** Elimina un juego por identificador. */
    fun remove(gameId: String) {
        mutableGames.update { current -> current.filterNot { it.id == gameId } }
    }

    /** Vacía la biblioteca. */
    fun clear() {
        mutableGames.update { emptyList() }
    }
}
