package com.nexora.android.runtime.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nexora.android.runtime.preferences.AppTheme
import com.nexora.android.runtime.preferences.GlobalPreferences

/**
 * Preferencias globales de la aplicación (Fase 7).
 *
 * Solo hay **una fila**, con `id = 1`. La forma "tabla singleton" es la
 * recomendada por Google para este tipo de settings cuando no quieres tirar
 * de DataStore.
 *
 * Por qué no `enum.ordinal`: usamos `wireValue` (string) para sobrevivir a
 * reordenamientos de enum. El DAO degrada cualquier valor desconocido a
 * [GlobalPreferences.DEFAULT].
 */
@Entity(tableName = "global_preferences")
data class GlobalPreferencesEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val theme: String,
    val animationsEnabled: Boolean,
    val autoExportLogs: Boolean,
    val defaultBackend: String,
    val updatedAt: Long,
) {
    fun toSettings(): GlobalPreferences = GlobalPreferences(
        theme = AppTheme.fromWireValue(theme),
        animationsEnabled = animationsEnabled,
        autoExportLogs = autoExportLogs,
        defaultBackend = defaultBackend,
    )

    companion object {
        const val SINGLETON_ID = 1

        fun fromSettings(settings: GlobalPreferences, now: Long): GlobalPreferencesEntity =
            GlobalPreferencesEntity(
                theme = settings.theme.wireValue,
                animationsEnabled = settings.animationsEnabled,
                autoExportLogs = settings.autoExportLogs,
                defaultBackend = settings.defaultBackend,
                updatedAt = now,
            )
    }
}