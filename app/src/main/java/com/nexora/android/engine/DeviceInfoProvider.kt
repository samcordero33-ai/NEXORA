package com.nexora.android.engine

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Lee del sistema los datos básicos del dispositivo.
 *
 * Usa solo APIs de Android (`android.os.Build`, `android.app.ActivityManager`).
 * No hay dependencias de escritorio Java.
 *
 * Fase 4: además de los datos de siempre, deduce el fabricante de la GPU y la
 * versión de OpenGL ES, que son las dos señales que el AutoOptimizer necesita
 * para decidir entre una ruta DirectX y una Vulkan.
 */
class DeviceInfoProvider(
    private val context: Context,
) {

    /**
     * Consulta el dispositivo y devuelve una instantánea de sus datos básicos.
     *
     * La memoria disponible cambia constantemente, así que conviene volver a
     * llamar a este método cada vez que se refresque la pantalla.
     */
    fun getDeviceInfo(): DeviceInfo {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memoryInfo = ActivityManager.MemoryInfo().also { info ->
            activityManager?.getMemoryInfo(info)
        }

        val gpuRenderer = gpuRendererCandidates().joinToString(separator = " ")

        return DeviceInfo(
            manufacturer = Build.MANUFACTURER.orUnknown(),
            model = Build.MODEL.orUnknown(),
            androidVersion = Build.VERSION.RELEASE.orUnknown(),
            apiLevel = Build.VERSION.SDK_INT,
            primaryAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            cpuCoreCount = Runtime.getRuntime().availableProcessors(),
            totalMemoryBytes = memoryInfo.totalMem,
            availableMemoryBytes = memoryInfo.availMem,
            gpuVendor = GpuVendor.fromDeviceStrings(gpuRenderer),
            gpuRenderer = gpuRenderer.trim(),
            glEsVersion = activityManager?.deviceConfigurationInfo?.reqGlEsVersion ?: 0,
            isLowRamDevice = activityManager?.isLowRamDevice ?: false,
        )
    }

    /**
     * Cadenas del sistema donde suele aparecer el nombre de la GPU o del SoC.
     *
     * `SOC_MODEL` y `SOC_MANUFACTURER` son de API 31, así que se añaden solo si
     * el dispositivo las tiene; en versiones anteriores quedan `HARDWARE`,
     * `BOARD` y compañía, que casi siempre bastan.
     */
    private fun gpuRendererCandidates(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Build.SOC_MODEL)
            add(Build.SOC_MANUFACTURER)
        }
        add(Build.HARDWARE)
        add(Build.BOARD)
        add(Build.DEVICE)
        add(Build.MANUFACTURER)
        add(Build.MODEL)
    }

    // `isNullOrBlank()` tiene contrato de smart cast: en la rama else `this` ya
    // es String no nulo, así que no hace falta ninguna aserción.
    private fun String?.orUnknown(): String = if (isNullOrBlank()) VALUE_UNKNOWN else this

    private companion object {
        const val VALUE_UNKNOWN = "Desconocido"
    }
}
