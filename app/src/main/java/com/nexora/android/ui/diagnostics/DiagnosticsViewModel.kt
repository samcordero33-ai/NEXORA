package com.nexora.android.ui.diagnostics

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.android.runtime.diagnostics.CrashFinding
import com.nexora.android.runtime.diagnostics.CrashLogAnalyzer
import com.nexora.android.runtime.diagnostics.DiagnosticsEvent
import com.nexora.android.runtime.diagnostics.DiagnosticsLevel
import com.nexora.android.runtime.diagnostics.DiagnosticsLogger
import com.nexora.android.runtime.diagnostics.LogExporter
import com.nexora.android.runtime.diagnostics.LogFileInfo
import com.nexora.android.runtime.diagnostics.LogFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Pestañas de la consola de diagnóstico. */
enum class DiagnosticsTab(val label: String) {
    /** Ring buffer en memoria: lo que pasa ahora mismo. */
    LIVE("En vivo"),

    /** Archivos `.log` de sesiones anteriores, con análisis de fallo. */
    SESSION("Sesión"),
}

/**
 * Estado de la consola de diagnóstico.
 *
 * @property events Eventos del ring buffer en memoria.
 * @property tab Pestaña activa.
 * @property logs Archivos de sesión guardados en disco.
 * @property selectedLog Log abierto en el visor, si hay alguno.
 * @property logLines Últimas líneas del log abierto.
 * @property finding Diagnóstico del log abierto.
 */
data class DiagnosticsUiState(
    val events: List<DiagnosticsEvent> = emptyList(),
    val exportedFile: File? = null,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
    val filterLevel: DiagnosticsLevel = DiagnosticsLevel.INFO,
    val tab: DiagnosticsTab = DiagnosticsTab.LIVE,
    val logs: List<LogFileInfo> = emptyList(),
    val selectedLog: LogFileInfo? = null,
    val logLines: List<String> = emptyList(),
    val finding: CrashFinding? = null,
)

/**
 * ViewModel de la consola de diagnóstico.
 *
 * Dos fuentes de verdad, a propósito:
 * - **En vivo**: el ring buffer de [DiagnosticsLogger], que comparten motor,
 *   audio, entrada y ViewModels. Es lo que se mira mientras el juego corre.
 * - **Sesión**: los archivos que [com.nexora.android.runtime.diagnostics.SessionLogRecorder]
 *   escribió en `filesDir/logs`. Es lo que sobrevive a un cierre inesperado,
 *   y por eso es lo que se analiza cuando algo ha fallado.
 */
class DiagnosticsViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val logger = DiagnosticsLogger.shared
    private val logDir = File(application.filesDir, "logs")

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
        refreshLogs()
    }

    /** Cambia el nivel mínimo que la UI muestra. */
    fun setFilter(level: DiagnosticsLevel) {
        mutableState.update { it.copy(filterLevel = level) }
    }

    /** Cambia de pestaña; al entrar en "Sesión" se relee el disco. */
    fun setTab(tab: DiagnosticsTab) {
        mutableState.update { it.copy(tab = tab) }
        if (tab == DiagnosticsTab.SESSION) refreshLogs()
    }

    /** Relee la lista de logs de sesión. */
    fun refreshLogs() {
        viewModelScope.launch {
            val logs = withContext(Dispatchers.IO) { LogFileStore.list(logDir) }
            mutableState.update { it.copy(logs = logs) }
        }
    }

    /** Abre un log: lee la cola y lo analiza en busca de un fallo conocido. */
    fun openLog(info: LogFileInfo) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val lines = LogFileStore.readTail(info.file, LogFileStore.DEFAULT_TAIL_LINES)
                lines to CrashLogAnalyzer.analyze(lines)
            }
            mutableState.update {
                it.copy(
                    selectedLog = info,
                    logLines = result.first,
                    finding = result.second,
                    errorMessage = null,
                )
            }
        }
    }

    /** Cierra el visor de log. */
    fun closeLog() {
        mutableState.update { it.copy(selectedLog = null, logLines = emptyList(), finding = null) }
    }

    /** Borra un log de sesión. */
    fun deleteLog(info: LogFileInfo) {
        viewModelScope.launch {
            val removed = withContext(Dispatchers.IO) { LogFileStore.delete(info.file) }
            mutableState.update {
                it.copy(
                    infoMessage = if (removed) "Registro borrado" else null,
                    errorMessage = if (removed) null else "No se pudo borrar el registro",
                    selectedLog = if (removed && it.selectedLog?.name == info.name) null else it.selectedLog,
                    logLines = if (removed && it.selectedLog?.name == info.name) emptyList() else it.logLines,
                    finding = if (removed && it.selectedLog?.name == info.name) null else it.finding,
                )
            }
            refreshLogs()
        }
    }

    /** Exporta el log actual a un `.txt` y devuelve el archivo. */
    fun exportLog() {
        viewModelScope.launch {
            try {
                val target = withContext(Dispatchers.IO) {
                    val dir = File(getApplication<Application>().filesDir, "exports")
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
    fun shareIntent(): Intent? = shareIntentFor(mutableState.value.exportedFile, "text/plain")

    /** Genera un `Intent.ACTION_SEND` para compartir un log de sesión. */
    fun shareIntentFor(info: LogFileInfo): Intent? = shareIntentFor(info.file, "text/plain")

    private fun shareIntentFor(file: File?, mime: String): Intent? {
        if (file == null || !file.exists()) return null
        return runCatching {
            val app = getApplication<Application>()
            val uri: Uri = FileProvider.getUriForFile(
                app,
                app.packageName + ".fileprovider",
                file,
            )
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "NEXORA · ${file.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }.getOrNull()
    }

    /** Borra el log en memoria. Los archivos de sesión no se tocan. */
    fun clearLog() {
        logger.clear()
        mutableState.update { it.copy(events = emptyList(), exportedFile = null) }
    }

    /** Descarta mensajes. */
    fun dismissMessages() {
        mutableState.update { it.copy(infoMessage = null, errorMessage = null) }
    }
}
