package com.nexora.android.ui.scanner

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.android.R
import com.nexora.android.engine.DeviceInfo
import com.nexora.android.engine.DeviceInfoProvider
import com.nexora.android.engine.profile.AutoOptimizer
import com.nexora.android.engine.profile.GameProfileHints
import com.nexora.android.engine.profile.GameProfileInspector
import com.nexora.android.engine.profile.PerformanceParameters
import com.nexora.android.engine.profile.PerformanceProfile
import com.nexora.android.engine.profile.ProfileRecommendation
import com.nexora.android.control.model.ControlAction
import com.nexora.android.control.model.ControlLayout
import com.nexora.android.control.model.HudControl
import com.nexora.android.control.repository.ControlLayoutRepository
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
import kotlinx.coroutines.Job
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
 * @property deviceInfo Datos del dispositivo; los usa el AutoOptimizer.
 * @property profileHints Pistas del juego abierto en el detalle.
 * @property profileRecommendation Recomendación del AutoOptimizer para ese juego.
 * @property customParameters Parámetros derivados para el perfil
 *   "Auto Personalizado" en este dispositivo.
 * @property controlLayout Disposición del HUD táctil del juego abierto (Fase 5).
 * @property controlEditorGameId Juego cuyo HUD se está editando; `null` fuera
 *   del editor. La navegación sigue siendo estado: no hay grafo que mantener.
 * @property runningGameId Juego abierto en la pantalla de ejecución (Fase 6).
 *   `null` si no hay ninguna sesión de juego abierta.
 * @property showsGlobalSettings Si la pantalla de Ajustes Globales está visible
 *   (Fase 7).
 * @property showsDiagnostics Si la consola de diagnóstico está visible.
 * @property savesGameId Juego cuyo gestor de partidas/caché está abierto.
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
    val deviceInfo: DeviceInfo? = null,
    val profileHints: GameProfileHints? = null,
    val profileRecommendation: ProfileRecommendation? = null,
    val customParameters: PerformanceParameters? = null,
    val controlLayout: ControlLayout? = null,
    val controlEditorGameId: String? = null,
    val runningGameId: String? = null,
    val showsGlobalSettings: Boolean = false,
    val showsDiagnostics: Boolean = false,
    val savesGameId: String? = null,
)

/**
 * ViewModel de la pantalla principal.
 *
 * Orquesta los permisos, el escáner, la biblioteca y el AutoOptimizer. No
 * contiene lógica de negocio: la decisión de qué es un juego vive en
 * `GameScanner`, la biblioteca en `GameLibraryRepository` (respaldada por Room)
 * y la recomendación de perfil en `AutoOptimizer`.
 */
class GameScannerViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val database = NexoraDatabase.getInstance(application)
    private val repository = GameLibraryRepository(dao = database.gameDao())
    private val controlRepository = ControlLayoutRepository(dao = database.controlLayoutDao())
    private val accessController = StorageAccessController(application)
    private val deviceInfoProvider = DeviceInfoProvider(application)
    private val profileInspector = GameProfileInspector(application)
    private val autoOptimizer = AutoOptimizer()
    private val settings: AppSettings = AppSettings.DEFAULT

    private val mutableUiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = mutableUiState.asStateFlow()

    /** Observa el HUD del juego abierto. Se cancela al cerrar el detalle. */
    private var controlJob: Job? = null

    init {
        mutableUiState.update { it.copy(deviceInfo = deviceInfoProvider.getDeviceInfo()) }

        // Room re-emite la consulta ante cualquier cambio de la tabla: la UI se
        // refresca sola tras escanear, borrar o cambiar un icono o un perfil.
        viewModelScope.launch {
            repository.games.collect { games ->
                mutableUiState.update { state ->
                    val stillOpen = state.selectedGameId?.takeIf { id ->
                        games.any { it.id == id }
                    }
                    state.copy(
                        games = games,
                        // Un juego borrado no puede seguir abierto en detalle.
                        selectedGameId = stillOpen,
                        profileRecommendation = state.profileRecommendation.takeIf { stillOpen != null },
                        profileHints = state.profileHints.takeIf { stillOpen != null },
                        customParameters = state.customParameters.takeIf { stillOpen != null },
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

    /**
     * Abre la pantalla de detalle de un juego y calcula su recomendación.
     *
     * El cálculo va en segundo plano: inspeccionar la carpeta del juego implica
     * enumerar archivos, y eso no puede pasar en el hilo principal.
     */
    fun openGameDetail(gameId: String) {
        mutableUiState.update {
            it.copy(
                selectedGameId = gameId,
                profileHints = null,
                profileRecommendation = null,
                customParameters = null,
            )
        }
        observeControlLayout(gameId)
        viewModelScope.launch { recommendProfileFor(gameId) }
    }

    /**
     * Vuelve de la pantalla de detalle a la biblioteca.
     *
     * La disposición del HUD se guarda al salir, aunque el usuario no haya
     * pulsado "Guardar": perder el trabajo por cerrar la pantalla sería peor que
     * escribir una fila de más.
     */
    fun closeGameDetail() {
        saveControlLayout()
        controlJob?.cancel()
        controlJob = null
        mutableUiState.update {
            it.copy(
                selectedGameId = null,
                profileHints = null,
                profileRecommendation = null,
                customParameters = null,
                controlLayout = null,
                controlEditorGameId = null,
                // No tocamos runningGameId: si el juego se está ejecutando, no
                // se debe cerrar la pantalla de detalle y dejar la de ejecución
                // huérfana. openGameRunner ya abre la ejecución desde el detalle.
            )
        }
    }

    // --- Editor del HUD táctil ---------------------------------------------

    /** Abre el editor de la disposición táctil de un juego. */
    fun openControlEditor(gameId: String) {
        mutableUiState.update { it.copy(controlEditorGameId = gameId) }
    }

    /** Cierra el editor guardando la disposición. */
    fun closeControlEditor() {
        saveControlLayout()
        mutableUiState.update { it.copy(controlEditorGameId = null) }
    }

    /** Escribe en Room la disposición que hay en pantalla. */
    fun saveControlLayout() {
        val layout = mutableUiState.value.controlLayout ?: return
        viewModelScope.launch {
            controlRepository.save(layout)
            mutableUiState.update {
                it.copy(
                    infoMessage = getString(R.string.controls_saved),
                    errorMessage = null,
                )
            }
        }
    }

    /** Devuelve el HUD a la disposición de fábrica de NEXORA. */
    fun resetControlLayout() {
        val gameId = mutableUiState.value.controlLayout?.gameId ?: return
        viewModelScope.launch {
            controlRepository.reset(gameId)
            mutableUiState.update {
                it.copy(
                    infoMessage = getString(R.string.controls_reset_done),
                    errorMessage = null,
                )
            }
        }
    }

    /**
     * Mueve un botón durante el arrastre.
     *
     * Solo cambia el estado en memoria: arrastrar dispara decenas de eventos por
     * segundo y no se va a escribir en base de datos en cada uno. El guardado
     * llega al soltar el botón ("Guardar" o al salir del editor).
     */
    fun moveControl(action: ControlAction, deltaX: Float, deltaY: Float) {
        updateControl(action) { control ->
            control.copy(
                centerX = control.centerX + deltaX,
                centerY = control.centerY + deltaY,
            ).clamped()
        }
    }

    /** Cambia el tamaño del botón seleccionado. */
    fun resizeControl(action: ControlAction, size: Float) {
        updateControl(action) { control -> control.copy(size = size).clamped() }
    }

    /** Muestra u oculta un botón del HUD. */
    fun toggleControlVisibility(action: ControlAction) {
        updateControl(action) { control -> control.copy(visible = !control.visible) }
    }

    /** Transparencia de la capa flotante, de 0 a 1. */
    fun setOverlayOpacity(opacity: Float) {
        mutableUiState.update { state ->
            val layout = state.controlLayout ?: return@update state
            state.copy(controlLayout = layout.copy(overlayOpacity = opacity.coerceIn(0f, 1f)))
        }
    }

    /** Activa o desactiva el recuadro de selección del arrastre con un dedo. */
    fun setDragBoxEnabled(enabled: Boolean) {
        mutableUiState.update { state ->
            val layout = state.controlLayout ?: return@update state
            state.copy(controlLayout = layout.copy(dragBoxEnabled = enabled))
        }
    }

    private fun updateControl(action: ControlAction, transform: (HudControl) -> HudControl) {
        mutableUiState.update { state ->
            val layout = state.controlLayout ?: return@update state
            val control = layout.controlFor(action) ?: return@update state
            state.copy(controlLayout = layout.withControl(transform(control)))
        }
    }

    /**
     * Observa la disposición del HUD de un juego.
     *
     * Un juego recién escaneado no tiene filas: el repositorio devuelve la
     * disposición predeterminada, así que el editor siempre tiene algo que
     * dibujar y la primera pulsación de "Guardar" crea las filas.
     */
    private fun observeControlLayout(gameId: String) {
        controlJob?.cancel()
        controlJob = viewModelScope.launch {
            controlRepository.observeLayout(gameId).collect { layout ->
                mutableUiState.update { state ->
                    if (state.selectedGameId != gameId) return@update state
                    state.copy(controlLayout = layout)
                }
            }
        }
    }

    // --- Acciones de la biblioteca -----------------------------------------

    /**
     * Botón "Jugar".
     *
     * Fase 6: el runtime de ejecución ya está integrado. Abrir el juego lanza
     * `GameRunnerScreen`, que monta el compositor del entorno y el motor.
     * El botón en la tarjeta de la biblioteca hace lo mismo: ambos llaman a
     * [openGameRunner].
     */
    fun requestPlay(game: Game) {
        openGameRunner(game.id)
    }

    /** Abre la pantalla de ejecución del juego. */
    fun openGameRunner(gameId: String) {
        mutableUiState.update {
            it.copy(
                selectedGameId = gameId,
                runningGameId = gameId,
            )
        }
    }

    /**
     * Cierra la pantalla de ejecución y limpia el estado de la sesión.
     *
     * El apagado real del motor lo hace `RuntimeViewModel.onCleared` cuando
     * la pantalla sale de la composición. Aquí limpiamos solo el flag de
     * ejecución: si el detalle estaba abierto (que es lo normal), la
     * navegación vuelve a él; si no, vuelve a la biblioteca.
     */
    fun closeGameRunner() {
        mutableUiState.update {
            it.copy(runningGameId = null)
        }
    }

    // --- Fase 7: pantallas auxiliares --------------------------------------

    /** Abre la pantalla de Ajustes Globales. */
    fun openGlobalSettings() {
        mutableUiState.update {
            it.copy(showsGlobalSettings = true)
        }
    }

    /** Cierra la pantalla de Ajustes Globales. */
    fun closeGlobalSettings() {
        mutableUiState.update {
            it.copy(showsGlobalSettings = false)
        }
    }

    /** Abre la consola de diagnóstico. */
    fun openDiagnostics() {
        mutableUiState.update {
            it.copy(showsDiagnostics = true)
        }
    }

    /** Cierra la consola de diagnóstico. */
    fun closeDiagnostics() {
        mutableUiState.update {
            it.copy(showsDiagnostics = false)
        }
    }

    /** Abre el gestor de partidas y caché del juego. */
    fun openSavesManager(gameId: String) {
        mutableUiState.update {
            it.copy(savesGameId = gameId)
        }
    }

    /** Cierra el gestor de partidas y caché. */
    fun closeSavesManager() {
        mutableUiState.update {
            it.copy(savesGameId = null)
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

    /**
     * Fija el perfil de rendimiento de un juego.
     *
     * Se persiste en Room, así que la elección sobrevive al reinicio.
     *
     * @param profile Perfil elegido, o `null` para devolver la decisión al
     *   AutoOptimizer.
     */
    fun selectProfile(gameId: String, profile: PerformanceProfile?) {
        viewModelScope.launch {
            repository.setProfile(gameId, profile)
            mutableUiState.update {
                it.copy(
                    infoMessage = if (profile == null) {
                        getString(R.string.profile_reset_auto)
                    } else {
                        getString(R.string.profile_applied, profile.technicalName)
                    },
                    errorMessage = null,
                )
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
            if (mutableUiState.value.selectedGameId == gameId) {
                controlJob?.cancel()
                controlJob = null
            }
            mutableUiState.update {
                it.copy(
                    selectedGameId = it.selectedGameId?.takeUnless { id -> id == gameId },
                    profileHints = null,
                    profileRecommendation = null,
                    customParameters = null,
                    controlLayout = it.controlLayout?.takeUnless { it.gameId == gameId },
                    controlEditorGameId = null,
                    runningGameId = it.runningGameId?.takeUnless { id -> id == gameId },
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

    /**
     * Calcula la recomendación del AutoOptimizer para un juego.
     *
     * Se vuelve a leer el dispositivo en este momento (la memoria disponible
     * cambia) y se inspecciona la carpeta del juego para saber qué API gráfica
     * pide; con ambas cosas, el optimizador puntúa los perfiles.
     */
    private suspend fun recommendProfileFor(gameId: String) {
        val game = repository.gameById(gameId) ?: return
        val device = deviceInfoProvider.getDeviceInfo()
        val hints = profileInspector.inspect(
            title = game.name,
            installDirectory = game.installDirectory,
            sizeBytes = game.sizeBytes,
            documentUri = game.documentUri,
        )
        val recommendation = autoOptimizer.recommend(device, hints)
        val customParameters = autoOptimizer.parameters(
            profile = PerformanceProfile.AUTO_CUSTOM,
            device = device,
            hints = hints,
        )

        mutableUiState.update { state ->
            // Si el usuario ya cerró el detalle, la recomendación llega tarde.
            if (state.selectedGameId != gameId) return@update state
            state.copy(
                deviceInfo = device,
                profileHints = hints,
                profileRecommendation = recommendation,
                customParameters = customParameters,
            )
        }
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
