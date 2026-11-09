package com.nexora.android.runtime.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashLogAnalyzerTest {

    @Test
    fun `reconoce una violacion de segmento`() {
        val finding = CrashLogAnalyzer.analyze(
            listOf(
                "wine: created the configuration directory",
                "Segmentation fault (core dumped)",
            ),
        )

        assertEquals(CrashSignature.SEGFAULT, finding.signature)
        assertTrue(finding.detected)
        assertTrue(finding.evidence.any { it.contains("Segmentation") })
        assertTrue(finding.suggestion.contains("SIGSEGV"))
    }

    @Test
    fun `reconoce una excepcion no controlada de wine`() {
        val finding = CrashLogAnalyzer.analyze(
            listOf("wine: Unhandled exception 0xc0000005 at address 0x00401000 (thread 0024)"),
        )

        assertEquals(CrashSignature.UNHANDLED_EXCEPTION, finding.signature)
    }

    @Test
    fun `un page fault de wine se clasifica como violacion de segmento`() {
        val finding = CrashLogAnalyzer.analyze(
            listOf("wine: Unhandled page fault on read access to 0x00000000"),
        )

        // Wine llama "page fault" a lo que el kernel reporta como SIGSEGV.
        assertEquals(CrashSignature.SEGFAULT, finding.signature)
    }

    @Test
    fun `reconoce una dll que falta`() {
        val finding = CrashLogAnalyzer.analyze(
            listOf("err:module:import_dll Library MSVCP140.dll (which is needed by L\"game.exe\") not found"),
        )

        assertEquals(CrashSignature.MISSING_DLL, finding.signature)
        assertTrue(finding.suggestion.contains("winetricks"))
    }

    @Test
    fun `reconoce la perdida del dispositivo vulkan`() {
        val finding = CrashLogAnalyzer.analyze(
            listOf("err:   DxvkSubmissionQueue: Command submission failed: VK_ERROR_DEVICE_LOST"),
        )

        assertEquals(CrashSignature.DEVICE_LOST, finding.signature)
    }

    @Test
    fun `reconoce falta de memoria`() {
        val finding = CrashLogAnalyzer.analyze(listOf("std::bad_alloc"))

        assertEquals(CrashSignature.OUT_OF_MEMORY, finding.signature)
    }

    @Test
    fun `reconoce instruccion ilegal del traductor`() {
        val finding = CrashLogAnalyzer.analyze(listOf("box64: Illegal instruction at 0x7f00"))

        assertEquals(CrashSignature.ILLEGAL_INSTRUCTION, finding.signature)
    }

    @Test
    fun `reconoce permiso denegado`() {
        val finding = CrashLogAnalyzer.analyze(listOf("wine: cannot open /data/.../wine64: Permission denied"))

        assertEquals(CrashSignature.PERMISSION_DENIED, finding.signature)
    }

    @Test
    fun `reconoce el runtime sin instalar`() {
        val finding = CrashLogAnalyzer.analyze(
            listOf("BinaryNotInstalledException: no hay wine64 en filesDir/runtime"),
        )

        assertEquals(CrashSignature.MISSING_BINARY, finding.signature)
    }

    @Test
    fun `la gravedad manda cuando hay varios sintomas`() {
        val finding = CrashLogAnalyzer.analyze(
            listOf(
                "Permission denied while opening cache",
                "Segmentation fault (core dumped)",
            ),
        )

        assertEquals(CrashSignature.SEGFAULT, finding.signature)
    }

    @Test
    fun `sin sintomas no inventa una causa`() {
        val finding = CrashLogAnalyzer.analyze(listOf("fixme:heap:HeapSetInformation", "Juego iniciado"))

        assertEquals(CrashSignature.NONE, finding.signature)
        assertFalse(finding.detected)
        assertTrue(finding.suggestion.contains("No se ha reconocido"))
    }

    @Test
    fun `un registro vacio se reporta como vacio`() {
        val finding = CrashLogAnalyzer.analyze(emptyList())

        assertEquals(CrashSignature.NONE, finding.signature)
        assertTrue(finding.suggestion.contains("vacío"))
    }

    @Test
    fun `la evidencia se recorta y se limita`() {
        val longLine = "Segmentation fault " + "x".repeat(400)
        val finding = CrashLogAnalyzer.analyze(
            listOf(longLine, "SIGSEGV", "signal 11", "page fault on read access"),
            maxEvidence = 2,
        )

        assertEquals(2, finding.evidence.size)
        assertTrue(finding.evidence.first().length <= 160)
    }
}
