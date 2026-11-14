package com.nexora.android.ui.runner

import android.app.Application
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.android.control.repository.ControlLayoutRepository
import com.nexora.android.engine.input.InputEvent
import com.nexora.android.engine.profile.AutoOptimizer
import com.nexora.android.engine.profile.PerformanceParameters
import com.nexora.android.engine.profile.PerformanceProfile
import com.nexora.android.engine.profile.ProfileRecommendation
import com.nexora.android.game.data.local.NexoraDatabase
import com.nexora.android.game.model.Game
import com.nexora.android.game.repository.GameLibraryRepository
import com.nexora.android.runtime.audio.AudioPipeline
import com.nexora.android.runtime.audio.AudioSettings
import com.nexora.android.runtime.audio.FifoPcmSource
import com.nexora.android.runtime.binary.FilesystemBinaryResolver
import com.nexora.android.runtime.binary.RuntimeBinaryLoader
import com.nexora.android.runtime.binary.RuntimeLayout
import com.nexora.android.runtime.diagnostics.DiagnosticsLogger
import com.nexora.android.runtime.engine.AndroidProcessLauncher
import com.nexora.android.runtime.engine.ProcessExecutionEngine
import com.nexora.android.runtime.engine.RuntimeEngine
import com.nexora.android.runtime.env.ComposedEnvironment
import com.nexora.android.runtime.env.EnvironmentComposer
import com.nexora.android.runtime.env.EnvironmentSettings
import com.nexora.android.runtime.env.WinePrefix
import com.nexora.android.runtime.input.EvdevInputTransport
import com.nexora.android.runtime.input.InputBridge
import com.nexora.android.runtime.input.ViewportMapping
import com.nexora.android.runtime.diagnostics.SessionLogRecorder
import com.nexora.android.runtime.overlay.DxvkHudParser
import com.nexora.android.runtime.overlay.OverlayCommand
import com.nexora.android.runtime.overlay.OverlayController
import com.nexora.android.runtime.overlay.OverlayMetricsBus
import com.nexora.android.runtime.monitor.AndroidRuntimeMonitorSource
import com.nexora.android.runtime.monitor.RuntimeMonitorSnapshot
import com.nexora.android.runtime.monitor.RuntimeMonitorSource
import com.nexora.android.runtime.model.RuntimeLog
import com.nexora.android.runtime.model.RuntimeStatus
import com.nexora.android.runtime.repository.AudioSettingsRepository
import com.nexora.android.runtime.repository.RuntimeConfigRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Estado de la pantalla de ejecución (`GameRunnerScreen`).
 *
 * Es una única data class inmutable: la pantalla es una función pura de
 * este estado, lo que la hace previewable y testeable. La complejidad del
 * motor (preparación, pausa, logs) se traduce aquí en un campo.
 */
data class RuntimeUiState(
    val game: Game? = null,
    val settings: EnvironmentSettings = EnvironmentSettings.DEFAULT,
    val audioSettings: AudioSettings = AudioSettings.DEFAULT,
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
    /**
     * `true` cuando la pantalla está en modo juego: barras del sistema
     * ocultas, superficie al 100% y panel de gestión fuera de la vista.
     * Se activa al lanzar y se alterna con el botón flotante.
     */
    val fullscreen: Boolean = false,
    val runtime: RuntimeBinaryUiState = RuntimeBinaryUiState(),
    val input: InputBridgeUiState = InputBridgeUiState(),
    val audio: AudioBridgeUiState = AudioBridgeUiState(),
    val overlay: OverlayUiState = OverlayUiState(),
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
 * Fase 5 (controles), Fase 6 (entorno) y Fase 7 (audio) con el
 * [RuntimeEngine] y el monitor técnico.
 */
class RuntimeViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val database = NexoraDatabase.getInstance(application)
    private val gameRepository = GameLibraryRepository(database.gameDao())
    private val configRepository = RuntimeConfigRepository(database.runtimeConfigDao())
    private val audioRepository = AudioSettingsRepository(database.audioSettingsDao())
    private val controlRepository = ControlLayoutRepository(database.controlLayoutDao())
    private val composer = EnvironmentComposer()
    private val autoOptimizer = AutoOptimizer()

    /** Instalador del runtime nativo (Fase 8). */
    private val binaryLoader = RuntimeBinaryLoader(application.applicationContext)

    /** Layout instalado; `null` hasta la primera comprobación. */
    @Volatile
    private var layout: RuntimeLayout? = null

    /**
     * Motor de ejecución real: resuelve binarios, arma la línea de comandos y
     * completa `LD_LIBRARY_PATH`/`PATH` antes de delegar en `ProcessBuilder`.
     */
    private val executionEngine = ProcessExecutionEngine(
        inner = AndroidProcessLauncher(
            binaryResolver = FilesystemBinaryResolver(layoutProvider = { layout }),
        ),
        layoutProvider = { layout },
        abis = Build.SUPPORTED_ABIS.toList(),
        tmpDirProvider = {
            File(application.filesDir, "tmp").apply { mkdirs() }.absolutePath
        },
    )

    private val engine = RuntimeEngine(launcher = executionEngine)
    private val monitorSource: RuntimeMonitorSource =
        AndroidRuntimeMonitorSource(application.applicationContext)

    /** Puente táctil → evdev. `null` mientras no haya sesión o nodo. */
    private var inputBridge: InputBridge? = null
    private var inputTransport: EvdevInputTransport? = null

    /** Sumidero de audio real. */
    private val audioPipeline = AudioPipeline()

    /**
     * Registro de la sesión en disco (Fase 9).
     *
     * La Fase 8 volcaba la salida del motor a memoria; esto la escribe en
     * `filesDir/logs` para que sobreviva a un cierre inesperado.
     */
    private val logRecorder = SessionLogRecorder(
        logDir = File(application.filesDir, "logs"),
    )

    /** Tamaño de la superficie de juego, que la UI comunica en píxeles. */
    private var surfaceWidthPx: Float = 0f
    private var surfaceHeightPx: Float = 0f

    private val mutableUiState = MutableStateFlow(RuntimeUiState())
    val uiState: StateFlow<RuntimeUiState> = mutableUiState.asStateFlow()

    private var monitorJob: Job? = null
    private var logsJob: Job? = null
    private var configJob: Job? = null
    private var audioJob: Job? = null
    private var gameJob: Job? = null

    init {
        viewModelScope.launch {
            engine.status.collect { status ->
                mutableUiState.update { it.copy(status = status) }
                if (status is RuntimeStatus.Failed) {
                    DiagnosticsLogger.shared.record(
                        level = com.nexora.android.runtime.diagnostics.DiagnosticsLevel.ERROR,
                        source = "engine",
                        message = "Estado Failed: ${status.reason}",
                    )
                    stopBridges()
                }
                if (status is RuntimeStatus.Stopped) {
                    stopBridges()
                }
                if (status is RuntimeStatus.Running) {
                    OverlayMetricsBus.publishSessionStarted(status.pid)
                    mutableUiState.update {
                        it.copy(input = it.input.copy(enabled = inputBridge?.enabled == true))
                    }
                }
                if (status is RuntimeStatus.Stopped || status is RuntimeStatus.Failed) {
                    OverlayMetricsBus.publishSessionStopped()
                }
            }
        }
        viewModelScope.launch {
            OverlayMetricsBus.commands.collect { command ->
                when (command) {
                    OverlayCommand.EmergencyKill -> {
                        DiagnosticsLogger.shared.record(
                            level = com.nexora.android.runtime.diagnostics.DiagnosticsLevel.WARN,
                            source = "overlay",
                            message = "Cierre de emergencia solicitado desde el HUD flotante",
                        )
                        logRecorder.appendLine("# cierre de emergencia solicitado desde el HUD")
                        kill()
                    }
                    OverlayCommand.HudDismissed -> {
                        mutableUiState.update {
                            it.copy(overlay = it.overlay.copy(running = false))
                        }
                    }
                }
            }
        }
        refreshOverlayPermission()
        installRuntime()
    }

    /**
     * Carga el juego solicitado y prepara todas las observaciones.
     */
    fun loadGame(gameId: String) {
        viewModelScope.launch {
            val game = gameRepository.gameById(gameId) ?: return@launch
            mutableUiState.update { it.copy(game = game) }
            observeConfiguration(gameId)
            observeAudio(gameId)
            observeLogs()
            gameJob = viewModelScope.launch { observeGame(gameId) }
            computeRecommendation(game)
            DiagnosticsLogger.shared.record(
                level = com.nexora.android.runtime.diagnostics.DiagnosticsLevel.INFO,
                source = "app",
                message = "Cargando ${game.name} (${gameId})",
            )
        }
    }

    /**
     * Instala (o comprueba) el runtime nativo en `filesDir/runtime`.
     *
     * Se llama al abrir la pantalla y cuando el usuario pulsa "Reinstalar".
     * Nunca lanza: el resultado —incluidos los componentes que no venían en el
     * APK— se refleja en [RuntimeUiState.runtime].
     */
    fun installRuntime(force: Boolean = false) {
        mutableUiState.update {
            it.copy(runtime = it.runtime.copy(installing = true, error = null))
        }
        viewModelScope.launch {
            val outcome = withContext(kotlinx.coroutines.Dispatchers.IO) {
                binaryLoader.ensureInstalled(force)
            }
            layout = outcome.layout

            val declared = outcome.manifest.entries.map { it.component }.distinct()
            val missing = declared
                .filter { !outcome.layout.has(it) }
                .map { it.label }

            mutableUiState.update {
                it.copy(
                    runtime = RuntimeBinaryUiState(
                        checked = true,
                        installing = false,
                        version = outcome.layout.version,
                        components = outcome.layout.installedComponents.map { component -> component.label },
                        missing = missing,
                        canLaunch = outcome.layout.canLaunch,
                        message = outcome.message,
                        error = outcome.report?.failures?.firstOrNull()?.reason,
                    ),
                )
            }
            DiagnosticsLogger.shared.record(
                level = if (outcome.layout.canLaunch) {
                    com.nexora.android.runtime.diagnostics.DiagnosticsLevel.INFO
                } else {
                    com.nexora.android.runtime.diagnostics.DiagnosticsLevel.WARN
                },
                source = "runtime",
                message = "${outcome.message} · ${outcome.report?.summary() ?: "sello al día"}",
            )
        }
    }

    /**
     * La UI comunica el tamaño real de la superficie de juego (en píxeles) para
     * que el puente táctil pueda escalar a coordenadas del escritorio virtual.
     */
    fun updateViewport(widthPx: Float, heightPx: Float) {
        surfaceWidthPx = widthPx
        surfaceHeightPx = heightPx
        inputBridge?.mapping = buildMapping()
    }

    /** Envía un gesto del HUD al puente real. Silencioso si no hay sesión. */
    fun dispatchInput(event: InputEvent) {
        val bridge = inputBridge ?: return
        bridge.dispatch(event)
        mutableUiState.update {
            it.copy(
                input = it.input.copy(
                    sentEvents = bridge.sentEvents,
                    lastError = bridge.lastError,
                ),
            )
        }
    }

    /** Permite silenciar la inyección sin cerrar la sesión. */
    fun setInputEnabled(enabled: Boolean) {
        val bridge = inputBridge
        if (bridge == null) {
            mutableUiState.update { it.copy(input = it.input.copy(enabled = false)) }
            return
        }
        bridge.enabled = enabled
        mutableUiState.update { it.copy(input = it.input.copy(enabled = enabled)) }
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

    /** Relee si la app puede dibujar sobre otras aplicaciones (Fase 9). */
    fun refreshOverlayPermission() {
        val canDraw = OverlayController.canDrawOverlays(getApplication())
        mutableUiState.update { it.copy(overlay = it.overlay.copy(canDraw = canDraw)) }
    }

    /** Lleva al ajuste del sistema para conceder "Aparecer encima de otras apps". */
    fun overlayPermissionIntent(): android.content.Intent =
        OverlayController.permissionIntent(getApplication())

    /**
     * Arranca el HUD flotante.
     *
     * Sin permiso de superposición no se arranca nada y se explica por qué:
     * un botón que dice "activado" sin ventana visible es peor que un error.
     */
    fun startOverlay() {
        val state = mutableUiState.value
        if (!OverlayController.canDrawOverlays(getApplication())) {
            mutableUiState.update {
                it.copy(
                    overlay = it.overlay.copy(
                        canDraw = false,
                        running = false,
                        error = "Falta el permiso «Aparecer encima de otras apps»",
                    ),
                )
            }
            return
        }
        val started = runCatching {
            OverlayController.start(getApplication(), state.game?.name.orEmpty())
        }.getOrDefault(false)

        mutableUiState.update {
            it.copy(
                overlay = it.overlay.copy(
                    canDraw = true,
                    running = started,
                    error = if (started) null else "El sistema no dejó abrir la ventana flotante",
                ),
            )
        }
        DiagnosticsLogger.shared.record(
            level = if (started) {
                com.nexora.android.runtime.diagnostics.DiagnosticsLevel.INFO
            } else {
                com.nexora.android.runtime.diagnostics.DiagnosticsLevel.WARN
            },
            source = "overlay",
            message = if (started) "HUD flotante iniciado" else "HUD flotante no pudo iniciarse",
        )
    }

    /** Detiene el HUD flotante. */
    fun stopOverlay() {
        runCatching { OverlayController.stop(getApplication()) }
        mutableUiState.update {
            it.copy(overlay = it.overlay.copy(running = false, error = null))
        }
    }

    /** Alterna el HUD flotante. */
    fun toggleOverlay() {
        if (mutableUiState.value.overlay.running) stopOverlay() else startOverlay()
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
            audioSettings = state.audioSettings,
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
            val runtimeWarning = if (layout?.canLaunch != true) {
                listOf(
                    "No hay binarios de Wine instalados en filesDir/runtime: el motor " +
                        "fallará con BinaryNotInstalledException. Copia el runtime en " +
                        "assets/runtime/ (ver manifest.txt) y pulsa Reinstalar.",
                )
            } else {
                emptyList()
            }
            mutableUiState.update {
                it.copy(
                    composed = preparation.environment,
                    warnings = preparation.warnings + runtimeWarning,
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
        // El registro se abre ANTES de lanzar: si el proceso muere en el
        // primer segundo, las líneas que importan son justamente esas.
        logRecorder.start(
            gameId = mutableUiState.value.game?.id ?: "sesion",
            header = listOf(
                "Comando: ${composed.command.joinToString(" ")}",
                "Prefijo: ${composed.winePrefixPath}",
                "Perfil: ${mutableUiState.value.activeProfileName}",
            ),
        )
        try {
            engine.launch(env = composed)
            // Al lanzar, la pantalla pasa a modo juego: el área del juego es lo
            // único que importa y los botones de gestión estorban. Si el
            // lanzamiento falla, se queda en modo normal para que el error se
            // vea.
            mutableUiState.update { it.copy(fullscreen = true) }
            // Los puentes se abren solo si el proceso llegó a arrancar: un
            // AudioTrack o un nodo evdev abiertos sin motor son recursos
            // ocupados para nada.
            startAudio(composed.winePrefixPath)
            startInput()
        } catch (error: Throwable) {
            logRecorder.appendLine("# no se pudo lanzar: ${error.message ?: error.javaClass.simpleName}")
            mutableUiState.update {
                it.copy(errorMessage = error.message ?: error.javaClass.simpleName)
            }
        }
    }

    /** Alterna entre modo normal y modo juego (pantalla completa). */
    fun setFullscreen(value: Boolean) {
        mutableUiState.update { it.copy(fullscreen = value) }
    }

    fun pause() = engine.pause()

    fun resume() = engine.resume()

    fun kill() {
        engine.kill()
        stopMonitor()
        logRecorder.appendLine("# proceso terminado por el usuario")
        OverlayMetricsBus.publishSessionStopped()
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

    /** Persiste los ajustes de audio del juego. */
    fun saveAudioSettings(settings: AudioSettings) {
        val gameId = mutableUiState.value.game?.id ?: return
        viewModelScope.launch {
            audioRepository.save(gameId, settings)
            mutableUiState.update {
                it.copy(
                    infoMessage = "Ajustes de audio guardados.",
                    errorMessage = null,
                )
            }
            prepareEnvironment()
        }
    }

    /** Restablece los ajustes de audio a los valores por defecto. */
    fun resetAudioSettings() {
        val gameId = mutableUiState.value.game?.id ?: return
        viewModelScope.launch {
            audioRepository.reset(gameId)
            prepareEnvironment()
        }
    }

    /** Cierra el motor y libera todo lo que tenga un ciclo de vida. */
    fun shutdown() {
        // Al salir de la pantalla se vuelve al modo normal: si el ViewModel
        // sobrevive (navegación por estado), la próxima entrada no debe
        // aparecer en pantalla completa sin que el usuario lo pida.
        mutableUiState.update { it.copy(fullscreen = false) }
        stopBridges()
        monitorJob?.cancel()
        monitorJob = null
        logsJob?.cancel()
        logsJob = null
        configJob?.cancel()
        configJob = null
        audioJob?.cancel()
        audioJob = null
        gameJob?.cancel()
        gameJob = null
        if (engine.status.value is RuntimeStatus.Running ||
            engine.status.value is RuntimeStatus.Paused
        ) {
            engine.kill()
        }
        monitorSource.close()
        logRecorder.appendLine("# sesión cerrada")
        logRecorder.close()
        OverlayMetricsBus.publishSessionStopped()
        OverlayController.stop(getApplication())
        mutableUiState.update { it.copy(overlay = it.overlay.copy(running = false)) }
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

    private fun observeAudio(gameId: String) {
        audioJob?.cancel()
        audioJob = viewModelScope.launch {
            audioRepository.observe(gameId).collect { settings ->
                mutableUiState.update { it.copy(audioSettings = settings) }
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
                        logRecorder.append(log)
                        DxvkHudParser.parseFps(log.message)?.let { fps ->
                            OverlayMetricsBus.publishEngineFps(fps)
                        }
                        mutableUiState.update { state ->
                            val newLogs = (state.logs + log).takeLast(MAX_LOG_LINES)
                            state.copy(logs = newLogs)
                        }
                        DiagnosticsLogger.shared.record(
                            level = when (log.stream) {
                                com.nexora.android.runtime.model.LogStream.STDERR ->
                                    com.nexora.android.runtime.diagnostics.DiagnosticsLevel.WARN
                                else -> com.nexora.android.runtime.diagnostics.DiagnosticsLevel.INFO
                            },
                            source = "runtime",
                            message = log.message,
                        )
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

    /**
     * Abre el `AudioTrack` y conecta el FIFO de audio del prefijo.
     *
     * El FIFO lo crea el motor al arrancar el contenedor; hasta entonces el
     * pipeline queda en `WAITING` y reintenta. Si el dispositivo no soporta el
     * formato, `AudioBridgeUiState.state` pasa a `FAILED` y el juego sigue
     * ejecutándose sin sonido.
     */
    private fun startAudio(winePrefixPath: String) {
        val settings = mutableUiState.value.audioSettings
        val minBuffer = runCatching {
            AudioTrack.getMinBufferSize(
                settings.sampleRate.hz,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        }.getOrDefault(0)

        val source = FifoPcmSource(
            path = "$winePrefixPath/nexora-audio.fifo",
            sampleRateHz = settings.sampleRate.hz,
            channelCount = 2,
        )
        val started = audioPipeline.start(
            settings = settings,
            source = source,
            minBufferBytes = if (minBuffer > 0) minBuffer else 4096,
        )
        val stats = audioPipeline.stats()
        mutableUiState.update {
            it.copy(
                audio = AudioBridgeUiState(
                    state = if (started) audioPipeline.state.name else "FAILED",
                    sampleRateHz = settings.sampleRate.hz,
                    bufferFrames = stats?.bufferFrames ?: 0,
                    latencyMs = stats?.latencyMs ?: 0,
                    secondsPlayed = stats?.secondsPlayed ?: 0.0,
                    underruns = stats?.underruns ?: 0,
                    error = audioPipeline.lastError ?: stats?.lastError,
                ),
            )
        }
        DiagnosticsLogger.shared.record(
            level = if (started) {
                com.nexora.android.runtime.diagnostics.DiagnosticsLevel.INFO
            } else {
                com.nexora.android.runtime.diagnostics.DiagnosticsLevel.WARN
            },
            source = "audio",
            message = if (started) {
                "AudioTrack abierto a ${settings.sampleRate.hz} Hz (${stats?.latencyMs ?: 0} ms de búfer)"
            } else {
                "AudioTrack no arrancó: ${audioPipeline.lastError}"
            },
        )
    }

    /**
     * Abre el puente táctil contra el primer nodo `evdev` escribible.
     *
     * En un dispositivo sin root no habrá ninguno: se informa y el HUD sigue
     * funcionando en pantalla, pero no inyecta eventos. Fingir que se envía
     * sería peor que decirlo.
     */
    private fun startInput() {
        if (inputBridge != null) return
        val node = EvdevInputTransport.findWritableNode()
        if (node == null) {
            mutableUiState.update {
                it.copy(
                    input = InputBridgeUiState(
                        enabled = false,
                        transport = "sin nodo accesible",
                        lastError = "No hay /dev/uinput ni /dev/input/event* escribible: " +
                            "la inyección necesita un nodo de entrada accesible (root o helper).",
                    ),
                )
            }
            return
        }
        val transport = EvdevInputTransport(node)
        val bridge = InputBridge(mapping = buildMapping(), transport = transport, enabled = true)
        inputTransport = transport
        inputBridge = bridge
        mutableUiState.update {
            it.copy(
                input = InputBridgeUiState(
                    enabled = true,
                    transport = transport.description,
                ),
            )
        }
        DiagnosticsLogger.shared.record(
            level = com.nexora.android.runtime.diagnostics.DiagnosticsLevel.INFO,
            source = "input",
            message = "Puente táctil abierto en $node",
        )
    }

    /** Cierra audio y entrada; idempotente. */
    private fun stopBridges() {
        audioPipeline.stop()
        inputTransport?.close()
        inputTransport = null
        inputBridge = null
        mutableUiState.update {
            it.copy(
                input = it.input.copy(enabled = false),
                audio = it.audio.copy(state = "STOPPED"),
            )
        }
    }

    /**
     * Mapeo pantalla → escritorio virtual.
     *
     * El escritorio sale del perfil de rendimiento (Fase 4); si el perfil es
     * "nativa" (0 × 0) se usa el tamaño real de la superficie, que es lo que
     * el usuario está viendo.
     */
    private fun buildMapping(): ViewportMapping {
        val preset = mutableUiState.value.parameters.resolution
        val desktopWidth = if (preset.width > 0) preset.width else surfaceWidthPx.toInt().coerceAtLeast(1)
        val desktopHeight = if (preset.height > 0) preset.height else surfaceHeightPx.toInt().coerceAtLeast(1)
        return ViewportMapping(
            surfaceWidth = surfaceWidthPx.coerceAtLeast(1f),
            surfaceHeight = surfaceHeightPx.coerceAtLeast(1f),
            desktopWidth = desktopWidth,
            desktopHeight = desktopHeight,
        )
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