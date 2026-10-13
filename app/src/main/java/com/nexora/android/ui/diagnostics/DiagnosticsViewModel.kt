package com.nexora.android.ui.diagnostics

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.android.runtime.diagnostics.DiagnosticsEvent
import com.nexora.android.runtime.diagnostics.DiagnosticsLevel
import com.nexora.android.runtime.diagnostics.DiagnosticsLogger
import com.nexora.android.runtime.diagnostics.LogExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Estado de la consola de diagnóstico.
 */
data class DiagnosticsUiState(
    val events: List<DiagnosticsEvent> = emptyList(),
    val exportedFile: File? = null,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
    val filterLevel: DiagnosticsLevel = DiagnosticsLevel.INFO,
)

/**
 * ViewModel de la consola de diagnóstico.
 *
 * No necesita `AndroidViewModel` para la lógica (todo es puro), pero hereda
 * de él para tener acceso a `applicationContext` cuando el usuario quiere
 * compartir el archivo exportado.
 */
class DiagnosticsViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val logger = DiagnosticsLogger.shared

    private val mutableState = MutableStateFlow(DiagnosticsUiState())
    val state: StateFlow<DiagnosticsUiState> = mutableState.asStateFlow()

    init {
        // Snapshot inicial + refresco en cada nuevo evento.
        viewModelScope.launch {
            var lastSeen = 0
            while (true) {
                val snap = logger.snapshot()
                if (snap.size != lastSeen) {
                    lastSeen = snap.size
                    mutableState.update { it.copy(events = snap) }
                }
                kotlinx.coroutines.delay(500L)
            }
        }
    }

    /** Cambia el nivel mínimo que la UI muestra. */
    fun setFilter(level: DiagnosticsLevel) {
        mutableState.update { it.copy(filterLevel = level) }
    }

    /** Exporta el log actual a un .txt y devuelve el archivo. */
    fun exportLog() {
        viewModelScope.launch {
            try {
                val target = withContext(Dispatchers.IO) {
                    val dir = File(
                        getApplication<Application>().filesDir,
                        "exports",
                    )
                    LogExporter.export(logger.snapshot(), dir)
                }
                mutableState.update {
                    it.copy(
                        exportedFile = target,
                        infoMessage = "Exportado a ${target.absolutePath}",
                        errorMessage = null,
                    )
                }
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(errorMessage = error.message ?: error.javaClass.simpleName)
                }
            }
        }
    }

    /** Genera un `Intent.ACTION_SEND` para compartir el último log exportado. */
    fun shareIntent(): Intent? {
        val file = mutableState.value.exportedFile ?: return null
        val authority = getApplication<Application>().packageName + ".fileprovider"
        val uri: Uri = FileProvider.getUriForFile(
            getApplication(),
            authority,
            file,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "NEXORA diagnostics log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Borra el log en memoria. */
    fun clearLog() {
        logger.clear()
        mutableState.update { it.copy(events = emptyList(), exportedFile = null) }
    }

    /** Descarta mensajes. */
    fun dismissMessages() {
        mutableState.update { it.copy(infoMessage = null, errorMessage = null) }
    }
}