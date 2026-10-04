package com.nexora.android.runtime.engine

import java.io.InputStream

/**
 * Abstracción del proceso hijo del juego.
 *
 * Existe para que el motor se pueda probar en JVM sin Android: en producción
 * es un envoltorio sobre `java.lang.Process`; en pruebas es un proceso
 * simulado que escribe líneas a stdout/stderr con un retardo y termina.
 *
 * Los flujos `stdout` y `stderr` se devuelven ya en **modo bloqueante** y
 * son seguros para que `LogStreamer` los lea con un `BufferedReader`.
 */
interface ProcessHandle {

    /** PID del proceso; `-1L` si la plataforma no lo expone. */
    val pid: Long

    /** `true` mientras el sistema operativo lo considere vivo. */
    val isAlive: Boolean

    /** Flujo stdout del proceso. */
    val stdout: InputStream

    /** Flujo stderr del proceso. */
    val stderr: InputStream

    /**
     * Mata el proceso de forma dura.
     *
     * En Android se traduce a `Process.destroy()`; en la JVM completa se
     * traduce a `Process.destroyForcibly()`. Es seguro llamarlo más de una
     * vez.
     */
    fun kill()

    /** Espera al final del proceso y devuelve el código de salida. */
    fun waitFor(): Int
}

/**
 * Lanzador de procesos: la pieza que sabe cómo arrancar un `.exe` (o un
 * binario traductor como `box64`).
 *
 * Hay dos implementaciones:
 * - [AndroidProcessLauncher]: usa `ProcessBuilder` y resuelve el binario
 *   mediante un [BinaryResolver].
 * - [NoopProcessLauncher]: solo para pruebas; no lanza nada real.
 */
interface ProcessLauncher {

    /**
     * Intenta lanzar el proceso.
     *
     * @throws BinaryNotInstalledException si el resolver no encuentra un
     *   binario para el primer argumento del comando (por ejemplo,
     *   `wine` o `box64` no están en el dispositivo).
     */
    fun launch(
        command: List<String>,
        environment: Map<String, String>,
        workingDirectory: String?,
    ): ProcessHandle
}

/** Lanza una excepción cuando el binario solicitado no existe en el dispositivo. */
class BinaryNotInstalledException(name: String) :
    RuntimeException("No se encontró un binario capaz de ejecutar «$name».")