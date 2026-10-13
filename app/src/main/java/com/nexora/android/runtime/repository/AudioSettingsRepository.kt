package com.nexora.android.runtime.repository

import com.nexora.android.runtime.audio.AudioSettings
import com.nexora.android.runtime.data.local.AudioSettingsDao
import com.nexora.android.runtime.data.local.AudioSettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Repositorio de audio por juego (Fase 7).
 *
 * Patrón: si no hay fila para el juego, devolvemos [AudioSettings.DEFAULT];
 * así la pantalla de ajustes ya puede pintar las opciones desde el primer
 * frame sin mostrar "vacío".
 */
class AudioSettingsRepository(
    private val dao: AudioSettingsDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    fun observe(gameId: String): Flow<AudioSettings> =
        dao.observe(gameId)
            .map { it?.toSettings() ?: AudioSettings.DEFAULT }
            .distinctUntilChanged()

    suspend fun get(gameId: String): AudioSettings =
        dao.get(gameId)?.toSettings() ?: AudioSettings.DEFAULT

    suspend fun save(gameId: String, settings: AudioSettings) {
        dao.upsert(AudioSettingsEntity.fromSettings(gameId, settings.normalized(), clock()))
    }

    suspend fun reset(gameId: String) {
        dao.deleteForGame(gameId)
    }
}