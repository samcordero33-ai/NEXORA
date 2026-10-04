package com.nexora.android.runtime.env

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Pruebas de la gestión del prefijo de Wine.
 *
 * Usa `TemporaryFolder` para tener un sistema de archivos real aislado del
 * disco del desarrollador: el compositor debe crear exactamente la jerarquía
 * que dice y no romper un prefijo ya existente.
 */
class WinePrefixTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `createIfMissing crea la jerarquia minima`() {
        val root = tempFolder.newFolder("filesDir")
        val prefix = WinePrefix.underFilesDir(root, "hollow")

        val created = prefix.createIfMissing()

        assertTrue(prefix.exists())
        assertTrue(created.isNotEmpty())
        assertTrue(File(prefix.path, "drive_c/Program Files").isDirectory)
        assertTrue(File(prefix.path, "drive_c/Program Files (x86)").isDirectory)
        assertTrue(File(prefix.path, "drive_c/windows").isDirectory)
        assertTrue(File(prefix.path, "drive_c/users").isDirectory)
    }

    @Test
    fun `createIfMissing es idempotente`() {
        val root = tempFolder.newFolder("filesDir")
        val prefix = WinePrefix.underFilesDir(root, "hollow")

        prefix.createIfMissing()
        val marker = File(prefix.path, "drive_c/windows/marker.txt").apply { writeText("x") }
        val createdAgain = prefix.createIfMissing()

        assertEquals(emptyList<String>(), createdAgain.map { it.absolutePath })
        assertTrue(marker.exists())
    }

    @Test
    fun `la ruta del prefijo es estable por juego`() {
        val root = tempFolder.newFolder("filesDir")
        val hollow = WinePrefix.underFilesDir(root, "hollow")
        val doom = WinePrefix.underFilesDir(root, "doom")
        assertEquals(File(root, "prefixes/hollow").absolutePath, hollow.path.absolutePath)
        assertEquals(File(root, "prefixes/doom").absolutePath, doom.path.absolutePath)
    }

    @Test
    fun `delete elimina el prefijo entero del juego`() {
        val root = tempFolder.newFolder("filesDir")
        val prefix = WinePrefix.underFilesDir(root, "hollow")
        prefix.createIfMissing()
        assertTrue(prefix.exists())

        assertTrue(prefix.delete())
        assertTrue(!prefix.exists())
    }
}