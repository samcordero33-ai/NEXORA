package com.nexora.android.runtime.engine

import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Implementación real del [ProcessLauncher] que usa `java.lang.ProcessBuilder`.
 *
 * La estrategia en Android es:
 * 1. Resolver el primer argumento del comando con un [BinaryResolver]. Esto
 *    deja abierta la puerta a que un módulo NDK o un connector JNI proporcione
 *    `wine`, `wine64`, `box64`, `wine86` o un stub de pruebas.
 * 2. Lanzar el resto de la línea de comandos con `ProcessBuilder`.
 * 3. Devolver un [AndroidProcessHandle] que envuelve el `java.lang.Process`.
 *
 * Si el resolver no encuentra el binario, lanza
 * [BinaryNotInstalledException]. En ese caso, el motor de ejecución registra
 * el error y la UI lo muestra: mejor eso que fingir que algo se ha ejecutado.
 */
class AndroidProcessLauncher(
    private val binaryResolver: BinaryResolver = AndroidBinaryResolver,
) : ProcessLauncher {

    override fun launch(
        command: List<String>,
        environment: Map<String, String>,
        workingDirectory: String?,
    ): ProcessHandle {
        require(command.isNotEmpty()) { "El comando no puede estar vacío" }

        val binary = binaryResolver.resolve(command.first())
            ?: throw BinaryNotInstalledException(command.first())

        val resolved = listOf(binary) + command.drop(1)
        val builder = ProcessBuilder(resolved)
        builder.environment().clear()
        environment.forEach { (k, v) -> builder.environment()[k] = v }
        if (workingDirectory != null) {
            builder.directory(File(workingDirectory))
        }
        return AndroidProcessHandle(builder.start())
    }
}

/**
 * Resuelve el nombre de un binario en una ruta concreta.
 *
 * Si devuelve `null`, [AndroidProcessLauncher] lanza
 * [BinaryNotInstalledException].
 */
fun interface BinaryResolver {
    fun resolve(name: String): String?
}

/**
 * Resolver que **siempre** devuelve `null`: en un dispositivo Android sin
 * root, NDK propio o conector JNI, no hay binarios de Wine/Box64 instalados.
 *
 * Es el comportamiento por defecto: el motor falla de forma explícita y la
 * UI muestra qué falta, en lugar de fingir éxito.
 */
object AndroidBinaryResolver : BinaryResolver {
    override fun resolve(name: String): String? = null
}

/**
 * Envoltorio de `java.lang.Process` que cumple el contrato de [ProcessHandle].
 */
class AndroidProcessHandle(
    private val process: Process,
) : ProcessHandle {

    override val pid: Long
        get() = try {
            // `java.lang.Process.pid()` y `Process.toHandle().pid()` solo
            // están disponibles en Java 9+. En Android, el stub de la clase
            // `Process` no los expone, así que los localizamos por reflexión.
            val pidMethod = runCatching { process.javaClass.getMethod("pid") }.getOrNull()
            if (pidMethod != null) {
                (pidMethod.invoke(process) as? Long) ?: -1L
            } else {
                val handle = runCatching { process.javaClass.getMethod("toHandle").invoke(process) }.getOrNull()
                if (handle != null) {
                    val handlePidMethod = handle.javaClass.getMethod("pid")
                    (handlePidMethod.invoke(handle) as? Long) ?: -1L
                } else {
                    -1L
                }
            }
        } catch (_: Throwable) {
            -1L
        }

    override val isAlive: Boolean
        get() = try {
            process.isAlive
        } catch (_: Throwable) {
            false
        }

    override val stdout: InputStream get() = process.inputStream
    override val stderr: InputStream get() = process.errorStream

    override fun kill() {
        runCatching { process.destroy() }
        runCatching { process.destroyForcibly() }
    }

    override fun waitFor(): Int {
        return try {
            process.waitFor()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            -1
        }
    }
}

/**
 * Lanzador de procesos **falso** para pruebas: no arranca nada real, sino que
 * genera líneas en `stdout` y `stderr` con un pequeño retardo y termina.
 *
 * Es útil para probar la máquina de estados del motor sin un `.exe` ni un
 * Wine instalado.
 */
class NoopProcessLauncher(
    private val stdoutLines: List<String> = listOf("NEXORA noop: arranque"),
    private val stderrLines: List<String> = listOf("NEXORA noop: sin motor"),
    private val lineDelayMs: Long = 20L,
    private val exitCode: Int = 0,
    private val fakePid: Long = 1000L,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ProcessLauncher {

    override fun launch(
        command: List<String>,
        environment: Map<String, String>,
        workingDirectory: String?,
    ): ProcessHandle {
        val stdoutIn = PipedInputStream()
        val stdoutOut = PipedOutputStream(stdoutIn)
        val stderrIn = PipedInputStream()
        val stderrOut = PipedOutputStream(stderrIn)

        val alive = AtomicBoolean(true)
        val lines = ConcurrentLinkedQueue<String>()

        scope.launch {
            for (line in stdoutLines) {
                if (!alive.get()) break
                delay(lineDelayMs)
                stdoutOut.write((line + "\n").toByteArray())
                stdoutOut.flush()
                lines.add(line)
            }
            stdoutOut.close()
        }
        scope.launch {
            for (line in stderrLines) {
                if (!alive.get()) break
                delay(lineDelayMs)
                stderrOut.write((line + "\n").toByteArray())
                stderrOut.flush()
                lines.add(line)
            }
            stderrOut.close()
        }

        return NoopProcessHandle(
            stdout = stdoutIn,
            stderr = stderrIn,
            fakePid = fakePid,
            exitCode = exitCode,
            alive = alive,
        )
    }
}

/**
 * `ProcessHandle` falso para pruebas.
 *
 * La única señal de "vivo" que da es el flag interno; `kill()` lo apaga y
 * cierra los flujos para que cualquier lector bloqueante termine.
 */
class NoopProcessHandle(
    override val stdout: InputStream,
    override val stderr: InputStream,
    private val fakePid: Long,
    private val exitCode: Int,
    private val alive: AtomicBoolean,
) : ProcessHandle {

    override val pid: Long get() = fakePid
    override val isAlive: Boolean get() = alive.get()

    override fun kill() {
        alive.set(false)
        runCatching { stdout.close() }
        runCatching { stderr.close() }
    }

    override fun waitFor(): Int {
        while (alive.get() && hasOpenStream()) {
            try {
                Thread.sleep(10)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return -1
            }
        }
        return exitCode
    }

    private fun hasOpenStream(): Boolean {
        return try {
            stdout.available() > 0 || stderr.available() > 0
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Helper para pruebas: lee un flujo en un hilo y devuelve lo leído.
 *
 * Existe solo en el paquete `engine` para los tests; no se usa en producción.
 */
internal fun drain(stream: InputStream): List<String> {
    val result = mutableListOf<String>()
    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
        while (true) {
            val line = reader.readLine() ?: break
            result += line
        }
    }
    return result
}

internal fun drainWithTimeout(
    stream: InputStream,
    timeoutMs: Long = 500L,
): List<String> = runBlocking(Dispatchers.IO) {
    withContext(Dispatchers.IO) {
        val lines = mutableListOf<String>()
        val job: Job = launch {
            try {
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        lines += line
                    }
                }
            } catch (_: Exception) {
                // stream cerrado
            }
        }
        delay(timeoutMs)
        job.cancel()
        lines
    }
}