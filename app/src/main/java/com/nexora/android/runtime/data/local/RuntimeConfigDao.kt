package com.nexora.android.runtime.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * DAO para la configuración de entorno de ejecución por juego.
 *
 * Sigue el mismo patrón que el resto de DAOs de NEXORA: `Flow` para la
 * lectura reactiva y `Upsert` para escribir sin distinguir entre alta y
 * actualización.
 */
@Dao
interface RuntimeConfigDao {

    @Query("SELECT * FROM runtime_configs WHERE gameId = :gameId LIMIT 1")
    fun observeConfig(gameId: String): Flow<RuntimeConfigEntity?>

    @Query("SELECT * FROM runtime_configs WHERE gameId = :gameId LIMIT 1")
    suspend fun getConfig(gameId: String): RuntimeConfigEntity?

    @Upsert
    suspend fun upsertConfig(entity: RuntimeConfigEntity)

    @Query("DELETE FROM runtime_configs WHERE gameId = :gameId")
    suspend fun deleteConfig(gameId: String)
}