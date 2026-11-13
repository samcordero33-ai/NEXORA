package com.nexora.android.runtime.binary

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Instalación de componentes empaquetados como `.tar.gz`.
 *
 * Es el camino que usa el runtime real: un solo asset de ~95 MB en vez de
 * 1.700 archivos sueltos en el manifiesto.
 */
class ArchiveInstallTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("nexora-archive").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private val archive: ByteArray = tarGz(
        "bin/wine" to ("#!/bin/sh\n" to 0b111_101_101),
        "bin/wineserver" to ("binario" to 0b111_101_101),
        "share/wine/version" to ("11.2" to 0b110_100_100),
    )

    private fun entry(sha256: String = Sha256.of(archive), size: Long = archive.size.toLong()) = BinaryEntry(
        component = BinaryComponent.WINE,
        assetPath = "wine/wine-11.2-arm64.tar.gz",
        targetPath = "wine",
        sha256 = sha256,
        executable = true,
        sizeBytes = size,
        archive = true,
        binaryPath = "bin/wine",
    )

    private fun installer() = BinaryInstaller(
        source = ByteArrayBinarySource(mapOf("wine/wine-11.2-arm64.tar.gz" to archive)),
        root = root,
    )

    @Test
    fun `extrae el arbol y marca el ejecutable principal`() {
        val report = installer().install(listOf(entry()))

        assertTrue(report.ok)
        assertEquals(1, report.installed.size)
        val binary = File(root, "wine/bin/wine")
        assertTrue(binary.isFile)
        assertTrue(binary.canExecute())
        assertTrue(File(root, "wine/share/wine/version").isFile)
        assertEquals("11.2", File(root, "wine/share/wine/version").readText())
        assertTrue(File(root, "wine/.nexora-archive").isFile)
    }

    @Test
    fun `la segunda pasada no vuelve a extraer`() {
        installer().install(listOf(entry()))

        val second = installer().install(listOf(entry()))

        assertTrue(second.ok)
        assertTrue(second.installed.isEmpty())
        assertEquals(1, second.upToDate.size)
    }

    @Test
    fun `una huella distinta no deja nada instalado`() {
        val report = installer().install(listOf(entry(sha256 = "0".repeat(64))))

        assertFalse(report.ok)
        assertEquals(1, report.failures.size)
        assertFalse(File(root, "wine").exists())
        assertFalse(File(root, "wine.part").exists())
    }

    @Test
    fun `un tamano distinto tampoco instala`() {
        val report = installer().install(listOf(entry(size = archive.size + 10L)))

        assertFalse(report.ok)
        assertFalse(File(root, "wine").exists())
    }

    @Test
    fun `el layout reconoce el componente extraido`() {
        installer().install(listOf(entry()))

        val layout = RuntimeLayout.fromDisk(root, "9", listOf(entry()))

        assertTrue(layout.has(BinaryComponent.WINE))
        assertEquals(File(root, "wine/bin/wine").absolutePath, layout.binary(BinaryComponent.WINE))
        assertTrue(layout.canLaunch)
    }

    @Test
    fun `dos entradas sobre el mismo archivo extraen una sola vez`() {
        val tools = entry().copy(component = BinaryComponent.TOOLS, binaryPath = "bin/wineserver")

        val report = installer().install(listOf(entry(), tools))

        assertEquals(1, report.installed.size)
        assertEquals(1, report.upToDate.size)
    }

    // --- utilidades ----------------------------------------------------------

    private fun tarGz(vararg files: Pair<String, Pair<String, Int>>): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { gzip ->
            for ((path, value) in files) {
                val (content, mode) = value
                val bytes = content.toByteArray()
                gzip.write(header(path, bytes.size, mode))
                gzip.write(bytes)
                gzip.write(ByteArray((512 - bytes.size % 512) % 512))
            }
            gzip.write(ByteArray(1024))
        }
        return output.toByteArray()
    }

    private fun header(name: String, size: Int, mode: Int): ByteArray {
        val block = ByteArray(512)
        name.toByteArray().copyInto(block, 0, 0, minOf(name.length, 100))
        Integer.toOctalString(mode).toByteArray().copyInto(block, 100)
        "0".toByteArray().copyInto(block, 108)
        "0".toByteArray().copyInto(block, 116)
        Integer.toOctalString(size).toByteArray().copyInto(block, 124)
        "0".toByteArray().copyInto(block, 136)
        java.util.Arrays.fill(block, 148, 156, ' '.code.toByte())
        block[156] = '0'.code.toByte()
        "ustar".toByteArray().copyInto(block, 257)
        "00".toByteArray().copyInto(block, 263)
        var checksum = 0
        for (byte in block) checksum += byte.toInt() and 0xFF
        Integer.toOctalString(checksum).toByteArray().copyInto(block, 148)
        block[155] = ' '.code.toByte()
        return block
    }
}
