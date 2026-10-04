package com.nexora.android.game.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Acceso a la biblioteca de juegos.
 *
 * Las consultas de lectura devuelven `Flow`: Room re-emite la lista cada vez que
 * cambia la tabla, así que la UI se actualiza sola tras un escaneo, un borrado o
 * un cambio de icono, sin que el ViewModel tenga que refrescar nada a mano.
 */
@Dao
interface GameDao {

    /** Biblioteca completa, ordenada alfabéticamente por título. */
    @Query("SELECT * FROM games ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<GameEntity>>

    /** Un juego concreto, o `null` si ya no existe. */
    @Query("SELECT * FROM games WHERE id = :id")
    fun observeById(id: String): Flow<GameEntity?>

    /** Lectura puntual de un juego, para operaciones que necesitan su estado. */
    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun getById(id: String): GameEntity?

    /**
     * Inserta los juegos detectados ignorando los que ya existen.
     *
     * `IGNORE` apoya el índice único de `exePath`: un juego ya en la biblioteca
     * no se toca (conserva su icono elegido y su fecha de alta) y no se crea una
     * fila duplicada.
     *
     * @return `rowId` de cada fila insertada, o `-1` en la que se ignoró.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringConflicts(games: List<GameEntity>): List<Long>

    /** Cambia el icono predeterminado de un juego. */
    @Query("UPDATE games SET iconPreset = :iconPreset WHERE id = :id")
    suspend fun updateIconPreset(id: String, iconPreset: Int)

    /** Asigna o borra (`null`) el icono personalizado de un juego. */
    @Query("UPDATE games SET iconUri = :iconUri WHERE id = :id")
    suspend fun updateIconUri(id: String, iconUri: String?)

    /**
     * Fija el perfil de rendimiento de un juego.
     *
     * @param profileId Identificador del perfil, o `null` para volver al
     *   automático (decide el AutoOptimizer).
     */
    @Query("UPDATE games SET profileId = :profileId WHERE id = :id")
    suspend fun updateProfileId(id: String, profileId: String?)

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM games")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM games")
    suspend fun count(): Int
}
