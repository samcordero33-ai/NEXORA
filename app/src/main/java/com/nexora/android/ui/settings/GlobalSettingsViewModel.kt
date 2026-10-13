package com.nexora.android.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.android.game.data.local.NexoraDatabase
import com.nexora.android.runtime.diagnostics.DiagnosticsLevel
import com.nexora.android.runtime.diagnostics.DiagnosticsLogger
import com.nexora.android.runtime.preferences.AppTheme
import com.nexora.android.runtime.preferences.GlobalPreferences
import com.nexora.android.runtime.repository.GlobalPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Estado de la pantalla de Ajustes Globales.
 */
data class GlobalSettingsUiState(
    val preferences: GlobalPreferences = GlobalPreferences.DEFAULT,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
)

/**
 * ViewModel de los Ajustes Globales.
 *
 * Lee de [GlobalPreferencesRepository] y expone un estado reactivo. Cualquier
 * cambio en la DB se refleja automáticamente.
 */
class GlobalSettingsViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val database = NexoraDatabase.getInstance(application)
    private val repository = GlobalPreferencesRepository(database.globalPreferencesDao())
    private val diagnostics = DiagnosticsLogger.shared

    private val mutableTransient = MutableStateFlow<Pair<String?, String?>>(null to null)

    val state: StateFlow<GlobalSettingsUiState> =
        combine(repository.observe(), mutableTransient) { prefs, transient ->
            GlobalSettingsUiState(
                preferences = prefs,
                infoMessage = transient.first,
                errorMessage = transient.second,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = GlobalSettingsUiState(),
        )

    fun setTheme(theme: AppTheme) = update { it.copy(theme = theme) }

    fun setAnimationsEnabled(enabled: Boolean) = update { it.copy(animationsEnabled = enabled) }

    fun setAutoExportLogs(enabled: Boolean) = update { it.copy(autoExportLogs = enabled) }

    fun setDefaultBackend(backend: String) = update { it.copy(defaultBackend = backend) }

    fun resetAll() {
        viewModelScope.launch {
            runCatching {
                repository.reset()
                // Limpiamos la biblioteca y el control del HUD.
                com.nexora.android.game.repository.GameLibraryRepository(database.gameDao()).clear()
                com.nexora.android.control.repository.ControlLayoutRepository(database.controlLayoutDao())
                    .resetAllForDevice()
                diagnostics.record(DiagnosticsLevel.WARN, "settings", "Ajustes restablecidos de fábrica")
            }.onSuccess {
                mutableTransient.value = "Ajustes restablecidos." to null
            }.onFailure { error ->
                diagnostics.record(DiagnosticsLevel.ERROR, "settings", "Error en reset: ${error.message}")
                mutableTransient.value = null to (error.message ?: error.javaClass.simpleName)
            }
        }
    }

    fun dismissMessages() {
        mutableTransient.value = null to null
    }

    private fun update(transform: (GlobalPreferences) -> GlobalPreferences) {
        viewModelScope.launch {
            val current = state.value.preferences
            val next = transform(current).normalized()
            runCatching { repository.save(next) }
                .onSuccess {
                    mutableTransient.value = "Ajustes guardados." to null
                    diagnostics.record(
                        DiagnosticsLevel.INFO,
                        "settings",
                        "Preferencias actualizadas: tema=${next.theme.wireValue}, " +
                            "anim=${next.animationsEnabled}, autoExport=${next.autoExportLogs}",
                    )
                }
                .onFailure { error ->
                    diagnostics.record(DiagnosticsLevel.ERROR, "settings", "Error guardando: ${error.message}")
                    mutableTransient.value = null to (error.message ?: error.javaClass.simpleName)
                }
        }
    }
}