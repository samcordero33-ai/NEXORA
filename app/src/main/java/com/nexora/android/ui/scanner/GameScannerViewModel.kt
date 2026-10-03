package com.nexora.android.ui.scanner

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.android.R
import com.nexora.android.game.data.local.NexoraDatabase
import com.nexora.android.game.model.Game
import com.nexora.android.game.model.GameIconPreset
import com.nexora.android.game.repository.GameLibraryRepository
import com.nexora.android.game.scan.GameScanner
import com.nexora.android.game.scan.ScanConfig
import com.nexora.android.game.scan.ScanOutcome
import com.nexora.android.game.scan.ScanProgress
import com.nexora.android.game.scan.StorageTargets
import com.nexora.android.permission.StorageAccessController
import com.nexora.android.permission.StorageAccessState
import com.nexora.android.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado de la pantalla principal.
 *
 * Se modela como una única data class inmutable: la pantalla es una función
 * pura de este estado, lo que la hace previewable y testeable sin ciclo de vida.
 *
 * @property games Juegos de la biblioteca, persistidos en Room.
 * @property selectedGameId Juego abierto en la pantalla de detalle; `null` es la
 *   biblioteca. La navegación es estado, así que sobrevive a la rotación y no
 *   necesita Navigation Compose ni back stack propio.
 * @property isScanning `true` mientras hay un escaneo en curso.
 * @property progress Progreso del escaneo en curso; `null` si no hay ninguno.
 * @property lastOutcome Resultado del último escaneo, para el resumen.
 * @property infoMessage Mensaje informativo no bloqueante.
 * @property errorMessage Mensaje de error no bloqueante.
 * @property storageAccess Estado de los permisos de almacenamiento.
 */
data class ScannerUiState(
    val games: List<Game> = emptyList(),
    val selectedGameId: String? = null,
    val isScanning: Boolean = false,
    val progress: ScanProgress? = null,
    val lastOutcome: ScanOutcome? = null,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
    val storageAccess: StorageAccessState = StorageAccessState(),
)

/**
 * ViewModel de la pantalla principal.
 *
 * Orquesta los permisos, el escáner y la biblioteca. No contiene lógica de
 * negocio: la decisión de qué es un juego vive en `GameScanner`, y la
 * biblioteca en `GameLibraryRepository` (respaldada por Room).
 */
class GameScannerViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val repository = GameLibraryRepository(
        dao = NexoraDatabase.getInstance(application).gameDao(),
    )
    private val accessController = StorageAccessController(application)
    private val settings: AppSettings = AppSettings.DEFAULT

    private val mutableUiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = mutableUiState.asStateFlow()

    init {
        // Room re-emite la consulta ante cualquier cambio de la tabla: la UI se
        // refresca sola tras escanear, borrar o cambiar un icono.
        viewModelScope.launch {
            repository.games.collect { games ->
                mutableUiState.update { state ->
                    state.copy(
                        games = games,
                        // Un juego borrado no puede seguir abierto en detalle.
                        selectedGameId = state.selectedGameId?.takeIf { id ->
                            games.any { it.id == id }
                        },
                    )
                }
            }
        }
        refreshStorageAccess()
    }

    /** Relee el estado de los permisos. Se llama al volver a la pantalla. */
    fun refreshStorageAccess() {
        mutableUiState.update { it.copy(storageAccess = accessController.currentState()) }
    }

    /** `Intent` para abrir el ajuste de acceso amplio; `null` en Android 10-. */
    fun allFilesAccessIntent(): Intent? = accessController.allFilesAccessIntent()

    /** Permisos clásicos que la Activity debe pedir en tiempo de ejecución. */
    fun legacyReadPermissions(): Array<String> = accessController.legacyReadPermissions()

    /**
     * Escanea la carpeta elegida con el Storage Access Framework.
     *
     * Conserva el permiso de lectura de forma persistente para que la carpeta
     * siga accesible tras reiniciar la aplicación.
     */
    fun onFolderSelected(treeUri: Uri) {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        refreshStorageAccess()
        scan { scanner -> scanner.scanTree(treeUri) { progress -> emitProgress(progress) } }
    }

    /** Escanea las carpetas habituales de juegos del dispositivo. */
    fun scanCommonDirectories() {
        if (!accessController.currentState().canReadSharedStorage) {
            mutableUiState.update {
                it.copy(
                    isScanning = false,
                    progress = null,
                    errorMessage = getString(R.string.scan_error_no_shared_access),
                    infoMessage = null,
                )
            }
            return
        }

        val targets = StorageTargets.commonGameDirectories(getApplication())
        if (targets.isEmpty()) {
            mutableUiState.update {
                it.copy(
                    isScanning = false,
                    progress = null,
                    errorMessage = getString(R.string.scan_error_no_targets),
                    infoMessage = null,
                )
            }
            return
        }

        scan { scanner ->
            scanner.scanTargets(targets) { progress -> emitProgress(progress) }
        }
    }

    // --- Navegación ---------------------------------------------------------

    /** Abre la pantalla de detalle de un juego. */
    fun openGameDetail(gameId: String) {
        mutableUiState.update { it.copy(selectedGameId = gameId) }
    }

    /** Vuelve de la pantalla de detalle a la biblioteca. */
    fun closeGameDetail() {
        mutableUiState.update { it.copy(selectedGameId = null) }
    }

    // --- Acciones de la biblioteca -----------------------------------------

    /**
     * Botón "Jugar".
     *
     * Fase 3: el juego ya está registrado y persistido, pero **no se lanza**: el
     * runtime de ejecución se integra en fases posteriores. El botón existe y es
     * funcional (informa al usuario) para no tener que tocar la tarjeta cuando
     * llegue el motor.
     */
    fun requestPlay(game: Game) {
        mutableUiState.update {
            it.copy(
                infoMessage = getString(R.string.play_not_available, game.name),
                errorMessage = null,
            )
        }
    }

    /** Cambia el icono predeterminado del juego al siguiente de la lista. */
    fun cycleGameIcon(gameId: String) {
        viewModelScope.launch {
            val game = repository.gameById(gameId) ?: return@launch
            val next = GameIconPreset.next(game.iconPreset)
            repository.setIconPreset(gameId, next)
            mutableUiState.update {
                it.copy(infoMessage = getString(R.string.detail_icon_changed), errorMessage = null)
            }
        }
    }

    /** Avisa de que la ruta del ejecutable se copió al portapapeles. */
    fun notifyPathCopied() {
        mutableUiState.update {
            it.copy(infoMessage = getString(R.string.detail_path_copied), errorMessage = null)
        }
    }

    /** Elimina un juego de la biblioteca (y cierra su detalle si estaba abierto). */
    fun removeGame(gameId: String) {
        viewModelScope.launch {
            repository.remove(gameId)
            mutableUiState.update {
                it.copy(
                    selectedGameId = it.selectedGameId?.takeUnless { id -> id == gameId },
                    infoMessage = null,
                    errorMessage = null,
                )
            }
        }
    }

    /** Vacía la biblioteca. */
    fun clearLibrary() {
        viewModelScope.launch {
            repository.clear()
            mutableUiState.update {
                it.copy(lastOutcome = null, infoMessage = null, selectedGameId = null)
            }
        }
    }

    /** Descarta los mensajes informativos y de error. */
    fun dismissMessages() {
        mutableUiState.update { it.copy(infoMessage = null, errorMessage = null) }
    }

    /** Libera el acceso persistente a una carpeta concedida. */
    fun releaseFolder(uriText: String) {
        runCatching {
            getApplication<Application>().contentResolver
                .releasePersistableUriPermission(
                    uriText.toUri(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
        }
        refreshStorageAccess()
    }

    private fun emitProgress(progress: ScanProgress) {
        mutableUiState.update { it.copy(progress = progress) }
    }

    /**
     * Ejecuta un escaneo en el ámbito del ViewModel.
     *
     * La cancelación se propaga sin convertirse en error: si la corrutina se
     * cancela (porque se abandona la pantalla), lo correcto es callar, no
     * mostrar un fallo.
     */
    private fun scan(block: suspend (GameScanner) -> ScanOutcome) {
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isScanning = true,
                    progress = ScanProgress.EMPTY,
                    errorMessage = null,
                    infoMessage = null,
                    lastOutcome = null,
                )
            }

            val scanner = GameScanner(
                context = getApplication(),
                config = ScanConfig(
                    maxDepth = settings.scanMaxDepth,
                    entryBudget = settings.scanEntryBudget,
                    filterNoise = settings.detectOnlyLikelyExecutables,
                ),
            )

            try {
                val outcome = block(scanner)
                val added = repository.addAll(outcome.games)
                mutableUiState.update {
                    it.copy(
                        isScanning = false,
                        progress = null,
                        lastOutcome = outcome,
                        infoMessage = buildSummary(outcome, added),
                    )
                }
            } catch (cancelled: CancellationException) {
                mutableUiState.update { it.copy(isScanning = false, progress = null) }
                throw cancelled
            } catch (error: Exception) {
                mutableUiState.update {
                    it.copy(
                        isScanning = false,
                        progress = null,
                        errorMessage = getString(
                            R.string.scan_error_failed,
                            error.message ?: error.javaClass.simpleName,
                        ),
                    )
                }
            }
        }
    }

    private fun buildSummary(outcome: ScanOutcome, added: Int): String =
        if (added == 0 && outcome.games.isEmpty()) {
            getString(R.string.scan_result_nothing)
        } else {
            getString(
                R.string.scan_result_summary,
                added,
                outcome.executablesDismissed,
            )
        }

    private fun getString(resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)
}
