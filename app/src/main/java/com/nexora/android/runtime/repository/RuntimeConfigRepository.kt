package com.nexora.android.runtime.repository

import com.nexora.android.runtime.data.local.RuntimeConfigDao
import com.nexora.android.runtime.data.local.toEntity
import com.nexora.android.runtime.data.local.toSettings
import com.nexora.android.runtime.env.EnvironmentSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repositorio de la configuración del entorno de ejecución.
 *
 * Misma forma que [com.nexora.android.control.repository.ControlLayoutRepository]:
 * una `Flow` por juego que devuelve los ajustes vigentes (los guardados o
 * los de fábrica si todavía no hay fila), y un `save` que escribe en Room.
 */
class RuntimeConfigRepository(
    private val dao: RuntimeConfigDao,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * Ajustes del entorno para un juego. Si no hay fila guardada, devuelve
     * los valores por defecto.
     */
    fun observeSettings(gameId: String): Flow<EnvironmentSettings> =
        dao.observeConfig(gameId).map { it?.toSettings() ?: EnvironmentSettings.DEFAULT }

    /** Lee los ajustes en un instante; `null` si no hay fila. */
    suspend fun settings(gameId: String): EnvironmentSettings? =
        dao.getConfig(gameId)?.toSettings()

    /** Persiste los ajustes. */
    suspend fun save(gameId: String, settings: EnvironmentSettings) {
        dao.upsertConfig(settings.toEntity(gameId, updatedAt = now()))
    }

    /** Borra la fila (devuelve el juego a los ajustes de fábrica). */
    suspend fun reset(gameId: String) {
        dao.deleteConfig(gameId)
    }
}