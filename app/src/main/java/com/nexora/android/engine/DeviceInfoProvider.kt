package com.nexora.android.engine

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Lee del sistema los datos básicos del dispositivo.
 *
 * Usa solo APIs de Android (`android.os.Build`, `android.app.ActivityManager`).
 * No hay dependencias de escritorio Java.
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
        val memoryInfo = ActivityManager.MemoryInfo().also { info ->
            context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(info)
        }

        return DeviceInfo(
            manufacturer = Build.MANUFACTURER.orUnknown(),
            model = Build.MODEL.orUnknown(),
            androidVersion = Build.VERSION.RELEASE.orUnknown(),
            apiLevel = Build.VERSION.SDK_INT,
            primaryAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            cpuCoreCount = Runtime.getRuntime().availableProcessors(),
            totalMemoryBytes = memoryInfo.totalMem,
            availableMemoryBytes = memoryInfo.availMem,
        )
    }

    // `isNullOrBlank()` tiene contrato de smart cast: en la rama else `this` ya
    // es String no nulo, así que no hace falta ninguna aserción.
    private fun String?.orUnknown(): String = if (isNullOrBlank()) VALUE_UNKNOWN else this

    private companion object {
        const val VALUE_UNKNOWN = "Desconocido"
    }
}
