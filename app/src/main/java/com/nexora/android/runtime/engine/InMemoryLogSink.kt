package com.nexora.android.runtime.engine

import com.nexora.android.runtime.model.LogStream
import com.nexora.android.runtime.model.RuntimeLog
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Recolector de logs en memoria, sin corrutinas ni `Flow`.
 *
 * Es la versión **simple** del LogStreamer, pensada para entornos donde no
 * se puede o no se quiere usar un canal con backpressure (tests JVM y
 * procesos cortos). Las líneas se meten en una cola acotada FIFO y se
 * pueden vaciar a demanda.
 */
class InMemoryLogSink(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val buffer = ConcurrentLinkedQueue<RuntimeLog>()

    /** Alimenta el sink con un flujo. Cierra al llegar a EOF. */
    fun accept(stream: InputStream, kind: LogStream) {
        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                add(RuntimeLog(now(), kind, line.trimEnd()))
            }
        }
    }

    private fun add(log: RuntimeLog) {
        buffer.add(log)
        while (buffer.size > capacity) buffer.poll()
    }

    /** Copia inmutable de los logs en orden cronológico. */
    fun snapshot(): List<RuntimeLog> = buffer.toList()

    /** Borra el contenido del sink. */
    fun clear() = buffer.clear()

    companion object {
        const val DEFAULT_CAPACITY = 256
    }
}