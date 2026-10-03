package com.nexora.android.game.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del filtro de ejecutables.
 *
 * Son pruebas JVM puras: [ExecutableFilter] no depende de nada de Android, que
 * es precisamente por lo que está aislado en su propia clase en vez de
 * incrustado en el recorrido del escáner.
 */
class ExecutableFilterTest {

    @Test
    fun `reconoce ejecutables sin distinguir mayusculas`() {
        assertTrue(ExecutableFilter.isExecutable("juego.exe"))
        assertTrue(ExecutableFilter.isExecutable("JUEGO.EXE"))
        assertTrue(ExecutableFilter.isExecutable("Juego.Exe"))
    }

    @Test
    fun `descarta lo que no es un ejecutable`() {
        assertFalse(ExecutableFilter.isExecutable("juego.dll"))
        assertFalse(ExecutableFilter.isExecutable("manual.pdf"))
        assertFalse(ExecutableFilter.isExecutable("guardado"))
        assertFalse(ExecutableFilter.isExecutable("README"))
    }

    @Test
    fun `ignora desinstaladores e instaladores`() {
        assertTrue(ExecutableFilter.isIgnoredExecutable("unins000.exe"))
        assertTrue(ExecutableFilter.isIgnoredExecutable("unins001.exe"))
        assertTrue(ExecutableFilter.isIgnoredExecutable("uninstall.exe"))
        assertTrue(ExecutableFilter.isIgnoredExecutable("unwise.exe"))
        assertTrue(ExecutableFilter.isIgnoredExecutable("setup.exe"))
        assertTrue(ExecutableFilter.isIgnoredExecutable("install.exe"))
        assertTrue(ExecutableFilter.isIgnoredExecutable("Setup64.exe"))
    }

    @Test
    fun `ignora redistribuibles y herramientas auxiliares`() {
        assertTrue(ExecutableFilter.isIgnoredExecutable("vcredist_x64.exe"))
        assertTrue(ExecutableFilter.isIgnoredExecutable("dxsetup.exe"))
        assertTrue(ExecutableFilter.isIgnoredExecutable("oalinst.exe"))
        assertTrue(ExecutableFilter.isIgnoredExecutable("physx.exe"))
        assertTrue(ExecutableFilter.isIgnoredExecutable("dotnetfx35.exe"))
        assertTrue(ExecutableFilter.isIgnoredExecutable("crashhandler.exe"))
        assertTrue(ExecutableFilter.isIgnoredExecutable("SettingsEditor.exe"))
        assertTrue(ExecutableFilter.isIgnoredExecutable("LauncherPatcher.exe"))
    }

    @Test
    fun `conserva ejecutables que si son el juego`() {
        assertFalse(ExecutableFilter.isIgnoredExecutable("hl2.exe"))
        assertFalse(ExecutableFilter.isIgnoredExecutable("HollowKnight-Win64-Shipping.exe"))
        assertFalse(ExecutableFilter.isIgnoredExecutable("Celeste.exe"))
        assertFalse(ExecutableFilter.isIgnoredExecutable("DoomEternalx64vk.exe"))
        assertFalse(ExecutableFilter.isIgnoredExecutable("GTA5.exe"))
    }

    @Test
    fun `no desciende por carpetas de redistribuibles`() {
        assertTrue(ExecutableFilter.isIgnoredDirectory("_CommonRedist"))
        assertTrue(ExecutableFilter.isIgnoredDirectory("__redist"))
        assertTrue(ExecutableFilter.isIgnoredDirectory("DirectX"))
        assertTrue(ExecutableFilter.isIgnoredDirectory("crashdumps"))
        assertTrue(ExecutableFilter.isIgnoredDirectory("Android"))
        assertFalse(ExecutableFilter.isIgnoredDirectory("Half-Life 2"))
    }

    @Test
    fun `puntua mejor al ejecutable que coincide con su carpeta`() {
        val main = ExecutableFilter.score("hl2.exe", "Half-Life 2", 40_000_000L)
        val utilidad = ExecutableFilter.score("config.exe", "Half-Life 2", 200_000L)
        assertTrue(main > utilidad)
    }

    @Test
    fun `elimina los sufijos tecnicos del titulo`() {
        assertEquals("Hollow Knight", ExecutableFilter.deriveTitle("HollowKnight-Win64-Shipping.exe"))
        assertEquals("Celeste", ExecutableFilter.deriveTitle("Celeste.exe"))
        assertEquals("Doom Eternal", ExecutableFilter.deriveTitle("DoomEternal.exe"))
        assertEquals("Mi Juego", ExecutableFilter.deriveTitle("Mi_Juego_x64.exe"))
    }

    @Test
    fun `el titulo nunca queda vacio`() {
        assertEquals("x64", ExecutableFilter.deriveTitle("x64.exe"))
        assertEquals("Shipping", ExecutableFilter.deriveTitle("Shipping.exe"))
    }
}
