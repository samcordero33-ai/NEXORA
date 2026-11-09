package com.nexora.android.runtime.binary

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RuntimeManifestTest {

    private val sample = """
        # manifiesto de prueba
        version=8

        [wine]
        asset=bin/wine64
        target=bin/wine64
        sha256=ABCDEF0123
        executable=true
        size=1024

        [box64]
        asset=bin/box64
        target=bin/box64
        executable=true
    """.trimIndent()

    @Test
    fun `parsea version y entradas del manifiesto`() {
        val result = RuntimeManifest.parse(sample)

        assertEquals("8", result.manifest.version)
        assertEquals(2, result.manifest.entries.size)
        assertTrue(result.problems.isEmpty())

        val wine = result.manifest.entries.first { it.component == BinaryComponent.WINE }
        assertEquals("bin/wine64", wine.assetPath)
        assertEquals("ABCDEF0123", wine.sha256)
        assertTrue(wine.executable)
        assertEquals(1024L, wine.sizeBytes)
        assertEquals("wine64", wine.fileName)
    }

    @Test
    fun `una entrada sin asset se descarta con aviso`() {
        val result = RuntimeManifest.parse("[wine]\ntarget=bin/wine64")

        assertTrue(result.manifest.entries.isEmpty())
        assertTrue(result.problems.any { it.contains("no declara asset") })
    }

    @Test
    fun `un componente desconocido no rompe el parseo`() {
        val result = RuntimeManifest.parse("[dolphin]\nasset=bin/dolphin")

        assertTrue(result.manifest.entries.isEmpty())
        assertTrue(result.problems.any { it.contains("desconocido") })
    }

    @Test
    fun `el target cae al asset cuando no se declara`() {
        val result = RuntimeManifest.parse("[dxvk]\nasset=lib/d3d11.dll")

        assertEquals("lib/d3d11.dll", result.manifest.entries.single().targetPath)
        assertFalse(result.manifest.entries.single().executable)
    }
}

class BinaryInstallerTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("nexora-runtime").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun entry(
        asset: String = "bin/wine64",
        target: String = "bin/wine64",
        content: ByteArray = "wine-binario".toByteArray(),
        sha: String = Sha256.of("wine-binario".toByteArray()),
        executable: Boolean = true,
    ) = BinaryEntry(
        component = BinaryComponent.WINE,
        assetPath = asset,
        targetPath = target,
        sha256 = sha,
        executable = executable,
        sizeBytes = content.size.toLong(),
    )

    @Test
    fun `extrae el binario y le da permiso de ejecucion`() {
        val source = ByteArrayBinarySource(mapOf("bin/wine64" to "wine-binario".toByteArray()))
        val report = BinaryInstaller(source, root).install(listOf(entry()))

        assertTrue(report.ok)
        assertEquals(1, report.installed.size)
        val installed = File(root, "bin/wine64")
        assertTrue(installed.isFile)
        assertEquals("wine-binario", installed.readText())
        assertTrue("debe ser ejecutable", installed.canExecute())
        assertEquals(1, report.availableComponents.size)
    }

    @Test
    fun `una huella distinta no deja archivo instalado`() {
        val source = ByteArrayBinarySource(mapOf("bin/wine64" to "otra-cosa".toByteArray()))
        val report = BinaryInstaller(source, root).install(
            listOf(entry(sha = Sha256.of("wine-binario".toByteArray()))),
        )

        assertFalse(report.ok)
        assertTrue(report.failures.single().reason.contains("Huella"))
        assertFalse(File(root, "bin/wine64").exists())
        assertFalse("no debe quedar el .part", File(root, "bin/wine64.part").exists())
    }

    @Test
    fun `rechaza un destino que se sale del directorio`() {
        val source = ByteArrayBinarySource(mapOf("bin/wine64" to "x".toByteArray()))
        val report = BinaryInstaller(source, root).install(
            listOf(entry(target = "../fuera/wine64", content = "x".toByteArray())),
        )

        assertFalse(report.ok)
        assertTrue(report.failures.single().reason.contains("se sale"))
    }

    @Test
    fun `un asset ausente se reporta como missing y no como fallo`() {
        val source = ByteArrayBinarySource(emptyMap())
        val report = BinaryInstaller(source, root).install(listOf(entry()))

        assertTrue(report.ok)
        assertEquals(1, report.missing.size)
        assertTrue(report.installed.isEmpty())
    }

    @Test
    fun `no reinstala si el archivo ya esta al dia`() {
        val source = ByteArrayBinarySource(mapOf("bin/wine64" to "wine-binario".toByteArray()))
        val installer = BinaryInstaller(source, root)
        installer.install(listOf(entry()))

        val second = installer.install(listOf(entry()))

        assertEquals(1, second.upToDate.size)
        assertTrue(second.installed.isEmpty())
    }

    @Test
    fun `sin huella declarada copia sin verificar`() {
        val source = ByteArrayBinarySource(mapOf("lib/d3d11.dll" to "dll".toByteArray()))
        val report = BinaryInstaller(source, root).install(
            listOf(
                BinaryEntry(
                    component = BinaryComponent.DXVK,
                    assetPath = "lib/d3d11.dll",
                    targetPath = "lib/d3d11.dll",
                    executable = false,
                ),
            ),
        )

        assertTrue(report.ok)
        assertTrue(File(root, "lib/d3d11.dll").exists())
        assertFalse("una DLL no debe ser ejecutable", File(root, "lib/d3d11.dll").canExecute())
    }
}

class RuntimeLayoutTest {

    @Test
    fun `fromDisk solo declara binarios presentes y ejecutables`() {
        val root = Files.createTempDirectory("nexora-layout").toFile()
        try {
            val wine = File(root, "bin/wine64").apply { parentFile?.mkdirs() }
            wine.writeText("x")
            wine.setExecutable(true)

            val layout = RuntimeLayout.fromDisk(
                root = root,
                version = "8",
                entries = listOf(
                    BinaryEntry(BinaryComponent.WINE, "bin/wine64", "bin/wine64", executable = true),
                    BinaryEntry(BinaryComponent.BOX64, "bin/box64", "bin/box64", executable = true),
                ),
            )

            assertTrue(layout.has(BinaryComponent.WINE))
            assertFalse(layout.has(BinaryComponent.BOX64))
            assertTrue(layout.canLaunch)
            assertFalse("sin box64 no hay traductor", layout.needsTranslator(listOf("arm64-v8a")))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `un layout vacio no puede lanzar y lista lo que falta`() {
        val layout = RuntimeLayout.empty("/tmp/runtime")

        assertFalse(layout.canLaunch)
        assertNotNull(layout.binDir)
        assertEquals(
            listOf(BinaryComponent.WINE, BinaryComponent.BOX64),
            layout.missingComponents(listOf(BinaryComponent.WINE, BinaryComponent.BOX64)),
        )
    }
}
