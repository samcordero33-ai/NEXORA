package com.nexora.android.runtime.saves

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SaveBackupArchiveTest {

    private lateinit var tmp: File

    @Before
    fun setUp() {
        tmp = Files.createTempDirectory("nexora-saves").toFile()
    }

    @After
    fun tearDown() {
        tmp.deleteRecursively()
    }

    @Test
    fun `pack crea un zip con los archivos del directorio`() {
        val src = File(tmp, "src").apply { mkdirs() }
        File(src, "save1.dat").writeText("partida 1")
        File(src, "save2.dat").writeText("partida 2")
        File(src, "sub").mkdirs()
        File(src, "sub/save3.dat").writeText("partida 3")

        val zip = File(tmp, "out.zip")
        val n = SaveBackupArchive.pack(src, zip)

        assertEquals(3, n)
        assertTrue("zip no vacío", zip.exists() && zip.length() > 0)
    }

    @Test
    fun `round-trip pack + extract conserva los archivos`() {
        val src = File(tmp, "src").apply { mkdirs() }
        File(src, "a.txt").writeText("alpha")
        File(src, "b").mkdirs()
        File(src, "b/c.txt").writeText("beta")

        val zip = File(tmp, "export.zip")
        SaveBackupArchive.pack(src, zip)

        val dst = File(tmp, "dst").apply { mkdirs() }
        val summary = SaveImporter.extract(zip, dst)

        assertEquals(2, summary.entriesExtracted)
        assertEquals(File(dst, "a.txt").readText(), "alpha")
        assertEquals(File(dst, "b/c.txt").readText(), "beta")
    }

    @Test
    fun `entryPrefix antepone un prefijo a las entradas del zip`() {
        val src = File(tmp, "s").apply { mkdirs() }
        File(src, "x.dat").writeText("x")

        val zip = File(tmp, "p.zip")
        SaveBackupArchive.pack(src, zip, entryPrefix = "nexora/hola")

        // Verificamos que el zip contiene la entrada prefijada.
        val entries = java.util.zip.ZipFile(zip).use { z ->
            z.entries().toList().map { it.name }
        }
        assertTrue("Entradas: $entries", entries.any { it == "nexora/hola/x.dat" })
    }
}

class SaveImporterTest {

    private lateinit var tmp: File

    @Before
    fun setUp() { tmp = Files.createTempDirectory("nexora-imp").toFile() }
    @After fun tearDown() { tmp.deleteRecursively() }

    @Test
    fun `extract rechaza entradas Zip Slip fuera del directorio destino`() {
        // Construimos un zip "malicioso" a mano.
        val zip = File(tmp, "evil.zip")
        java.util.zip.ZipOutputStream(zip.outputStream()).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("legit.txt"))
            z.write("ok".toByteArray())
            z.closeEntry()
            z.putNextEntry(java.util.zip.ZipEntry("../evil.txt"))
            z.write("evil".toByteArray())
            z.closeEntry()
        }

        val dst = File(tmp, "dst").apply { mkdirs() }
        val summary = SaveImporter.extract(zip, dst)

        assertEquals(1, summary.entriesExtracted)
        assertTrue(summary.rejectedEntries.any { it.contains("evil") })
        assertEquals("ok", File(dst, "legit.txt").readText())
        assertNotNull("legit debe existir", File(dst, "legit.txt").exists())
    }
}

class ShaderCacheCleanerTest {

    private lateinit var tmp: File

    @Before
    fun setUp() { tmp = Files.createTempDirectory("nexora-cache").toFile() }
    @After fun tearDown() { tmp.deleteRecursively() }

    @Test
    fun `clean elimina archivos dxvk-cache y state_cache`() {
        val prefix = File(tmp, "prefix").apply { mkdirs() }
        val dxvk = File(prefix, "drive_c/users/steamuser/AppData/Roaming/DXVK").apply { mkdirs() }
        File(dxvk, "pipeline.iso.dxvk-cache").writeBytes(ByteArray(128))
        File(dxvk, "spirv.iso_state_cache").writeBytes(ByteArray(256))
        File(dxvk, "otro.txt").writeText("no se debe borrar")

        val r = ShaderCacheCleaner.clean(prefix)
        assertEquals(2, r.deletedFiles)
        assertTrue(r.freedBytes >= 384)
        assertTrue("otro.txt sigue existiendo", File(dxvk, "otro.txt").exists())
    }

    @Test
    fun `clean es idempotente cuando el prefijo no existe`() {
        val r = ShaderCacheCleaner.clean(File(tmp, "noexiste"))
        assertEquals(0, r.deletedFiles)
        assertEquals(0L, r.freedBytes)
    }
}

class TempFileCleanerTest {

    private lateinit var tmp: File

    @Before
    fun setUp() { tmp = Files.createTempDirectory("nexora-tmp").toFile() }
    @After fun tearDown() { tmp.deleteRecursively() }

    @Test
    fun `clean borra archivos recientes y conserva los antiguos sin deep`() {
        val dir = File(tmp, "tmp").apply { mkdirs() }
        val oldFile = File(dir, "old.log")
        oldFile.writeText("viejo")
        oldFile.setLastModified(System.currentTimeMillis() - 30L * 24L * 3600L * 1000L)
        val newFile = File(dir, "new.log")
        newFile.writeText("nuevo")

        val r = TempFileCleaner.clean(listOf(dir), deep = false)
        assertEquals(1, r.deletedFiles)
        assertTrue(oldFile.exists())
        assertTrue(!newFile.exists())
    }

    @Test
    fun `clean con deep borra todo independientemente de la antiguedad`() {
        val dir = File(tmp, "tmp").apply { mkdirs() }
        val oldFile = File(dir, "old.log")
        oldFile.writeText("viejo")
        oldFile.setLastModified(System.currentTimeMillis() - 365L * 24L * 3600L * 1000L)

        val r = TempFileCleaner.clean(listOf(dir), deep = true)
        assertEquals(1, r.deletedFiles)
        assertTrue(!oldFile.exists())
    }
}