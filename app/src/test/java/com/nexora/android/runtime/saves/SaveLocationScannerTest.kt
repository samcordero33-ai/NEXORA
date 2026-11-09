package com.nexora.android.runtime.saves

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SaveLocationScannerTest {

    private lateinit var driveC: File

    @Before
    fun setUp() {
        driveC = Files.createTempDirectory("nexora-drivec").toFile()
    }

    @After
    fun tearDown() {
        driveC.deleteRecursively()
    }

    private fun file(relative: String, content: String = "x") {
        val target = File(driveC, relative)
        target.parentFile?.mkdirs()
        target.writeText(content)
    }

    @Test
    fun `encuentra carpetas de guardado en las rutas habituales`() {
        file("users/steamuser/Saved Games/Mi Juego/slot1.sav")
        file("users/steamuser/Documents/My Games/Otro/perfil.ini")

        val folders = SaveLocationScanner.scan(driveC)

        val labels = folders.map { it.label }
        assertTrue(labels.any { it.contains("Mi Juego") })
        assertTrue(labels.any { it.contains("Otro") })
    }

    @Test
    fun `no lista carpetas vacias`() {
        File(driveC, "users/steamuser/Saved Games/Vacia").mkdirs()

        val folders = SaveLocationScanner.scan(driveC)

        assertTrue(folders.isEmpty())
    }

    @Test
    fun `cuenta archivos y suma tamanos`() {
        file("users/u/Saved Games/Juego/a.sav", "12345")
        file("users/u/Saved Games/Juego/b.sav", "123")

        val folder = SaveLocationScanner.scan(driveC).first { it.label.contains("Juego") }

        assertEquals(2, folder.fileCount)
        assertEquals(8L, folder.bytes)
    }

    @Test
    fun `marca la carpeta que coincide con el nombre del juego`() {
        file("users/u/Saved Games/The Witcher 3/save.sav")
        file("users/u/Documents/My Games/OtroJuego/save.sav")

        val folders = SaveLocationScanner.scan(driveC, gameName = "The Witcher 3: Wild Hunt")

        val witcher = folders.first { it.label.contains("Witcher") }
        assertTrue(witcher.matchesGame)
        assertTrue(folders.first().label.contains("Witcher"))
    }

    @Test
    fun `descarta la carpeta padre cuando ya lista la hija`() {
        file("users/u/Saved Games/JuegoConcreto/save.sav")

        val labels = SaveLocationScanner.scan(driveC).map { it.label }

        assertTrue(labels.any { it.endsWith("JuegoConcreto") })
        assertTrue(labels.none { it.endsWith("Saved Games") })
    }

    @Test
    fun `sin prefijo devuelve lista vacia`() {
        assertTrue(SaveLocationScanner.scan(File(driveC, "no-existe")).isEmpty())
    }

    @Test
    fun `los tokens ignoran articulos y sufijos comerciales`() {
        val tokens = SaveLocationScanner.tokensOf("The Witcher 3: Wild Hunt - Game of the Year Edition")

        assertEquals(listOf("witcher", "wild", "hunt", "year"), tokens)
    }

    @Test
    fun `reconoce nombres de carpeta de guardado`() {
        assertTrue(SaveLocationScanner.looksLikeSaveFolder("SaveGames"))
        assertTrue(SaveLocationScanner.looksLikeSaveFolder("saved games"))
        assertTrue(SaveLocationScanner.looksLikeSaveFolder("profile"))
        assertTrue(!SaveLocationScanner.looksLikeSaveFolder("Program Files"))
    }
}
