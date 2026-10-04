package com.nexora.android.engine

/** Un gibibyte, en bytes. */
private const val BYTES_PER_GIB = 1_073_741_824L

/** Versión de OpenGL ES 3.0, tal y como la codifica `reqGlEsVersion`. */
private const val GL_ES_30 = 0x0003_0000

/** Versión de OpenGL ES 3.1: mínimo razonable para una ruta gráfica moderna. */
private const val GL_ES_31 = 0x0003_0001

/**
 * Clase de hardware del dispositivo.
 *
 * Es la primera decisión del AutoOptimizer: antes de mirar el juego, hay que
 * saber cuánta memoria y cuántos núcleos hay, porque eso fija el techo de lo
 * que cualquier perfil puede pedir.
 *
 * Los umbrales son deliberadamente conservadores: un dispositivo de 6 GB con
 * ocho núcleos (el caso típico de un SoC de gama media con GPU Mali) queda en
 * [MID], no en [HIGH], porque la traducción de llamadas y la capa gráfica
 * compiten por esa misma memoria.
 */
enum class DeviceTier(
    /** Texto de la clase de hardware, para la UI y las razones del optimizador. */
    val label: String,
) {
    /** Poca memoria o pocos núcleos: hay que recortar resolución y efectos. */
    LOW("Gama baja"),

    /** Gama media: el caso más común. Equilibrio entre calidad y estabilidad. */
    MID("Gama media"),

    /** Memoria y núcleos de sobra, y GPU con GL ES 3.1 o superior. */
    HIGH("Gama alta"),
    ;

    companion object {

        /** Clasifica un dispositivo a partir de sus datos básicos. */
        fun of(info: DeviceInfo): DeviceTier {
            val gib = info.totalMemoryBytes.toDouble() / BYTES_PER_GIB
            val cores = info.cpuCoreCount

            if (info.isLowRamDevice || gib < 4.0 || cores <= 4) return LOW
            if (gib >= 8.0 && cores >= 8 && info.glEsVersion >= GL_ES_31) return HIGH
            return MID
        }
    }
}

/**
 * Datos de la GPU del dispositivo, ya normalizados para el AutoOptimizer.
 *
 * @property vendor Fabricante deducido; [GpuVendor.UNKNOWN] si no se pudo.
 * @property renderer Cadena original de la que se dedujo, para depuración.
 * @property glEsVersion Versión de OpenGL ES codificada por el sistema
 *   (`reqGlEsVersion`): `0x00030001` es GL ES 3.1.
 */
data class GpuInfo(
    val vendor: GpuVendor,
    val renderer: String,
    val glEsVersion: Int,
) {

    /** `true` si la GPU declara al menos OpenGL ES 3.0. */
    val supportsGlEs30: Boolean get() = glEsVersion >= GL_ES_30

    /** `true` si la GPU declara al menos OpenGL ES 3.1 (base para Vulkan). */
    val supportsGlEs31: Boolean get() = glEsVersion >= GL_ES_31
}
