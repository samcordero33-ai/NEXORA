package com.nexora.android.engine.profile

import com.nexora.android.engine.GpuVendor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del catálogo de perfiles y de la detección de pistas del juego.
 *
 * Los identificadores son contrato de base de datos y el número de perfiles es
 * contrato de UI: si alguien añade un perfil sin querer o renombra un `id`,
 * estas pruebas lo dicen antes de que se pierdan datos guardados.
 */
class PerformanceProfileTest {

    @Test
    fun `hay exactamente diez perfiles`() {
        assertEquals(10, PerformanceProfile.ALL.size)
    }

    @Test
    fun `los identificadores son unicos y sobreviven a la ida y vuelta`() {
        val ids = PerformanceProfile.ALL.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { id -> assertEquals(id, PerformanceProfile.fromId(id)?.id) }
    }

    @Test
    fun `un identificador desconocido significa automatico`() {
        assertNull(PerformanceProfile.fromId("no_existe"))
        assertNull(PerformanceProfile.fromId(null))
        assertNull(PerformanceProfile.fromId(""))
    }

    @Test
    fun `los nombres de los perfiles son tecnicos y van numerados`() {
        PerformanceProfile.ALL.forEachIndexed { index, profile ->
            assertTrue(
                "El nombre debe empezar por su número: ${profile.technicalName}",
                profile.technicalName.startsWith("${index + 1}."),
            )
            assertTrue(profile.summary.isNotBlank())
        }
    }

    @Test
    fun `todo perfil define parametros completos`() {
        PerformanceProfile.ALL.forEach { profile ->
            val parameters = profile.parameters
            assertTrue(parameters.fpsCap >= 0)
            assertTrue(parameters.workerThreads > 0)
            assertTrue(parameters.audioLatencyMs > 0)
            assertTrue(parameters.videoMemoryBudgetMb > 0)
        }
    }

    @Test
    fun `las librerias junto al ejecutable delatan la API grafica`() {
        val hints = GameProfileHints.fromFiles(
            title = "Juego",
            installDirectory = "/games/Juego",
            sizeBytes = 1_000L,
            fileNames = listOf("Game.exe", "d3d11.dll", "dxgi.dll", "readme.txt"),
        )

        assertEquals(GraphicsApi.D3D11, hints.primaryApi)
        assertTrue(hints.detectedApis.contains(GraphicsApi.D3D11))
        assertTrue(hints.inspected)
    }

    @Test
    fun `la API mas moderna manda cuando hay varias`() {
        val hints = GameProfileHints.fromFiles(
            title = "Juego",
            installDirectory = null,
            sizeBytes = 0L,
            fileNames = listOf("d3d9.dll", "vulkan-1.dll"),
        )

        assertEquals(GraphicsApi.VULKAN, hints.primaryApi)
    }

    @Test
    fun `sin carpeta inspeccionada no se puede afirmar que falte la API`() {
        val hints = GameProfileHints.fromTitleOnly(
            title = "Starcraft",
            installDirectory = null,
            sizeBytes = 0L,
        )

        assertEquals(GameGenre.RTS, hints.genre)
        assertNull(hints.primaryApi)
        assertEquals(false, hints.inspected)
    }

    @Test
    fun `el genero se deduce del titulo`() {
        assertEquals(
            GameGenre.RACING,
            GameProfileHints.fromTitleOnly("Need for Speed: Most Wanted", null, 0L).genre,
        )
        assertEquals(
            GameGenre.UNKNOWN,
            GameProfileHints.fromTitleOnly("", null, 0L).genre,
        )
    }

    @Test
    fun `la GPU se clasifica por las cadenas del dispositivo`() {
        assertEquals(GpuVendor.MALI, GpuVendor.fromDeviceStrings("mt6789", "Mali-G57 MC2"))
        assertEquals(GpuVendor.ADRENO, GpuVendor.fromDeviceStrings("sm7325", "Adreno 642L"))
        assertEquals(GpuVendor.UNKNOWN, GpuVendor.fromDeviceStrings(null, ""))
    }
}
