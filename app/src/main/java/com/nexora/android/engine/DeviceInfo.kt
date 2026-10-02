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
)
