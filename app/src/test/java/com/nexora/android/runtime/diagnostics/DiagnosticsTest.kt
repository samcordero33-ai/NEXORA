package com.nexora.android.runtime.diagnostics

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DiagnosticsLoggerTest {

    private lateinit var logger: DiagnosticsLogger

    @Before
    fun setUp() { logger = DiagnosticsLogger(capacity = 4) }

    @After
    fun tearDown() { logger.clear() }

    @Test
    fun `registra eventos en orden`() {
        logger.record(DiagnosticsLevel.INFO, "app", "uno")
        logger.record(DiagnosticsLevel.WARN, "engine", "dos")
        logger.record(DiagnosticsLevel.ERROR, "runtime", "tres")

        val snap = logger.snapshot()
        assertEquals(3, snap.size)
        assertEquals("uno", snap[0].message)
        assertEquals("dos", snap[1].message)
        assertEquals("tres", snap[2].message)
        assertEquals(DiagnosticsLevel.WARN, snap[1].level)
    }

    @Test
    fun `respeta la capacidad maxima como ring buffer`() {
        repeat(10) { i -> logger.record(DiagnosticsLevel.INFO, "t", "msg-$i") }
        val snap = logger.snapshot()
        assertEquals(4, snap.size)
        assertEquals("msg-6", snap.first().message)
        assertEquals("msg-9", snap.last().message)
    }

    @Test
    fun `clear vacia el buffer`() {
        logger.record(DiagnosticsLevel.INFO, "t", "x")
        assertEquals(1, logger.size)
        logger.clear()
        assertEquals(0, logger.size)
    }
}

class LogExporterTest {

    private lateinit var tmp: File

    @Before
    fun setUp() { tmp = Files.createTempDirectory("nexora-logexp").toFile() }
    @After fun tearDown() { tmp.deleteRecursively() }

    @Test
    fun `export escribe un archivo con cabecera y lineas`() {
        val now = 1_700_000_000_000L
        val events = listOf(
            DiagnosticsEvent(now, DiagnosticsLevel.INFO, "app", "boot"),
            DiagnosticsEvent(now + 1000, DiagnosticsLevel.WARN, "engine", "lanzando"),
        )

        val file = LogExporter.export(events, File(tmp, "out.txt"))
        assertTrue(file.exists())
        val text = file.readText()
        assertTrue("cabecera", text.startsWith("NEXORA diagnostics log"))
        assertTrue("contador", text.contains("Eventos: 2"))
        assertTrue("evento app", text.contains("INFO app: boot"))
        assertTrue("evento engine", text.contains("WARN engine: lanzando"))
    }

    @Test
    fun `export genera nombre con timestamp si la salida es directorio`() {
        val events = listOf<DiagnosticsEvent>()
        val dir = File(tmp, "exports").apply { mkdirs() }
        val file = LogExporter.export(events, dir)
        assertTrue(file.name.startsWith("nexora-diagnostics-"))
        assertTrue(file.name.endsWith(".txt"))
    }
}

class DiagnosticsEventTest {

    @Test
    fun `formatLine produce HH_mm_ss LEVEL source message`() {
        val cal = java.util.Calendar.getInstance().apply {
            set(2024, 0, 1, 13, 5, 9) // 2024-01-01 13:05:09
        }
        val event = DiagnosticsEvent(
            timestampMs = cal.timeInMillis,
            level = DiagnosticsLevel.ERROR,
            source = "runtime",
            message = "shader compile failed",
        )
        val line = event.formatLine()
        assertEquals("13:05:09 ERROR runtime: shader compile failed", line)
    }
}