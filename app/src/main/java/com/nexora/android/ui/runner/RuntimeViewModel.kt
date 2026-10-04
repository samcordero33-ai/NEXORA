package com.nexora.android.ui.runner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.android.control.repository.ControlLayoutRepository
import com.nexora.android.engine.profile.AutoOptimizer
import com.nexora.android.engine.profile.PerformanceParameters
import com.nexora.android.engine.profile.PerformanceProfile
import com.nexora.android.engine.profile.ProfileRecommendation
import com.nexora.android.game.data.local.NexoraDatabase
import com.nexora.android.game.model.Game
import com.nexora.android.game.repository.GameLibraryRepository
import com.nexora.android.runtime.env.ComposedEnvironment
import com.nexora.android.runtime.env.EnvironmentComposer
import com.nexora.android.runtime.env.EnvironmentSettings
import com.nexora.android.runtime.env.WinePrefix
import com.nexora.android.runtime.monitor.AndroidRuntimeMonitorSource
import com.nexora.android.runtime.monitor.RuntimeMonitorSnapshot
import com.nexora.android.runtime.monitor.RuntimeMonitorSource
import com.nexora.android.runtime.model.RuntimeLog
import com.nexora.android.runtime.model.RuntimeStatus
import com.nexora.android.runtime.repository.RuntimeConfigRepository
import com.nexora.android.runtime.engine.AndroidProcessLauncher
import com.nexora.android.runtime.engine.ProcessHandle
import com.nexora.android.runtime.engine.RuntimeEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Estado de la pantalla de ejecución (`GameRunnerScreen`).
 *
 * Es una única data class inmutable: la pantalla es una función pura de
 * este estado, lo que la hace previewable y testeable. La complejidad del
 * motor (preparación, pausa, logs) se traduce aquí en un campo.
 *
 * @property game Juego en ejecución; `null` si el ViewModel aún no ha
 *   cargado nada.
 * @property settings Ajustes del entorno para este juego, observados de
 *   Room. Cambian en vivo.
 * @property parameters Parámetros efectivos del perfil (los del manual o los
 *   del Auto Personalizado, si el usuario eligió automático).
 * @property profileId Identificador del perfil activo, o `null` si decide
 *   el AutoOptimizer.
 * @property recommendation Recomendación del AutoOptimizer, si está
 *   calculada.
 * @property composed Resultado del compositor: variables, advertencias y
 *   comando. `null` antes de pulsar "Preparar entorno".
 * @property warnings Advertencias de la fase de preparación, mostradas al
 *   usuario antes del primer lanzamiento.
 * @property status Estado del [RuntimeEngine].
 * @property logs Logs del proceso (últimas N líneas; los anteriores se
 *   descartan).
 * @property monitor Monitor técnico (FPS, RAM, temperatura).
 * @property monitorVisible Si la UI está mostrando el monitor flotante.
 * @property hudVisible Si la UI está mostrando la capa de controles táctil.
 * @property infoMessage Mensaje informativo no bloqueante.
 * @property errorMessage Mensaje de error no bloqueante.
 */
data class RuntimeUiState(
    val game: Game? = null,
    val settings: EnvironmentSettings = EnvironmentSettings.DEFAULT,
    val parameters: PerformanceParameters = PerformanceProfile.MID_COMPATIBILITY.parameters,
    val profileId: String? = null,
    val recommendation: ProfileRecommendation? = null,
    val composed: ComposedEnvironment? = null,
    val warnings: List<String> = emptyList(),
    val status: RuntimeStatus = RuntimeStatus.Idle,
    val logs: List<RuntimeLog> = emptyList(),
    val monitor: RuntimeMonitorSnapshot = RuntimeMonitorSnapshot.EMPTY,
    val monitorVisible: Boolean = false,
    val hudVisible: Boolean = true,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
) {

    /** Nombre humano del perfil activo; "Automático" si decide el optimizador. */
    val activeProfileName: String
        get() = profileId?.let { id ->
            PerformanceProfile.fromId(id)?.technicalName
        } ?: PerformanceProfile.AUTO_CUSTOM.technicalName
}

/**
 * ViewModel de la pantalla de ejecución.
 *
 * Une los repositorios de la Fase 3 (biblioteca), Fase 4 (perfiles),
 * Fase 5 (controles) y Fase 6 (entorno) con el [RuntimeEngine] y el monitor
 * técnico.
 *
 * Es **deliberadamente no singleton**: cuando el usuario sale de la pantalla
 * se llama a [shutdown] y el motor libera el proceso. Volver a entrar crea
 * un ViewModel nuevo.
 */
class RuntimeViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val database = NexoraDatabase.getInstance(application)
    private val gameRepository = GameLibraryRepository(database.gameDao())
    private val configRepository = RuntimeConfigRepository(database.runtimeConfigDao())
    private val controlRepository = ControlLayoutRepository(database.controlLayoutDao())
    private val composer = EnvironmentComposer()
    private val autoOptimizer = AutoOptimizer()

    private val engine = RuntimeEngine(launcher = AndroidProcessLauncher())
    private val monitorSource: RuntimeMonitorSource =
        AndroidRuntimeMonitorSource(application.applicationContext)

    private val mutableUiState = MutableStateFlow(RuntimeUiState())
    val uiState: StateFlow<RuntimeUiState> = mutableUiState.asStateFlow()

    private var monitorJob: Job? = null
    private var logsJob: Job? = null
    private var configJob: Job? = null
    private var gameJob: Job? = null

    init {
        viewModelScope.launch {
            engine.status.collect { status ->
                mutableUiState.update { it.copy(status = status) }
            }
        }
    }

    /**
     * Carga el juego solicitado y prepara todas las observaciones.
     *
     * Se puede llamar más de una vez (al cambiar de juego), pero **antes**
     * de [shutdown].
     */
    fun loadGame(gameId: String) {
        viewModelScope.launch {
            val game = gameRepository.gameById(gameId) ?: return@launch
            mutableUiState.update { it.copy(game = game) }
            observeConfiguration(gameId)
            observeLogs()
            gameJob = viewModelScope.launch { observeGame(gameId) }
            computeRecommendation(game)
        }
    }

    /** Cambia el modo del monitor (FPS/RAM/temp). */
    fun setMonitorVisible(visible: Boolean) {
        mutableUiState.update { it.copy(monitorVisible = visible) }
        if (visible) startMonitor() else stopMonitor()
    }

    /** Muestra u oculta la capa de controles táctil sobre la superficie de juego. */
    fun setHudVisible(visible: Boolean) {
        mutableUiState.update { it.copy(hudVisible = visible) }
    }

    /** Prepara el entorno sin lanzar el proceso. */
    fun prepareEnvironment() {
        val state = mutableUiState.value
        val game = state.game ?: return
        val composed = composer.compose(
            gameId = game.id,
            exePath = game.executablePath,
            parameters = state.parameters,
            profileId = state.profileId ?: state.recommendation?.profile?.id ?: PerformanceProfile.AUTO_CUSTOM.id,
            settings = state.settings,
            prefixPathFor = EnvironmentComposer.prefixPathFor(getApplication<Application>().filesDir.absolutePath),
        )

        // Crea el prefijo en disco. Va en IO; el motor sigue en memoria.
        viewModelScope.launch {
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                WinePrefix.underFilesDir(
                    filesDir = getApplication<Application>().filesDir,
                    gameId = game.id,
                ).createIfMissing()
            }
            val preparation = engine.prepare(env = composed, ensurePrefix = {})
            mutableUiState.update {
                it.copy(
                    composed = preparation.environment,
                    warnings = preparation.warnings,
                    status = engine.status.value,
                )
            }
        }
    }

    /** Lanza el proceso. Si el binario no está, deja el estado en `Failed`. */
    fun launch() {
        val composed = mutableUiState.value.composed ?: run {
            prepareEnvironment()
            return
        }
        try {
            engine.launch(env = composed)
        } catch (error: Throwable) {
            mutableUiState.update {
                it.copy(errorMessage = error.message ?: error.javaClass.simpleName)
            }
        }
    }

    fun pause() = engine.pause()

    fun resume() = engine.resume()

    fun kill() {
        engine.kill()
        stopMonitor()
    }

    /** Persiste los ajustes de entorno modificados por el usuario. */
    fun saveSettings(settings: EnvironmentSettings) {
        val gameId = mutableUiState.value.game?.id ?: return
        viewModelScope.launch {
            configRepository.save(gameId, settings)
            mutableUiState.update {
                it.copy(
                    infoMessage = "Ajustes del entorno guardados.",
                    errorMessage = null,
                )
            }
            prepareEnvironment()
        }
    }

    /** Restablece los ajustes del entorno a los valores de fábrica. */
    fun resetSettings() {
        val gameId = mutableUiState.value.game?.id ?: return
        viewModelScope.launch {
            configRepository.reset(gameId)
            prepareEnvironment()
        }
    }

    /** Cierra el motor y libera todo lo que tenga un ciclo de vida. */
    fun shutdown() {
        monitorJob?.cancel()
        monitorJob = null
        logsJob?.cancel()
        logsJob = null
        configJob?.cancel()
        configJob = null
        gameJob?.cancel()
        gameJob = null
        if (engine.status.value is RuntimeStatus.Running ||
            engine.status.value is RuntimeStatus.Paused
        ) {
            engine.kill()
        }
        monitorSource.close()
    }

    /** Descarta los mensajes informativos / de error. */
    fun dismissMessages() {
        mutableUiState.update { it.copy(infoMessage = null, errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        shutdown()
    }

    private fun observeConfiguration(gameId: String) {
        configJob?.cancel()
        configJob = viewModelScope.launch {
            configRepository.observeSettings(gameId).collect { settings ->
                mutableUiState.update { it.copy(settings = settings) }
            }
        }
    }

    private fun observeLogs() {
        logsJob?.cancel()
        logsJob = viewModelScope.launch {
            while (isActive) {
                val flow = engine.logs()
                if (flow == null) {
                    delay(200L)
                } else {
                    flow.collect { log ->
                        mutableUiState.update { state ->
                            val newLogs = (state.logs + log).takeLast(MAX_LOG_LINES)
                            state.copy(logs = newLogs)
                        }
                    }
                }
            }
        }
    }

    private suspend fun observeGame(gameId: String) {
        gameRepository.observeGame(gameId).collect { game ->
            if (game == null) {
                mutableUiState.update { it.copy(game = null) }
                return@collect
            }
            mutableUiState.update { it.copy(game = game) }
        }
    }

    private suspend fun computeRecommendation(game: Game) {
        val device = com.nexora.android.engine.DeviceInfoProvider(getApplication())
            .getDeviceInfo()
        val hints = com.nexora.android.engine.profile.GameProfileInspector(getApplication())
            .inspect(
                title = game.name,
                installDirectory = game.installDirectory,
                sizeBytes = game.sizeBytes,
                documentUri = game.documentUri,
            )
        val recommendation = autoOptimizer.recommend(device, hints)
        val customParams = autoOptimizer.parameters(
            profile = PerformanceProfile.AUTO_CUSTOM,
            device = device,
            hints = hints,
        )

        val profileId = game.profile?.id
        val effectiveProfile = profileId?.let { PerformanceProfile.fromId(it) }
            ?: PerformanceProfile.AUTO_CUSTOM
        val effectiveParams = when (effectiveProfile) {
            PerformanceProfile.AUTO_CUSTOM -> customParams
            else -> effectiveProfile.parameters
        }

        mutableUiState.update {
            it.copy(
                recommendation = recommendation,
                profileId = profileId,
                parameters = effectiveParams,
            )
        }
    }

    private fun startMonitor() {
        if (monitorJob?.isActive == true) return
        (monitorSource as? AndroidRuntimeMonitorSource)?.start()
        monitorJob = viewModelScope.launch {
            while (isActive) {
                val nowNanos = System.nanoTime()
                val frame = monitorSource.sampleFrame(nowNanos)
                val resources = monitorSource.sampleResources()
                mutableUiState.update { state ->
                    state.copy(
                        monitor = RuntimeMonitorSnapshot(
                            fps = frame.fps,
                            frameTimeMs = frame.frameTimeMs,
                            usedRamBytes = resources.usedRamBytes,
                            totalRamBytes = resources.totalRamBytes,
                            cpuUsagePercent = resources.cpuUsagePercent,
                            cpuTempCelsius = resources.cpuTempCelsius,
                            gpuTempCelsius = resources.gpuTempCelsius,
                            elapsedMs = if (state.monitor.elapsedMs == 0L) 0L else state.monitor.elapsedMs + 1L,
                        ),
                    )
                }
                delay(500L)
            }
        }
    }

    private fun stopMonitor() {
        monitorJob?.cancel()
        monitorJob = null
        (monitorSource as? AndroidRuntimeMonitorSource)?.close()
        mutableUiState.update { it.copy(monitor = RuntimeMonitorSnapshot.EMPTY) }
    }

    companion object {
        /** Máximo de logs que la UI pinta. Más allá, se descartan los más antiguos. */
        const val MAX_LOG_LINES: Int = 400
    }
}