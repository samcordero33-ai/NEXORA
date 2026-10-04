package com.nexora.android.runtime.model

/**
 * Estados por los que pasa el motor de ejecución de NEXORA.
 *
 * Es un `sealed class` para que la UI pueda hacer un `when` exhaustivo y
 * representar cada estado con un composable dedicado: la pantalla de
 * ejecución es una **función pura** del estado, sin estados ocultos.
 */
sealed class RuntimeStatus {

    /** El motor no está haciendo nada: este es el estado inicial y el de tras `kill`. */
    data object Idle : RuntimeStatus()

    /** Se está preparando el entorno (creando el prefijo, escribiendo variables). */
    data class Preparing(val stage: String) : RuntimeStatus()

    /** El proceso del juego está en marcha. */
    data class Running(
        val pid: Long,
        val commandLine: List<String>,
        val startedAtMs: Long,
    ) : RuntimeStatus()

    /**
     * El usuario pidió pausar la sesión.
     *
     * En Android **no podemos suspender el proceso** desde la JVM: `Process`
     * no expone `SIGSTOP`. Aquí el "pausado" es un marcador de UI: el
     * proceso sigue vivo, lo que paramos es el envío de input y la captura
     * de logs activos. `kill()` sigue funcionando.
     */
    data class Paused(val pid: Long, val sinceMs: Long) : RuntimeStatus()

    /** El proceso terminó por sí solo. */
    data class Stopped(val exitCode: Int, val finishedAtMs: Long) : RuntimeStatus()

    /** El motor no pudo iniciar el proceso (binario ausente, error de E/S, …). */
    data class Failed(val reason: String, val atMs: Long) : RuntimeStatus()
}

/**
 * Descripción legible del estado, en español.
 *
 * No es para mostrar literalmente al usuario: es el texto corto que va en el
 * chip de estado de la pantalla de ejecución.
 */
val RuntimeStatus.label: String
    get() = when (this) {
        is RuntimeStatus.Idle -> "Inactivo"
        is RuntimeStatus.Preparing -> "Preparando: $stage"
        is RuntimeStatus.Running -> "En ejecución (pid $pid)"
        is RuntimeStatus.Paused -> "Pausado (pid $pid)"
        is RuntimeStatus.Stopped -> "Detenido (código $exitCode)"
        is RuntimeStatus.Failed -> "Error: $reason"
    }