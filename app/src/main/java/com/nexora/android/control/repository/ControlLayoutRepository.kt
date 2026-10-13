package com.nexora.android.control.repository

import com.nexora.android.control.data.local.ControlLayoutDao
import com.nexora.android.control.data.local.layoutFrom
import com.nexora.android.control.data.local.toEntities
import com.nexora.android.control.model.ControlLayout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Perfiles de control táctil de NEXORA.
 *
 * Junta las dos tablas del HUD (ajustes y botones) en un único
 * [ControlLayout] observable: la UI solo ve un objeto coherente, y lo que falte
 * en base de datos se rellena con la disposición predeterminada.
 */
class ControlLayoutRepository(
    private val dao: ControlLayoutDao,
) {

    /** Disposición del HUD de un juego, actualizada ante cualquier cambio. */
    fun observeLayout(gameId: String): Flow<ControlLayout> =
        combine(
            dao.observeProfile(gameId),
            dao.observeButtons(gameId),
        ) { profile, buttons ->
            layoutFrom(gameId = gameId, profile = profile, buttons = buttons)
        }

    /** Guarda la disposición completa de un juego. */
    suspend fun save(layout: ControlLayout) {
        val (profile, buttons) = layout.toEntities(updatedAt = System.currentTimeMillis())
        dao.upsertProfile(profile)
        dao.upsertButtons(buttons)
    }

    /** Borra el perfil guardado: el juego vuelve a la disposición de fábrica. */
    suspend fun reset(gameId: String) {
        dao.deleteButtons(gameId)
        dao.deleteProfile(gameId)
    }

    /** Vacía todos los perfiles del dispositivo (Fase 7, Ajustes Globales). */
    suspend fun resetAllForDevice() {
        dao.deleteAllButtons()
        dao.deleteAllProfiles()
    }
}
