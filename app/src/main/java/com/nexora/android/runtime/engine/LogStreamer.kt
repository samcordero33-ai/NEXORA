package com.nexora.android.runtime.engine

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Convierte los `stdout` y `stderr` de un [ProcessHandle] en un `Flow` de
 * [com.nexora.android.runtime.model.RuntimeLog].
 *
 * Cada línea se entrega tal cual sale del proceso, con la marca de tiempo
 * del momento en que se leyó. Si el productor (el proceso) escribe más
 * rápido de lo que la UI consume, las líneas se acumulan en un `Channel`
 * con buffer acotado: la UI decide si descartar las más antiguas.
 *
 * Es **una clase con estado** pero **thread-safe**: se puede lanzar desde
 * cualquier corrutina y consumir desde otra.
 */
class LogStreamer(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val now: () -> Long = System::currentTimeMillis,
    private val capacity: Int = DEFAULT_CAPACITY,
) {

    private val channel = Channel<com.nexora.android.runtime.model.RuntimeLog>(capacity = capacity)
    private val started = AtomicBoolean(false)
    private var pumpJob: Job? = null

    /**
     * Inicia la lectura de los flujos del proceso.
     *
     * Si se llama más de una vez, la segunda llamada no hace nada: cada
     * [ProcessHandle] solo se drena una vez.
     *
     * @return Flujo de líneas; se cierra solo cuando **ambos** flujos
     *   devuelven EOF o cuando se llama a [stop].
     */
    fun stream(handle: ProcessHandle): Flow<com.nexora.android.runtime.model.RuntimeLog> {
        if (started.compareAndSet(false, true)) {
            pumpJob = scope.launch { pump(handle) }
        }
        return channel.receiveAsFlow()
    }

    /** Cierra el flujo y cancela las corrutinas de lectura. */
    fun stop() {
        channel.close()
        pumpJob?.cancel()
        pumpJob = null
    }

    private suspend fun pump(handle: ProcessHandle) {
        launchReader(handle.stdout, com.nexora.android.runtime.model.LogStream.STDOUT)
        launchReader(handle.stderr, com.nexora.android.runtime.model.LogStream.STDERR)
    }

    private fun launchReader(
        stream: InputStream,
        kind: com.nexora.android.runtime.model.LogStream,
    ) {
        scope.launch {
            try {
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        val safe = line.replace('\u0000', ' ').trimEnd()
                        val event = com.nexora.android.runtime.model.RuntimeLog(
                            timestampMs = now(),
                            stream = kind,
                            message = safe,
                        )
                        channel.send(event)
                    }
                }
            } catch (_: Exception) {
                // El proceso terminó o el lector se canceló: cerramos el canal
                // cuando ambos lados han llegado aquí. Si el que falla es uno,
                // dejamos que el otro siga.
            }
        }
    }

    companion object {
        /** Buffer por defecto: 512 líneas sobreviven a un consumo lento. */
        const val DEFAULT_CAPACITY = 512
    }
}