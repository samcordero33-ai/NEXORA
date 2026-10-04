package com.nexora.android.game.repository

import com.nexora.android.engine.profile.PerformanceProfile
import com.nexora.android.game.data.local.GameDao
import com.nexora.android.game.data.local.toDomain
import com.nexora.android.game.data.local.toEntity
import com.nexora.android.game.model.Game
import com.nexora.android.game.model.GameIconPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Biblioteca de juegos de NEXORA.
 *
 * Fase 3: la biblioteca se apoya en **Room**. Es la misma interfaz que tenía en
 * memoria en la Fase 2 (`games`, `addAll`, `remove`, `clear`), así que la UI no
 * cambia de contrato; lo que cambia es que los juegos detectados sobreviven al
 * reinicio de la aplicación.
 *
 * El deduplicado lo hace la base de datos, no este repositorio: `exePath` tiene
 * índice único y la inserción es `IGNORE`, de modo que reescanear no duplica ni
 * pisa los datos del juego ya guardado.
 */
class GameLibraryRepository(
    private val dao: GameDao,
) {

    /** Juegos de la biblioteca, ordenados alfabéticamente por título. */
    val games: Flow<List<Game>> = dao.observeAll().map { entities ->
        entities.map { it.toDomain() }
    }

    /** Un juego concreto observado en el tiempo; `null` si se borra. */
    fun observeGame(gameId: String): Flow<Game?> =
        dao.observeById(gameId).map { it?.toDomain() }

    /**
     * Incorpora los juegos detectados en un escaneo.
     *
     * @return número de juegos realmente nuevos; los repetidos se descartan.
     */
    suspend fun addAll(detected: List<Game>): Int {
        if (detected.isEmpty()) return 0
        val inserted = dao.insertIgnoringConflicts(detected.map { it.toEntity() })
        return inserted.count { it != ROW_IGNORED }
    }

    /** Elimina un juego por identificador. */
    suspend fun remove(gameId: String) {
        dao.deleteById(gameId)
    }

    /** Vacía la biblioteca. */
    suspend fun clear() {
        dao.deleteAll()
    }

    /** Lee un juego puntual; `null` si ya no está en la biblioteca. */
    suspend fun gameById(gameId: String): Game? = dao.getById(gameId)?.toDomain()

    /** Cambia el icono predeterminado de un juego. */
    suspend fun setIconPreset(gameId: String, preset: GameIconPreset) {
        dao.updateIconPreset(gameId, preset.ordinal)
    }

    /**
     * Fija el perfil de rendimiento de un juego.
     *
     * @param profile Perfil elegido, o `null` para volver al automático: en
     *   ese caso la UI muestra lo que recomiende el AutoOptimizer.
     */
    suspend fun setProfile(gameId: String, profile: PerformanceProfile?) {
        dao.updateProfileId(gameId, profile?.id)
    }

    private companion object {

        /** Valor que devuelve Room en una fila ignorada por conflicto. */
        const val ROW_IGNORED = -1L
    }
}
