package com.nexora.android.runtime.env

import com.nexora.android.engine.profile.PerformanceParameters
import com.nexora.android.engine.profile.PerformanceProfile
import com.nexora.android.engine.profile.RenderBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del compositor de entorno.
 *
 * El compositor es una **función pura**: mismas entradas, mismo resultado. Eso
 * permite validar todas las ramas (DXVK sí/no, Mesa, advertencias) sin
 * Android, sin Compose y sin disco.
 */
class EnvironmentComposerTest {

    private val composer = EnvironmentComposer()
    private val prefixPath = EnvironmentComposer.prefixPathFor("/data/nexora")

    private val baseProfile = PerformanceParameters(
        resolution = com.nexora.android.engine.profile.ResolutionPreset.HD_720P,
        fpsCap = 30,
        backend = RenderBackend.DIRECTX_9,
        cpuPriority = com.nexora.android.engine.profile.CpuPriority.NORMAL,
        workerThreads = 4,
        effectsQuality = com.nexora.android.engine.profile.QualityLevel.MEDIUM,
        textureQuality = com.nexora.android.engine.profile.QualityLevel.MEDIUM,
        vsync = true,
        audioLatencyMs = 40,
        videoMemoryBudgetMb = 512,
    )

    @Test
    fun `perfil DirectX 9 habilita DXVK con HUD desactivada por defecto`() {
        val composed = composer.compose(
            gameId = "hollow",
            exePath = "/storage/games/Hollow/hollow.exe",
            parameters = baseProfile,
            profileId = PerformanceProfile.MID_COMPATIBILITY.id,
            settings = EnvironmentSettings.DEFAULT,
            prefixPathFor = prefixPath,
        )

        assertEquals("1", composed.environment["DXVK_ENABLE_ASYNC"])
        assertEquals("1", composed.environment["DXVK_ASYNC"])
        assertEquals("0", composed.environment["DXVK_HUD"])
        assertTrue(composed.environment.containsKey("WINEPREFIX"))
        assertTrue(composed.environment.containsKey("BOX64_DYNAREC"))
        assertTrue(composed.environment.containsKey("BOX64_LOG"))
        assertTrue(composed.environment.containsKey("WINEDLLOVERRIDES"))
    }

    @Test
    fun `perfil Vulkan no activa DXVK`() {
        val params = baseProfile.copy(backend = RenderBackend.VULKAN)
        val composed = composer.compose(
            gameId = "doom",
            exePath = "/storage/doom/doom.exe",
            parameters = params,
            profileId = PerformanceProfile.VULKAN_BASE.id,
            settings = EnvironmentSettings.DEFAULT,
            prefixPathFor = prefixPath,
        )

        assertFalse("DXVK_ENABLE_ASYNC no debe estar en Vulkan", composed.environment.containsKey("DXVK_ENABLE_ASYNC"))
        assertFalse(composed.environment.containsKey("DXVK_HUD"))
    }

    @Test
    fun `driver Gallium explicito se exporta al entorno`() {
        val composed = composer.compose(
            gameId = "aoe",
            exePath = "/storage/aoe/aoe.exe",
            parameters = baseProfile,
            profileId = PerformanceProfile.DX10_11.id,
            settings = EnvironmentSettings.DEFAULT.copy(galliumDriver = GalliumDriver.TURNIP),
            prefixPathFor = prefixPath,
        )

        assertEquals("turnip", composed.environment["GALLIUM_DRIVER"])
    }

    @Test
    fun `driver Gallium en AUTO no se exporta`() {
        val composed = composer.compose(
            gameId = "aoe",
            exePath = "/storage/aoe/aoe.exe",
            parameters = baseProfile,
            profileId = PerformanceProfile.MID_COMPATIBILITY.id,
            settings = EnvironmentSettings.DEFAULT,
            prefixPathFor = prefixPath,
        )

        assertFalse(composed.environment.containsKey("GALLIUM_DRIVER"))
        assertFalse(composed.environment.containsKey("MESA_VK_WINSYS"))
    }

    @Test
    fun `WINEDEBUG vacio no aparece en el entorno`() {
        val composed = composer.compose(
            gameId = "g",
            exePath = "/x.exe",
            parameters = baseProfile,
            profileId = "p",
            settings = EnvironmentSettings.DEFAULT.copy(wineDebug = ""),
            prefixPathFor = prefixPath,
        )
        assertFalse(composed.environment.containsKey("WINEDEBUG"))
    }

    @Test
    fun `WINEDEBUG presente se exporta tal cual`() {
        val composed = composer.compose(
            gameId = "g",
            exePath = "/x.exe",
            parameters = baseProfile,
            profileId = "p",
            settings = EnvironmentSettings.DEFAULT.copy(wineDebug = "+loaddll"),
            prefixPathFor = prefixPath,
        )
        assertEquals("+loaddll", composed.environment["WINEDEBUG"])
    }

    @Test
    fun `Box64 dynarec BIG y 6 hilos usa bigblock 2`() {
        val params = baseProfile.copy(workerThreads = 8)
        val composed = composer.compose(
            gameId = "x",
            exePath = "/x.exe",
            parameters = params,
            profileId = PerformanceProfile.MAX_PERFORMANCE.id,
            settings = EnvironmentSettings.DEFAULT.copy(
                box64Dynarec = Box64DynarecSize.BIG,
            ),
            prefixPathFor = prefixPath,
        )
        assertEquals("big", composed.environment["BOX64_DYNAREC"])
        assertEquals("2", composed.environment["BOX64_DYNAREC_BIGBLOCK"])
    }

    @Test
    fun `Vulkan con driver software genera aviso`() {
        val params = baseProfile.copy(backend = RenderBackend.VULKAN)
        val composed = composer.compose(
            gameId = "doom",
            exePath = "/doom.exe",
            parameters = params,
            profileId = "p",
            settings = EnvironmentSettings.DEFAULT.copy(galliumDriver = GalliumDriver.SOFTPIPE),
            prefixPathFor = prefixPath,
        )
        assertTrue(composed.warnings.any { it.contains("Vulkan") && it.contains("software") })
    }

    @Test
    fun `la ruta del prefijo se incluye y es estable por juego`() {
        val composed = composer.compose(
            gameId = "aoe2",
            exePath = "/aoe2.exe",
            parameters = baseProfile,
            profileId = "p",
            settings = EnvironmentSettings.DEFAULT,
            prefixPathFor = prefixPath,
        )
        assertEquals("/data/nexora/prefixes/aoe2", composed.winePrefixPath)
        assertEquals("/data/nexora/prefixes/aoe2", composed.environment["WINEPREFIX"])
    }

    @Test
    fun `winetricks se serializa con comas`() {
        val composed = composer.compose(
            gameId = "g",
            exePath = "/x.exe",
            parameters = baseProfile,
            profileId = "p",
            settings = EnvironmentSettings.DEFAULT.copy(
                winetricksVerbs = listOf("d3dx9", "vcrun2019"),
            ),
            prefixPathFor = prefixPath,
        )
        assertEquals("d3dx9,vcrun2019", composed.environment["NEXORA_WINETRICKS"])
    }

    @Test
    fun `el comando incluye el exe como unico argumento`() {
        val composed = composer.compose(
            gameId = "g",
            exePath = "/games/Hero/hero.exe",
            parameters = baseProfile,
            profileId = "p",
            settings = EnvironmentSettings.DEFAULT,
            prefixPathFor = prefixPath,
        )
        assertEquals(listOf("/games/Hero/hero.exe"), composed.command)
    }

    @Test
    fun `environment normalizado ignora espacios y duplicados`() {
        val normalized = EnvironmentSettings.DEFAULT.copy(
            wineDebug = "  +seh  ",
            winetricksVerbs = listOf("d3dx9", " d3dx9 ", "vcrun2019", ""),
        ).normalized()
        assertEquals("+seh", normalized.wineDebug)
        assertEquals(listOf("d3dx9", "vcrun2019"), normalized.winetricksVerbs)
    }

    @Test
    fun `fromStorage degrada valores desconocidos al preset por defecto`() {
        val mapped = EnvironmentSettings.fromStorage(
            dxvkHud = "no-existe",
            box64Log = "tampoco",
            box64Dynarec = "raro",
            galliumDriver = "?",
            mesaVkWinsys = null,
            wineDebug = " +d3d ",
            winetricksVerbs = listOf("d3dx9"),
        )
        assertEquals(DxvkHudPreset.OFF, mapped.dxvkHud)
        assertEquals(Box64LogLevel.NONE, mapped.box64Log)
        assertEquals(Box64DynarecSize.AUTO, mapped.box64Dynarec)
        assertEquals(GalliumDriver.AUTO, mapped.galliumDriver)
        assertEquals(MesaVkWinsys.AUTO, mapped.mesaVkWinsys)
        assertEquals("+d3d", mapped.wineDebug)
        assertEquals(listOf("d3dx9"), mapped.winetricksVerbs)
        // fromStorage ya devuelve normalized.
        assertNotNull(mapped.normalized())
    }
}