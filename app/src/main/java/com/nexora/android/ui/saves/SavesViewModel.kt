package com.nexora.android.ui.saves

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.android.game.repository.GameLibraryRepository
import com.nexora.android.game.data.local.NexoraDatabase
import com.nexora.android.game.model.Game
import com.nexora.android.runtime.diagnostics.DiagnosticsLevel
import com.nexora.android.runtime.diagnostics.DiagnosticsLogger
import com.nexora.android.runtime.saves.SaveBackupArchive
import com.nexora.android.runtime.saves.SaveImporter
import com.nexora.android.runtime.saves.ShaderCacheCleaner
import com.nexora.android.runtime.saves.TempFileCleaner
import com.nexora.android.runtime.env.WinePrefix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Estado de la pantalla de gestión de partidas y caché (Fase 7).
 *
 * @property game Juego al que pertenecen las operaciones; `null` mientras
 *   no se haya cargado.
 * @property totalBytes Suma del tamaño actual del prefijo (Winetricks,
 *   user-profile, etc.).
 * @property infoMessage Mensaje informativo no bloqueante.
 * @property errorMessage Mensaje de error no bloqueante.
 */
data class SavesUiState(
    val game: Game? = null,
    val totalBytes: Long = 0L,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
) {
    val gameName: String get() = game?.name.orEmpty()
}

/**
 * ViewModel del gestor de partidas y caché.
 *
 * Mantiene un estado mínimo (juego + tamaño) y delega en funciones puras de
 * la capa `runtime/saves/` todas las modificaciones. Las operaciones costosas
 * van a `Dispatchers.IO`.
 */
class SavesViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val database = NexoraDatabase.getInstance(application)
    private val gameRepository = GameLibraryRepository(database.gameDao())
    private val diagnostics = DiagnosticsLogger.shared

    private val mutableState = MutableStateFlow(SavesUiState())
    val state: StateFlow<SavesUiState> = mutableState.asStateFlow()

    /** Carga el juego y mide el prefijo. */
    fun load(gameId: String) {
        viewModelScope.launch {
            val game = gameRepository.gameById(gameId)
            val prefix = WinePrefix.underFilesDir(
                filesDir = getApplication<Application>().filesDir,
                gameId = gameId,
            )
            val totalBytes = withContext(Dispatchers.IO) {
                if (prefix.exists()) dirSize(prefix.path) else 0L
            }
            mutableState.update {
                it.copy(game = game, totalBytes = totalBytes)
            }
        }
    }

    /** Exporta las partidas y perfil de usuario del prefijo a un ZIP. */
    fun exportSaves(target: File) {
        val gameId = mutableState.value.game?.id ?: return
        viewModelScope.launch {
            try {
                val prefix = WinePrefix.underFilesDir(
                    filesDir = getApplication<Application>().filesDir,
                    gameId = gameId,
                )
                val count = withContext(Dispatchers.IO) {
                    SaveBackupArchive.pack(
                        root = prefix.driveC,
                        outputZip = target,
                        entryPrefix = "nexora/$gameId",
                    )
                }
                diagnostics.record(DiagnosticsLevel.INFO, "saves", "Exportadas $count entradas a ${target.absolutePath}")
                mutableState.update {
                    it.copy(infoMessage = "Exportadas $count entradas", errorMessage = null)
                }
            } catch (error: Exception) {
                diagnostics.record(DiagnosticsLevel.ERROR, "saves", "Error exportando: ${error.message}")
                mutableState.update {
                    it.copy(errorMessage = error.message ?: error.javaClass.simpleName)
                }
            }
        }
    }

    /** Importa un ZIP de partidas dentro del prefijo. */
    fun importSaves(zipFile: File) {
        val gameId = mutableState.value.game?.id ?: return
        viewModelScope.launch {
            try {
                val summary = withContext(Dispatchers.IO) {
                    val target = WinePrefix.underFilesDir(
                        filesDir = getApplication<Application>().filesDir,
                        gameId = gameId,
                    ).driveC
                    SaveImporter.extract(zipFile, target)
                }
                diagnostics.record(
                    DiagnosticsLevel.INFO,
                    "saves",
                    "Importadas ${summary.entriesExtracted} entradas (${summary.totalBytes} B)",
                )
                val rejected = if (summary.rejectedEntries.isNotEmpty()) {
                    " (${summary.rejectedEntries.size} rechazadas por seguridad)"
                } else ""
                mutableState.update {
                    it.copy(
                        infoMessage = "Importadas ${summary.entriesExtracted} entradas$rejected",
                        errorMessage = null,
                    )
                }
            } catch (error: Exception) {
                diagnostics.record(DiagnosticsLevel.ERROR, "saves", "Error importando: ${error.message}")
                mutableState.update {
                    it.copy(errorMessage = error.message ?: error.javaClass.simpleName)
                }
            }
        }
    }

    /** Limpia la caché de shaders DXVK del juego. */
    fun cleanShaderCache() {
        val gameId = mutableState.value.game?.id ?: return
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val prefix = WinePrefix.underFilesDir(
                        filesDir = getApplication<Application>().filesDir,
                        gameId = gameId,
                    ).path
                    ShaderCacheCleaner.clean(prefix)
                }
                diagnostics.record(
                    DiagnosticsLevel.INFO,
                    "saves",
                    "Caché DXVK: ${result.deletedFiles} archivos, ${result.freedBytes} B liberados",
                )
                mutableState.update {
                    it.copy(
                        infoMessage = "Caché DXVK: ${result.deletedFiles} archivos borrados",
                        errorMessage = null,
                    )
                }
            } catch (error: Exception) {
                diagnostics.record(DiagnosticsLevel.ERROR, "saves", "Error limpiando caché: ${error.message}")
                mutableState.update {
                    it.copy(errorMessage = error.message ?: error.javaClass.simpleName)
                }
            }
        }
    }

    /** Limpia temporales recientes (<7 días) de los prefijos. */
    fun cleanTemporary(deep: Boolean = false) {
        viewModelScope.launch {
            try {
                val filesDir = getApplication<Application>().filesDir
                val targets = listOf(
                    File(filesDir, "cache/nexora-tmp"),
                    File(filesDir, "prefixes"),
                )
                val result = withContext(Dispatchers.IO) {
                    TempFileCleaner.clean(targets, deep)
                }
                diagnostics.record(
                    DiagnosticsLevel.INFO,
                    "saves",
                    "Temporales: ${result.deletedFiles} archivos, ${result.freedBytes} B",
                )
                mutableState.update {
                    it.copy(
                        infoMessage = "Temporales: ${result.deletedFiles} archivos borrados",
                        errorMessage = null,
                    )
                }
            } catch (error: Exception) {
                diagnostics.record(DiagnosticsLevel.ERROR, "saves", "Error limpiando temporales: ${error.message}")
                mutableState.update {
                    it.copy(errorMessage = error.message ?: error.javaClass.simpleName)
                }
            }
        }
    }

    /** Descarta mensajes. */
    fun dismissMessages() {
        mutableState.update { it.copy(infoMessage = null, errorMessage = null) }
    }

    private fun dirSize(path: File): Long {
        if (!path.exists()) return 0L
        var total = 0L
        for (f in path.walkTopDown()) if (f.isFile) total += f.length()
        return total
    }
}