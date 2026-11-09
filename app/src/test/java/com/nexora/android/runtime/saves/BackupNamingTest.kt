package com.nexora.android.runtime.saves

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class BackupNamingTest {

    @Test
    fun `el nombre incluye juego y fecha`() {
        val epoch = Calendar.getInstance().apply {
            clear()
            set(2025, Calendar.MARCH, 12, 18, 4, 12)
        }.timeInMillis

        assertEquals(
            "nexora-saves_witcher3_20250312-180412.zip",
            BackupNaming.fileName("witcher3", epoch),
        )
    }

    @Test
    fun `ida y vuelta del sello`() {
        val epoch = Calendar.getInstance().apply {
            clear()
            set(2024, Calendar.DECEMBER, 31, 23, 59, 58)
        }.timeInMillis

        val name = BackupNaming.fileName("juego-1", epoch)
        val stamp = BackupNaming.parse(name)

        assertNotNull(stamp)
        assertEquals("juego-1", stamp!!.gameId)
        assertEquals(epoch, stamp.epochMs)
    }

    @Test
    fun `sanea caracteres peligrosos del id`() {
        assertEquals("a-b-c", BackupNaming.sanitize("a/b:c"))
        assertEquals("game", BackupNaming.sanitize("///"))
    }

    @Test
    fun `un zip ajeno no se parsea`() {
        assertNull(BackupNaming.parse("mis-partidas.zip"))
        assertNull(BackupNaming.parse("nexora-saves_juego.zip"))
        assertNull(BackupNaming.parse("nexora-saves_juego_20250312.zip"))
    }

    @Test
    fun `una fecha imposible no se acepta`() {
        assertNull(BackupNaming.parseTimestamp("20251332-180412"))
        assertNull(BackupNaming.parseTimestamp("20250312-990412"))
        assertNull(BackupNaming.parseTimestamp("basura"))
    }

    @Test
    fun `la etiqueta es legible`() {
        val epoch = Calendar.getInstance().apply {
            clear()
            set(2025, Calendar.MARCH, 12, 18, 4, 12)
        }.timeInMillis

        val label = BackupNaming.label(BackupStamp("juego", epoch))
        assertEquals("12/03/2025 · 18:04", label)
        assertTrue(label.contains("2025"))
    }
}
