package com.nexora.android.runtime.repository

import com.nexora.android.runtime.data.local.GlobalPreferencesDao
import com.nexora.android.runtime.data.local.GlobalPreferencesEntity
import com.nexora.android.runtime.preferences.GlobalPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Repositorio de configuración global (Fase 7).
 *
 * Decisión: cuando no existe fila en la DB, devolvemos [GlobalPreferences.DEFAULT].
 * Eso simplifica el `ViewModel`: no necesita un caso "todavía no se ha
 * inicializado" y la UI se puede pintar ya en su primer frame con valores
 * válidos.
 */
class GlobalPreferencesRepository(
    private val dao: GlobalPreferencesDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    fun observe(): Flow<GlobalPreferences> =
        dao.observe()
            .map { it?.toSettings() ?: GlobalPreferences.DEFAULT }
            .distinctUntilChanged()

    suspend fun current(): GlobalPreferences =
        dao.get()?.toSettings() ?: GlobalPreferences.DEFAULT

    suspend fun save(prefs: GlobalPreferences) {
        dao.upsert(GlobalPreferencesEntity.fromSettings(prefs.normalized(), clock()))
    }

    suspend fun reset() {
        save(GlobalPreferences.DEFAULT)
    }
}