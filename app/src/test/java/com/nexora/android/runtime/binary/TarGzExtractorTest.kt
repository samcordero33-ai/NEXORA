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
 * Pruebas del extractor de `.tar.gz`.
 *
 * El archivo de prueba se construye en memoria con un escritor ustar mínimo:
 * así la prueba no depende de un `.tar.gz` versionado ni de la herramienta
 * `tar` del sistema, y sigue siendo válida en cualquier máquina.
 */
class TarGzExtractorTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("nexora-tar").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `extrae archivos y carpetas anidadas`() {
        val archive = tarGz(
            Entry(name = "bin/", type = '5'),
            Entry(name = "bin/wine", content = "#!/bin/sh\necho hola\n".toByteArray(), mode = 0b111_101_101),
            Entry(name = "share/wine/", type = '5'),
            Entry(name = "share/wine/version", content = "11.2".toByteArray()),
        )

        val stats = TarGzExtractor(root).extract(archive.inputStream())

        assertEquals(2, stats.files)
        assertEquals(2, stats.directories)
        assertEquals("11.2", File(root, "share/wine/version").readText())
        assertEquals("#!/bin/sh\necho hola\n", File(root, "bin/wine").readText())
    }

    @Test
    fun `respeta el bit de ejecucion del modo`() {
        val archive = tarGz(
            Entry(name = "bin/wine", content = "x".toByteArray(), mode = 0b111_101_101),
            Entry(name = "share/dato.txt", content = "x".toByteArray(), mode = 0b110_100_100),
        )

        TarGzExtractor(root).extract(archive.inputStream())

        assertTrue(File(root, "bin/wine").canExecute())
        assertFalse(File(root, "share/dato.txt").canExecute())
    }

    @Test
    fun `descarta rutas que se escapan del destino`() {
        val outside = File(root.parentFile, "nexora-escape.txt")
        outside.delete()
        val archive = tarGz(
            Entry(name = "../nexora-escape.txt", content = "pwned".toByteArray()),
            Entry(name = "legitimo.txt", content = "ok".toByteArray()),
        )

        val stats = TarGzExtractor(root).extract(archive.inputStream())

        assertEquals(1, stats.skipped)
        assertFalse(outside.exists())
        assertTrue(File(root, "legitimo.txt").isFile)
    }

    @Test
    fun `crea enlaces simbolicos relativos e ignora los absolutos`() {
        val archive = tarGz(
            Entry(name = "bin/wine64", content = "bin".toByteArray()),
            Entry(name = "bin/wine", type = '2', link = "wine64"),
            Entry(name = "bin/roto", type = '2', link = "/usr/lib/libroto.so"),
        )

        val stats = TarGzExtractor(root).extract(archive.inputStream())

        assertEquals(1, stats.links)
        assertEquals(1, stats.skipped)
        assertEquals("bin", File(root, "bin/wine").readText())
    }

    @Test(expected = java.io.IOException::class)
    fun `lanza si el archivo llega truncado`() {
        // Contenido poco comprimible: si fueran ceros, el gzip cabría en menos
        // de los 600 bytes que se recortan y la prueba no probaría nada.
        val complete = tarGz(Entry(name = "dato.txt", content = ByteArray(8192) { (it * 37 % 251).toByte() }))
        val truncated = complete.copyOf(complete.size * 2 / 3)

        TarGzExtractor(root).extract(truncated.inputStream())
    }

    // --- Escritor tar mínimo -------------------------------------------------

    private data class Entry(
        val name: String,
        val content: ByteArray = ByteArray(0),
        val mode: Int = 0b110_100_100,
        val type: Char = '0',
        val link: String = "",
    )

    private fun tarGz(vararg entries: Entry): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { gzip ->
            for (entry in entries) {
                gzip.write(header(entry))
                if (entry.type == '0' && entry.content.isNotEmpty()) {
                    gzip.write(entry.content)
                    gzip.write(ByteArray((512 - entry.content.size % 512) % 512))
                }
            }
            gzip.write(ByteArray(1024))
        }
        return output.toByteArray()
    }

    private fun header(entry: Entry): ByteArray {
        val block = ByteArray(512)
        write(block, 0, 100, entry.name)
        write(block, 100, 8, Integer.toOctalString(entry.mode))
        write(block, 108, 8, "0")
        write(block, 116, 8, "0")
        write(block, 124, 12, Integer.toOctalString(entry.content.size))
        write(block, 136, 12, "0")
        java.util.Arrays.fill(block, 148, 156, ' '.code.toByte())
        block[156] = entry.type.code.toByte()
        write(block, 157, 100, entry.link)
        write(block, 257, 6, "ustar")
        write(block, 263, 2, "00")

        var checksum = 0
        for (byte in block) checksum += byte.toInt() and 0xFF
        write(block, 148, 7, Integer.toOctalString(checksum))
        block[155] = ' '.code.toByte()
        return block
    }

    private fun write(block: ByteArray, offset: Int, length: Int, value: String) {
        val bytes = value.toByteArray()
        bytes.copyInto(block, offset, 0, minOf(bytes.size, length))
    }
}
