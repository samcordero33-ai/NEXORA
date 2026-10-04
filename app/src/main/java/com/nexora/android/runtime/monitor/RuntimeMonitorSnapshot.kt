package com.nexora.android.runtime.monitor

/**
 * Lectura puntual del estado del dispositivo mientras un juego está corriendo.
 *
 * Es un **dato inmutable** que la UI pinta en el monitor flotante: FPS
 * calculados con el `Choreographer`, RAM con `Debug.MemoryInfo`, temperaturas
 * estimadas (el sistema no expone un sensor térmico en Android sin permisos
 * extra, así que se estiman a partir del uso de CPU y GPU).
 */
data class RuntimeMonitorSnapshot(
    val fps: Int,
    val frameTimeMs: Double,
    val usedRamBytes: Long,
    val totalRamBytes: Long,
    val cpuUsagePercent: Int,
    val cpuTempCelsius: Int,
    val gpuTempCelsius: Int,
    val elapsedMs: Long,
) {

    /**
     * Porcentaje de uso de RAM como entero 0–100. `0` si el total no se
     * conoce (caso degenerado).
     */
    val ramUsagePercent: Int
        get() = if (totalRamBytes <= 0L) 0 else ((usedRamBytes * 100L) / totalRamBytes).toInt().coerceIn(0, 100)

    companion object {
        /** Lectura "vacía" para cuando el monitor aún no tiene datos. */
        val EMPTY: RuntimeMonitorSnapshot = RuntimeMonitorSnapshot(
            fps = 0,
            frameTimeMs = 0.0,
            usedRamBytes = 0L,
            totalRamBytes = 0L,
            cpuUsagePercent = 0,
            cpuTempCelsius = 0,
            gpuTempCelsius = 0,
            elapsedMs = 0L,
        )
    }
}