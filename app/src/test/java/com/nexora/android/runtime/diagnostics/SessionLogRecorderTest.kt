package com.nexora.android.runtime.diagnostics

import com.nexora.android.runtime.model.LogStream
import com.nexora.android.runtime.model.RuntimeLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SessionLogRecorderTest {

    private lateinit var tmp: File
    private lateinit var logDir: File

    @Before
    fun setUp() {
        tmp = Files.createTempDirectory("nexora-logs").toFile()
        logDir = File(tmp, "logs")
    }

    @After
    fun tearDown() {
        tmp.deleteRecursively()
    }

    @Test
    fun `abre un archivo con la cabecera de la sesion`() {
        val recorder = SessionLogRecorder(logDir, clock = { 1_700_000_000_000L })
        val file = recorder.start("witcher3", header = listOf("Comando: wine64 juego.exe"))

        assertNotNull(file)
        assertTrue(file!!.exists())
        val text = file.readText()
        assertTrue(text.contains("# Juego: witcher3"))
        assertTrue(text.contains("Comando: wine64 juego.exe"))
        recorder.close()
    }

    @Test
    fun `escribe cada linea del motor`() {
        val recorder = SessionLogRecorder(logDir, clock = { 1_700_000_000_000L })
        val file = recorder.start("juego")!!
        recorder.append(
            RuntimeLog(timestampMs = 1_700_000_000_000L, stream = LogStream.STDERR, message = "err:fallo"),
        )
        recorder.close()

        val text = file.readText()
        assertTrue(text.contains("[stderr] err:fallo"))
    }

    @Test
    fun `append sin sesion no lanza`() {
        val recorder = SessionLogRecorder(logDir)
        recorder.append(RuntimeLog(0L, LogStream.STDOUT, "hola"))
        assertNull(recorder.currentFile)
    }

    @Test
    fun `se corta al alcanzar el tope de tamano`() {
        val recorder = SessionLogRecorder(logDir, clock = { 1_700_000_000_000L }, maxBytes = 200L)
        val file = recorder.start("juego")!!
        repeat(50) { recorder.appendLine("linea $it de relleno") }
        recorder.close()

        val text = file.readText()
        assertTrue(text.contains("# log truncado"))
    }

    @Test
    fun `close es idempotente`() {
        val recorder = SessionLogRecorder(logDir, clock = { 1_700_000_000_000L })
        recorder.start("juego")
        val first = recorder.close()
        val second = recorder.close()

        assertNotNull(first)
        assertNull(second)
        assertNull(recorder.currentFile)
    }

    @Test
    fun `el nombre del archivo incluye el juego saneado`() {
        val name = SessionLogRecorder.fileName("a/b:c", 1_700_000_000_000L)

        assertTrue(name.startsWith("session-a-b-c-"))
        assertTrue(name.endsWith(".log"))
        assertFalse(name.contains("/"))
    }
}

class LogFileStoreTest {

    private lateinit var tmp: File

    @Before
    fun setUp() {
        tmp = Files.createTempDirectory("nexora-logstore").toFile()
    }

    @After
    fun tearDown() {
        tmp.deleteRecursively()
    }

    @Test
    fun `lista solo los log y del mas reciente al mas antiguo`() {
        val older = File(tmp, "session-a.log").apply {
            writeText("x")
            setLastModified(1_000L)
        }
        val newer = File(tmp, "session-b.log").apply {
            writeText("y")
            setLastModified(2_000L)
        }
        File(tmp, "notas.txt").writeText("no es un log")

        val list = LogFileStore.list(tmp)

        assertEquals(2, list.size)
        assertEquals(newer.name, list.first().name)
        assertEquals(older.name, list.last().name)
    }

    @Test
    fun `readTail devuelve solo el final`() {
        val file = File(tmp, "session.log")
        file.writeText((1..10).joinToString("\n") { "linea $it" })

        val tail = LogFileStore.readTail(file, maxLines = 3)

        assertEquals(listOf("linea 8", "linea 9", "linea 10"), tail)
    }

    @Test
    fun `readTail con mas lineas que el archivo devuelve todo`() {
        val file = File(tmp, "session.log")
        file.writeText("a\nb")

        assertEquals(listOf("a", "b"), LogFileStore.readTail(file, maxLines = 100))
    }

    @Test
    fun `readTail de un archivo inexistente devuelve vacio`() {
        assertTrue(LogFileStore.readTail(File(tmp, "no-existe.log")).isEmpty())
    }

    @Test
    fun `borra un log y el listado se actualiza`() {
        val file = File(tmp, "session.log").apply { writeText("x") }

        assertTrue(LogFileStore.delete(file))
        assertTrue(LogFileStore.list(tmp).isEmpty())
    }

    @Test
    fun `deleteAll vacia la carpeta de logs`() {
        File(tmp, "a.log").writeText("x")
        File(tmp, "b.log").writeText("y")
        File(tmp, "c.txt").writeText("z")

        assertEquals(2, LogFileStore.deleteAll(tmp))
        assertTrue(File(tmp, "c.txt").exists())
    }
}
