package com.nexora.android.runtime.monitor

/**
 * Fuente de los datos del monitor.
 *
 * Es una interfaz para que el monitor sea **testeable en JVM**: las pruebas
 * inyectan un [FakeRuntimeMonitorSource] determinista y la pantalla usa una
 * implementación que se apoya en `Choreographer` y `Debug.MemoryInfo`.
 */
interface RuntimeMonitorSource {

    /** Captura un [FrameSnapshot] con el instante actual (en nanos). */
    fun sampleFrame(nanos: Long): FrameSnapshot

    /** Captura un [ResourceSnapshot] de RAM y temperaturas estimadas. */
    fun sampleResources(): ResourceSnapshot

    /** Cierra la fuente (libera listeners). */
    fun close() = Unit
}

/** Muestra de FPS en un instante. */
data class FrameSnapshot(
    val fps: Int,
    val frameTimeMs: Double,
)

/** Muestra de recursos del dispositivo en un instante. */
data class ResourceSnapshot(
    val usedRamBytes: Long,
    val totalRamBytes: Long,
    val cpuUsagePercent: Int,
    val cpuTempCelsius: Int,
    val gpuTempCelsius: Int,
)

/**
 * Fuente de monitor falsa, pensada para `Preview` y pruebas unitarias.
 *
 * @param next se invoca cada vez que se pide una muestra, para que el test
 *   pueda inyectar valores concretos y la preview un generador aleatorio.
 */
class FakeRuntimeMonitorSource(
    private val nextFrame: () -> FrameSnapshot = {
        FrameSnapshot(fps = 60, frameTimeMs = 16.6)
    },
    private val nextResources: () -> ResourceSnapshot = {
        ResourceSnapshot(
            usedRamBytes = 1_500L * 1024L * 1024L,
            totalRamBytes = 6_000L * 1024L * 1024L,
            cpuUsagePercent = 35,
            cpuTempCelsius = 55,
            gpuTempCelsius = 62,
        )
    },
) : RuntimeMonitorSource {
    override fun sampleFrame(nanos: Long): FrameSnapshot = nextFrame()
    override fun sampleResources(): ResourceSnapshot = nextResources()
}