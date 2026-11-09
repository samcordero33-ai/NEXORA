package com.nexora.android.runtime.saves

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SaveBackupStoreTest {

    private lateinit var tmp: File
    private lateinit var source: File
    private lateinit var store: SaveBackupStore

    private var now: Long = 1_700_000_000_000L

    @Before
    fun setUp() {
        tmp = Files.createTempDirectory("nexora-backups").toFile()
        source = File(tmp, "drive_c").apply { mkdirs() }
        File(source, "users/u/Saved Games/Juego").mkdirs()
        File(source, "users/u/Saved Games/Juego/slot1.sav").writeText("partida")

        store = SaveBackupStore(
            rootDir = File(tmp, "save_backups"),
            clock = { now },
        )
    }

    @After
    fun tearDown() {
        tmp.deleteRecursively()
    }

    @Test
    fun `crea un respaldo con el nombre convenido`() {
        val backup = store.create("juego1", source)

        assertTrue(backup.file.exists())
        assertTrue(backup.file.name.startsWith("nexora-saves_juego1_"))
        assertTrue(backup.file.name.endsWith(".zip"))
        assertEquals("juego1", backup.stamp.gameId)
        assertTrue(backup.bytes > 0L)
    }

    @Test
    fun `lista los respaldos del mas reciente al mas antiguo`() {
        val first = store.create("juego1", source)
        now += 60_000L
        val second = store.create("juego1", source)

        val list = store.list("juego1")

        assertEquals(2, list.size)
        assertEquals(second.file.name, list.first().file.name)
        assertEquals(first.file.name, list.last().file.name)
    }

    @Test
    fun `dos respaldos en el mismo segundo no se pisan`() {
        val first = store.create("juego1", source)
        val second = store.create("juego1", source)

        assertFalse(first.file.name == second.file.name)
        assertEquals(2, store.list("juego1").size)
    }

    @Test
    fun `prune conserva solo los ultimos`() {
        store.create("juego1", source)
        now += 1_000L
        store.create("juego1", source)
        now += 1_000L
        store.create("juego1", source)

        val deleted = store.prune("juego1", keep = 1)

        assertEquals(2, deleted)
        assertEquals(1, store.list("juego1").size)
    }

    @Test
    fun `borra un respaldo concreto`() {
        val backup = store.create("juego1", source)

        assertTrue(store.delete(backup))
        assertTrue(store.list("juego1").isEmpty())
    }

    @Test
    fun `los juegos no comparten carpeta`() {
        store.create("juego1", source)
        store.create("juego2", source)

        assertEquals(1, store.list("juego1").size)
        assertEquals(1, store.list("juego2").size)
        assertTrue(store.dirFor("juego1") != store.dirFor("juego2"))
    }

    @Test
    fun `un zip ajeno en la carpeta se ignora al listar`() {
        store.create("juego1", source)
        File(store.dirFor("juego1"), "partidas-manuales.zip").writeText("no es nuestro")

        assertEquals(1, store.list("juego1").size)
    }

    @Test
    fun `el zip guarda rutas relativas al drive_c`() {
        val backup = store.create("juego1", source)
        val entries = mutableListOf<String>()
        java.util.zip.ZipInputStream(backup.file.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries += entry.name
                entry = zip.nextEntry
            }
        }

        // Sin prefijo "nexora/<id>/": exportar e importar son simétricos.
        assertTrue(entries.any { it == "users/u/Saved Games/Juego/slot1.sav" })
    }
}
