package com.nexora.android.runtime.engine

import com.nexora.android.runtime.env.ComposedEnvironment
import com.nexora.android.runtime.model.RuntimeLog
import com.nexora.android.runtime.model.RuntimeStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Orquestador del proceso de ejecución de un juego.
 *
 * Es la pieza que une:
 * - el [ProcessLauncher] que sabe cómo arrancar un `.exe`,
 * - el [LogStreamer] que convierte su salida en un `Flow`,
 * - el estado [RuntimeStatus] que la UI pinta.
 *
 * ### Ciclo de vida
 * 1. **Preparar**: `prepare(env, ensurePrefix)` deja el prefijo listo y el
 *    estado en `Preparing`. No arranca nada todavía.
 * 2. **Lanzar**: `launch()` ejecuta el comando y mueve el estado a
 *    `Running(pid, …)`.
 * 3. **Pausar / Reanudar**: cambia el estado a `Paused` y vuelta a
 *    `Running`. Como se documenta en [RuntimeStatus.Paused], en Android
 *    esto **no suspende** el proceso: detiene el envío de input y los logs
 *    activos, pero el binario sigue corriendo.
 * 4. **kill**: cierra el proceso y deja el motor en `Stopped`.
 *
 * El motor **es singleton por sesión de juego**: una vez que un juego se
 * lanza, no se vuelve a llamar a `launch()` sobre el mismo motor.
 */
class RuntimeEngine(
    private val launcher: ProcessLauncher,
    private val streamerFactory: () -> LogStreamer = { LogStreamer() },
    private val now: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    private val _status = MutableStateFlow<RuntimeStatus>(RuntimeStatus.Idle)
    val status: StateFlow<RuntimeStatus> = _status.asStateFlow()

    private var currentHandle: ProcessHandle? = null
    private var streamer: LogStreamer? = null
    private var logJob: Job? = null

    /**
     * Resultado de preparar el entorno: estado actualizado y `warnings` que
     * la UI debe mostrar antes de lanzar.
     */
    fun prepare(
        env: ComposedEnvironment,
        ensurePrefix: () -> Unit,
    ): Preparation {
        ensurePrefix()
        _status.value = RuntimeStatus.Preparing(stage = "Entorno listo")
        return Preparation(
            environment = env,
            warnings = env.warnings,
        )
    }

    /**
     * Arranca el proceso y conecta los logs al `Flow` actual.
     *
     * @throws BinaryNotInstalledException si el binario no está disponible.
     */
    fun launch(env: ComposedEnvironment): ProcessHandle {
        check(_status.value is RuntimeStatus.Idle ||
            _status.value is RuntimeStatus.Preparing ||
            _status.value is RuntimeStatus.Stopped
        ) {
            "RuntimeEngine.launch(): el motor ya está activo (${_status.value})"
        }
        _status.value = RuntimeStatus.Preparing(stage = "Lanzando")
        val handle = try {
            launcher.launch(
                command = env.command,
                environment = env.environment,
                workingDirectory = null,
            )
        } catch (error: Exception) {
            _status.value = RuntimeStatus.Failed(
                reason = error.message ?: error.javaClass.simpleName,
                atMs = now(),
            )
            throw error
        }

        currentHandle = handle
        attachLogs(handle)
        _status.value = RuntimeStatus.Running(
            pid = handle.pid,
            commandLine = env.command,
            startedAtMs = now(),
        )
        return handle
    }

    /** `true` si el motor está en un estado que acepta `pause()`. */
    val canPause: Boolean get() = _status.value is RuntimeStatus.Running

    /** `true` si el motor está en un estado que acepta `resume()`. */
    val canResume: Boolean get() = _status.value is RuntimeStatus.Paused

    /** `true` si el motor está en un estado que acepta `kill()`. */
    val canKill: Boolean get() = _status.value is RuntimeStatus.Running ||
        _status.value is RuntimeStatus.Paused

    /**
     * Pausa la sesión.
     *
     * En Android no podemos suspender el proceso desde la JVM; lo que sí
     * paramos es el envío de input y la captura activa de logs. El proceso
     * sigue corriendo en segundo plano.
     */
    fun pause() {
        val current = _status.value
        if (current is RuntimeStatus.Running) {
            _status.value = RuntimeStatus.Paused(current.pid, sinceMs = now())
        }
    }

    /** Reactiva la sesión tras una pausa. */
    fun resume() {
        val current = _status.value
        if (current is RuntimeStatus.Paused) {
            _status.value = RuntimeStatus.Running(
                pid = current.pid,
                commandLine = listOf(),
                startedAtMs = current.sinceMs,
            )
        }
    }

    /**
     * Mata el proceso. Es seguro aunque el motor esté en cualquier estado:
     * en `Idle` y `Preparing` no hace nada.
     */
    fun kill() {
        val current = _status.value
        val handle = currentHandle
        if (handle != null) {
            handle.kill()
            val code = handle.waitFor()
            _status.value = RuntimeStatus.Stopped(exitCode = code, finishedAtMs = now())
            currentHandle = null
            logJob?.cancel()
            logJob = null
            streamer?.stop()
            streamer = null
        } else if (current is RuntimeStatus.Preparing) {
            _status.value = RuntimeStatus.Idle
        }
    }

    /** Flujo de logs del proceso actual, `null` si no hay ninguno en marcha. */
    fun logs(): Flow<RuntimeLog>? {
        val handle = currentHandle ?: return null
        return streamer?.stream(handle)
    }

    private fun attachLogs(handle: ProcessHandle) {
        streamer = streamerFactory().also { it.stream(handle) }
    }

    /**
     * Lo que devolvió `prepare()`: el entorno ya compuesto y los avisos a
     * enseñar antes de pulsar "Lanzar".
     */
    data class Preparation(
        val environment: ComposedEnvironment,
        val warnings: List<String>,
    )
}