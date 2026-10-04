package com.nexora.android.runtime.engine

import com.nexora.android.runtime.model.LogStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del [LogStreamer].
 *
 * Alimenta los flujos desde un hilo productor (`launch { writer.write(...) }`)
 * y comprueba que las lee consumiéndolas con `first()` y `toList()`. Verifica
 * también el sello de origen (stdout/stderr).
 */
class LogStreamerTest {

    @Test
    fun `stream emite lineas con el sello correcto`() = runBlocking {
        val stdout = PipedInputStream()
        val stderr = PipedInputStream()
        val stdoutWriter = PipedOutputStream(stdout)
        val stderrWriter = PipedOutputStream(stderr)

        val handle = object : ProcessHandle {
            override val pid = 42L
            override val isAlive = true
            override val stdout = stdout
            override val stderr = stderr
            override fun kill() { stdout.close(); stderr.close() }
            override fun waitFor() = 0
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val streamer = LogStreamer(scope = scope)

        scope.launch(Dispatchers.IO) {
            stdoutWriter.write("out-1\n".toByteArray())
            stdoutWriter.write("out-2\n".toByteArray())
            stdoutWriter.flush()
            stderrWriter.write("err-1\n".toByteArray())
            stderrWriter.flush()
            stdoutWriter.close()
            stderrWriter.close()
        }

        val collected = mutableListOf<com.nexora.android.runtime.model.RuntimeLog>()
        val flow = streamer.stream(handle)
        // Leemos exactamente 3 elementos: el productor escribe 3 líneas y
        // cierra los flujos, pero el canal sigue abierto hasta `stop()`. Si
        // hacemos `collect` plano, se queda esperando para siempre.
        val first = flow.take(3).toList()
        collected.addAll(first)
        // No esperamos exactamente 3: nuestro pump aísla el orden; lo que
        // garantizamos es que todas las líneas aparecen con su sello.
        val outs = collected.filter { it.stream == LogStream.STDOUT }.map { it.message }
        val errs = collected.filter { it.stream == LogStream.STDERR }.map { it.message }
        assertTrue("stdout: $outs", "out-1" in outs)
        assertTrue("stdout: $outs", "out-2" in outs)
        assertTrue("stderr: $errs", "err-1" in errs)

        streamer.stop()
        scope.cancel()
    }
}