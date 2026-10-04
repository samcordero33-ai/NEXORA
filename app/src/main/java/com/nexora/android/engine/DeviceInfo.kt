package com.nexora.android.engine

/**
 * Datos básicos del dispositivo donde corre NEXORA.
 *
 * Son los mismos que se muestran en la sección "Información del dispositivo" y
 * los que en fases posteriores decidirán qué backend del runtime es viable
 * (arquitectura de 64 bits, memoria disponible, versión de Android...).
 *
 * @property manufacturer Fabricante tal y como lo reporta el sistema.
 * @property model Modelo comercial del dispositivo.
 * @property androidVersion Versión de Android visible para el usuario.
 * @property apiLevel Nivel de API del sistema.
 * @property primaryAbi ABI principal; determina qué binarios se pueden cargar.
 * @property cpuCoreCount Núcleos disponibles para la JVM.
 * @property totalMemoryBytes Memoria total del dispositivo, en bytes.
 * @property availableMemoryBytes Memoria libre en el momento de la consulta.
 * @property gpuVendor Fabricante deducido de la GPU (Mali, Adreno...).
 * @property gpuRenderer Cadena de la que se dedujo el fabricante.
 * @property glEsVersion Versión de OpenGL ES declarada por el sistema.
 * @property isLowRamDevice `true` si el sistema marca el dispositivo como de
 *   memoria reducida (`ActivityManager.isLowRamDevice`).
 */
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val primaryAbi: String,
    val cpuCoreCount: Int,
    val totalMemoryBytes: Long,
    val availableMemoryBytes: Long,
    val gpuVendor: GpuVendor = GpuVendor.UNKNOWN,
    val gpuRenderer: String = "",
    val glEsVersion: Int = 0,
    val isLowRamDevice: Boolean = false,
) {

    /** Memoria total en gibibytes, para reglas y textos informativos. */
    val totalMemoryGib: Double get() = totalMemoryBytes.toDouble() / BYTES_PER_GIB

    /** Clase de hardware, calculada por [DeviceTier.of]. */
    val tier: DeviceTier get() = DeviceTier.of(this)

    private companion object {
        const val BYTES_PER_GIB = 1_073_741_824.0
    }
}
