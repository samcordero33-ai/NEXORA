package com.nexora.android.runtime.binary

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Prueba de humo contra los archivos **reales** de `assets/runtime/`.
 *
 * Los binarios no se versionan (los reconstruye `tools/fetch-runtime.sh`), así
 * que la prueba se salta si no están: en un clon limpio no falla, y en la
 * máquina que sí los tiene comprueba que el empaquetado es el que espera el
 * instalador.
 *
 * Se usa el archivo de Box64 y no el de Wine porque son 6 MB frente a 95 MB:
 * el camino de código es el mismo y la prueba tarda una fracción.
 */
class RealRuntimeAssetTest {

    private lateinit var root: File

    private val box64Asset = File("src/main/assets/runtime/box64/box64-0.4.4-arm64.tgz")

    @Before
    fun setUp() {
        root = Files.createTempDirectory("nexora-real").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `el box64 empaquetado se extrae y es un ELF arm64 ejecutable`() {
        assumeTrue("sin binarios empaquetados: se omite", box64Asset.isFile)

        val parent = requireNotNull(box64Asset.parentFile) { "sin carpeta de assets" }
        val installer = BinaryInstaller(
            source = DirectoryBinarySource(parent),
            root = root,
        )
        val entry = BinaryEntry(
            component = BinaryComponent.BOX64,
            assetPath = box64Asset.name,
            targetPath = "box64",
            sha256 = Sha256.of(box64Asset) ?: "",
            executable = true,
            sizeBytes = box64Asset.length(),
            archive = true,
            binaryPath = "box64",
        )

        val report = installer.install(listOf(entry))

        assertTrue(report.failures.toString(), report.ok)
        val binary = File(root, "box64/box64")
        assertTrue(binary.isFile)
        assertTrue(binary.canExecute())
        assertEquals(0x7F, binary.readBytes().first().toInt())
        assertEquals(183, readElfMachine(binary)) // EM_AARCH64
    }

    @Test
    fun `el manifiesto real declara las huellas de los archivos presentes`() {
        val manifestFile = File("src/main/assets/runtime/manifest.txt")
        assumeTrue("sin manifiesto empaquetado: se omite", manifestFile.isFile)

        val parsed = RuntimeManifest.parse(manifestFile.readText())

        assertTrue(parsed.problems.toString(), parsed.problems.isEmpty())
        assertEquals("9", parsed.manifest.version)
        for (entry in parsed.manifest.entries) {
            val asset = File("src/main/assets/runtime", entry.assetPath)
            assumeTrue("falta ${entry.assetPath}: se omite", asset.isFile)
            assertEquals(entry.assetPath, entry.sha256, Sha256.of(asset))
            assertEquals(entry.assetPath, entry.sizeBytes, asset.length())
        }
    }

    /** Lee `e_machine` de la cabecera ELF (offset 18, little-endian). */
    private fun readElfMachine(file: File): Int {
        val header = ByteArray(20)
        file.inputStream().use { it.read(header) }
        return (header[18].toInt() and 0xFF) or ((header[19].toInt() and 0xFF) shl 8)
    }
}
