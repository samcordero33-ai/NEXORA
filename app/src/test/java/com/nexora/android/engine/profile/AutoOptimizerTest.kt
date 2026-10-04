package com.nexora.android.engine.profile

import com.nexora.android.engine.DeviceInfo
import com.nexora.android.engine.DeviceTier
import com.nexora.android.engine.GpuVendor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del AutoOptimizer.
 *
 * Son JVM puras: el optimizador solo ve [DeviceInfo] y [GameProfileHints], así
 * que se prueba sin Robolectric ni instrumentación. Cubren los casos que el
 * optimizador tiene que acertar por definición: un juego de estrategia con
 * DirectX 9 en un SoC de gama media con Mali, una ruta Vulkan en hardware
 * sobrado, un dispositivo de memoria reducida y el caso sin datos.
 */
class AutoOptimizerTest {

    private val optimizer = AutoOptimizer()

    /** SoC de gama media típico: 8 núcleos, 6 GB y GPU Mali con GL ES 3.2. */
    private val midRangeMali = DeviceInfo(
        manufacturer = "NEXORA",
        model = "Prueba",
        androidVersion = "13",
        apiLevel = 33,
        primaryAbi = "arm64-v8a",
        cpuCoreCount = 8,
        totalMemoryBytes = GIB * 6,
        availableMemoryBytes = GIB * 3,
        gpuVendor = GpuVendor.MALI,
        gpuRenderer = "Mali-G57 MC2",
        glEsVersion = GL_ES_32,
        isLowRamDevice = false,
    )

    private val highEnd = midRangeMali.copy(
        cpuCoreCount = 8,
        totalMemoryBytes = GIB * 12,
        availableMemoryBytes = GIB * 8,
        gpuVendor = GpuVendor.ADRENO,
        gpuRenderer = "Adreno 730",
    )

    private val lowEnd = midRangeMali.copy(
        cpuCoreCount = 4,
        totalMemoryBytes = GIB * 2,
        availableMemoryBytes = GIB,
        gpuVendor = GpuVendor.UNKNOWN,
        gpuRenderer = "",
        glEsVersion = GL_ES_30,
        isLowRamDevice = true,
    )

    @Test
    fun `un RTS con DirectX 9 en gama media con Mali va al perfil Rendimiento RTS`() {
        val hints = GameProfileHints(
            title = "Age of Empires II: Definitive Edition",
            installDirectory = "/storage/emulated/0/Games/AoE2",
            detectedApis = setOf(GraphicsApi.D3D9),
            genre = GameGenre.RTS,
            inspected = true,
        )

        val recommendation = optimizer.recommend(midRangeMali, hints)

        assertEquals(PerformanceProfile.RTS_PERFORMANCE, recommendation.profile)
        assertEquals(DeviceTier.MID, recommendation.deviceTier)
        assertTrue(
            "Debe explicar qué señal manda",
            recommendation.reasons.any { it.contains("estrategia en tiempo real", ignoreCase = true) },
        )
        assertEquals(RenderBackend.DIRECTX_9, recommendation.parameters.backend)
    }

    @Test
    fun `un juego Vulkan en hardware de gama alta va al perfil Vulkan Base`() {
        val hints = GameProfileHints(
            title = "Doom Eternal",
            detectedApis = setOf(GraphicsApi.VULKAN),
            genre = GameGenre.FPS,
            inspected = true,
        )

        val recommendation = optimizer.recommend(highEnd, hints)

        assertEquals(PerformanceProfile.VULKAN_BASE, recommendation.profile)
        assertEquals(DeviceTier.HIGH, recommendation.deviceTier)
        assertEquals(RenderBackend.VULKAN, recommendation.parameters.backend)
    }

    @Test
    fun `un dispositivo de memoria reducida va al perfil Ahorro de Energia`() {
        val recommendation = optimizer.recommend(lowEnd, GameProfileHints.EMPTY)

        assertEquals(PerformanceProfile.POWER_SAVER, recommendation.profile)
        assertEquals(DeviceTier.LOW, recommendation.deviceTier)
        assertEquals(30, recommendation.parameters.fpsCap)
        assertTrue(recommendation.parameters.videoMemoryBudgetMb <= 256)
    }

    @Test
    fun `sin datos del juego ni de la GPU se cae al perfil de compatibilidad media`() {
        val anonymousDevice = midRangeMali.copy(
            gpuVendor = GpuVendor.UNKNOWN,
            gpuRenderer = "",
        )

        val recommendation = optimizer.recommend(anonymousDevice, GameProfileHints.EMPTY)

        assertEquals(PerformanceProfile.MID_COMPATIBILITY, recommendation.profile)
        assertTrue(recommendation.reasons.isNotEmpty())
    }

    @Test
    fun `un juego Vulkan en un dispositivo sin GL ES 3_1 deriva un perfil personalizado`() {
        val deviceWithoutModernGl = midRangeMali.copy(
            totalMemoryBytes = GIB * 8,
            glEsVersion = GL_ES_30,
        )
        val hints = GameProfileHints(
            title = "Vulkan Test",
            detectedApis = setOf(GraphicsApi.VULKAN),
            genre = GameGenre.UNKNOWN,
            inspected = true,
        )

        val recommendation = optimizer.recommend(deviceWithoutModernGl, hints)

        assertEquals(PerformanceProfile.AUTO_CUSTOM, recommendation.profile)
        assertEquals(RenderBackend.DIRECTX_10_11, recommendation.parameters.backend)
        assertTrue(
            recommendation.reasons.any { it.contains("backend", ignoreCase = true) },
        )
    }

    @Test
    fun `el perfil personalizado se deriva de la clase de hardware`() {
        val hints = GameProfileHints(title = "Juego", genre = GameGenre.RPG, inspected = true)

        val low = optimizer.parameters(PerformanceProfile.AUTO_CUSTOM, lowEnd, hints)
        val high = optimizer.parameters(PerformanceProfile.AUTO_CUSTOM, highEnd, hints)

        assertEquals(ResolutionPreset.SVGA_800_600, low.resolution)
        assertEquals(30, low.fpsCap)
        assertTrue(low.vsync)
        assertEquals(ResolutionPreset.HD_PLUS_900P, high.resolution)
        assertEquals(0, high.fpsCap)
        assertEquals(CpuPriority.HIGH, high.cpuPriority)
    }

    @Test
    fun `los parametros derivados respetan el numero de nucleos`() {
        val singleCore = lowEnd.copy(cpuCoreCount = 1)

        val parameters = optimizer.parameters(
            profile = PerformanceProfile.AUTO_CUSTOM,
            device = singleCore,
            hints = GameProfileHints.EMPTY,
        )

        assertTrue(parameters.workerThreads >= 1)
        assertTrue(parameters.workerThreads <= singleCore.cpuCoreCount.coerceAtLeast(1))
    }

    @Test
    fun `un perfil cerrado conserva sus parametros definidos`() {
        val parameters = optimizer.parameters(
            profile = PerformanceProfile.RTS_PERFORMANCE,
            device = highEnd,
            hints = GameProfileHints.EMPTY,
        )

        assertEquals(PerformanceProfile.RTS_PERFORMANCE.parameters, parameters)
    }

    @Test
    fun `la peticion del ejemplo del proyecto se cumple con un juego RTS sobre Mali`() {
        // Helio G99 + Mali: 8 núcleos, 6 GB, GL ES 3.2 (gama media).
        val hints = GameProfileHints(
            title = "Command & Conquer Remastered",
            detectedApis = setOf(GraphicsApi.D3D9),
            genre = GameGenre.RTS,
            inspected = true,
        )

        val recommendation = optimizer.recommend(midRangeMali, hints)

        assertEquals(
            "4. Rendimiento RTS / DirectX 9",
            recommendation.profile.technicalName,
        )
    }

    private companion object {
        const val GIB = 1_073_741_824L
        const val GL_ES_30 = 0x0003_0000
        const val GL_ES_32 = 0x0003_0002
    }
}
