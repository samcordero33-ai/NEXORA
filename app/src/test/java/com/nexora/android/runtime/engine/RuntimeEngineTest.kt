package com.nexora.android.runtime.engine

import com.nexora.android.runtime.env.ComposedEnvironment
import com.nexora.android.runtime.model.RuntimeStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de la máquina de estados del [RuntimeEngine].
 *
 * Usa un [NoopProcessLauncher] para no depender de Wine ni del sistema
 * operativo: las líneas se generan en memoria, el "proceso" termina por sí
 * solo, y nosotros verificamos la transición de estados y el `Flow` de
 * logs.
 */
class RuntimeEngineTest {

    private fun fakeEnvironment(): ComposedEnvironment = ComposedEnvironment(
        environment = mapOf("WINEPREFIX" to "/tmp/prefix", "NEXORA_FPS_CAP" to "30"),
        warnings = emptyList(),
        command = listOf("/path/to/game.exe"),
        winePrefixPath = "/tmp/prefix",
    )

    private fun newEngine(
        stdout: List<String> = listOf("Hello", "World"),
        stderr: List<String> = listOf("[stderr] warm"),
        exitCode: Int = 0,
    ): Pair<RuntimeEngine, CoroutineScope> {
        val launcher = NoopProcessLauncher(
            stdoutLines = stdout,
            stderrLines = stderr,
            exitCode = exitCode,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val engine = RuntimeEngine(launcher = launcher, scope = scope)
        return engine to scope
    }

    @Test
    fun `estado inicial es Idle`() {
        val (engine, _) = newEngine()
        assertEquals(RuntimeStatus.Idle, engine.status.value)
    }

    @Test
    fun `prepare mueve el estado a Preparing y devuelve los warnings`() {
        var ensured = false
        val (engine, _) = newEngine()
        val preparation = engine.prepare(
            env = fakeEnvironment().copy(warnings = listOf("aviso 1", "aviso 2")),
            ensurePrefix = { ensured = true },
        )

        assertTrue("prepare debe invocar ensurePrefix", ensured)
        assertEquals(listOf("aviso 1", "aviso 2"), preparation.warnings)
        assertTrue(engine.status.value is RuntimeStatus.Preparing)
    }

    @Test
    fun `launch arranca el proceso y emite logs`() = runBlocking {
        val (engine, scope) = newEngine(
            stdout = listOf("linea 1", "linea 2", "linea 3"),
            stderr = emptyList(),
        )
        engine.prepare(env = fakeEnvironment(), ensurePrefix = {})

        engine.launch(env = fakeEnvironment())

        val running = engine.status.value as RuntimeStatus.Running
        assertTrue("PID positivo", running.pid > 0L)
        assertEquals(listOf("/path/to/game.exe"), running.commandLine)

        val logs = engine.logs() ?: error("El motor debe exponer logs tras launch")
        // Leemos exactamente las 3 líneas que produce el noop (la stderr
        // por defecto está vacía en este test). Si el motor recibiera más,
        // `take` corta.
        val collected = logs.take(3).toList()
        assertEquals(3, collected.size)
        assertTrue(
            "todas las líneas son STDOUT: $collected",
            collected.all { it.stream == com.nexora.android.runtime.model.LogStream.STDOUT },
        )
        assertEquals("linea 1", collected[0].message)
        assertEquals("linea 3", collected[2].message)

        engine.kill()
        scope.cancel()
    }

    @Test
    fun `pause y resume alternan el estado entre Paused y Running`() = runBlocking {
        val (engine, scope) = newEngine()
        engine.prepare(env = fakeEnvironment(), ensurePrefix = {})
        engine.launch(env = fakeEnvironment())

        assertTrue(engine.canPause)
        engine.pause()
        assertTrue(engine.status.value is RuntimeStatus.Paused)
        assertFalse(engine.canPause)
        assertTrue(engine.canResume)

        engine.resume()
        assertTrue(engine.status.value is RuntimeStatus.Running)

        engine.kill()
        scope.cancel()
    }

    @Test
    fun `kill desde Running termina el proceso y deja el estado Stopped`() {
        val (engine, scope) = newEngine()
        engine.prepare(env = fakeEnvironment(), ensurePrefix = {})
        engine.launch(env = fakeEnvironment())

        engine.kill()

        val stopped = engine.status.value as RuntimeStatus.Stopped
        assertEquals(0, stopped.exitCode)
        assertTrue("logs se debe limpiar tras kill", engine.logs() == null)
        scope.cancel()
    }

    @Test
    fun `binario ausente produce Failed y mantiene el error`() {
        val launcher = object : ProcessLauncher {
            override fun launch(
                command: List<String>,
                environment: Map<String, String>,
                workingDirectory: String?,
            ): ProcessHandle = throw BinaryNotInstalledException(command.first())
        }
        val engine = RuntimeEngine(launcher = launcher)
        engine.prepare(env = fakeEnvironment(), ensurePrefix = {})

        var caught: Exception? = null
        try {
            engine.launch(env = fakeEnvironment())
        } catch (e: BinaryNotInstalledException) {
            caught = e
        }
        assertNotNull("El motor debe propagar la excepción", caught)

        val failed = engine.status.value as RuntimeStatus.Failed
        assertTrue(failed.reason.contains("wine") || failed.reason.contains("box64") || failed.reason.length > 0)
    }

    @Test
    fun `kill desde Idle no hace nada y deja el estado Idle`() {
        val (engine, scope) = newEngine()
        engine.kill()
        assertEquals(RuntimeStatus.Idle, engine.status.value)
        scope.cancel()
    }

    @Test
    fun `pause desde Idle no cambia el estado`() {
        val (engine, scope) = newEngine()
        engine.pause()
        assertEquals(RuntimeStatus.Idle, engine.status.value)
        scope.cancel()
    }
}