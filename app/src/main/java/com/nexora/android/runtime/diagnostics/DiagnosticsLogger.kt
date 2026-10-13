package com.nexora.android.runtime.diagnostics

import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Sink global de diagnóstico.
 *
 * Mantiene un **ring buffer acotado** (por defecto 1024 eventos) al que pueden
 * apuntar escritura tanto el motor de ejecución (vía [com.nexora.android.runtime.engine.LogStreamer])
 * como la UI (errores de ViewModel, crashes, eventos de navegación).
 *
 * Es seguro para concurrencia: usa `ConcurrentLinkedDeque` y protege la
 * política de tamaño con un lock. La idea es que siempre se puedan leer los
 * últimos N eventos sin bloquear al productor más que un instante.
 */
class DiagnosticsLogger(
    val capacity: Int = DEFAULT_CAPACITY,
) {

    private val deque = ConcurrentLinkedDeque<DiagnosticsEvent>()
    private val lock = Any()

    init {
        require(capacity > 0) { "capacity debe ser > 0" }
    }

    fun record(level: DiagnosticsLevel, source: String, message: String) {
        val event = DiagnosticsEvent(
            timestampMs = System.currentTimeMillis(),
            level = level,
            source = source,
            message = message,
        )
        synchronized(lock) {
            deque.addLast(event)
            while (deque.size > capacity) deque.pollFirst()
        }
    }

    /** Devuelve una instantánea inmutable de los eventos en orden cronológico. */
    fun snapshot(): List<DiagnosticsEvent> = synchronized(lock) {
        deque.toList()
    }

    /** Vacía el buffer. Pensado para tests; en producción rara vez se usa. */
    fun clear() = synchronized(lock) { deque.clear() }

    val size: Int get() = synchronized(lock) { deque.size }

    companion object {
        const val DEFAULT_CAPACITY = 1024

        /** Instancia compartida por la app. */
        val shared: DiagnosticsLogger by lazy { DiagnosticsLogger() }
    }
}