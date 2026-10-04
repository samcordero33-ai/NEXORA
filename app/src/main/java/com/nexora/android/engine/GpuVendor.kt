package com.nexora.android.engine

/**
 * Fabricante de la GPU del dispositivo.
 *
 * Android **no expone** el nombre de la GPU por ninguna API pública: leer el
 * `GL_RENDERER` exige crear un contexto OpenGL, algo que NEXORA no hace solo
 * para rellenar un dato informativo. En su lugar se clasifica la GPU a partir
 * de las cadenas que el sistema sí publica (`Build.SOC_MODEL`,
 * `Build.HARDWARE`, `Build.BOARD`...), que es lo que hacen la mayoría de las
 * aplicaciones de diagnóstico.
 *
 * La detección es una **pista** para el AutoOptimizer, no una verdad absoluta:
 * si no hay coincidencia, el resultado es [UNKNOWN] y las reglas que dependen
 * de la GPU simplemente no puntúan.
 */
enum class GpuVendor(
    /** Nombre corto, tal y como se muestra en la UI. */
    val label: String,
) {
    MALI("Mali"),
    ADRENO("Adreno"),
    POWERVR("PowerVR"),
    TEGRA("Tegra"),
    INTEL("Intel"),
    UNKNOWN("Desconocida"),
    ;

    companion object {

        /**
         * Clasifica la GPU a partir de las cadenas de identidad del dispositivo.
         *
         * Se hacen dos pasadas: primero los nombres explícitos de GPU y, si no
         * hay coincidencia, la familia del SoC (un Helio o un Dimensity llevan
         * Mali; un Snapdragon lleva Adreno). El orden importa: un dispositivo
         * puede mencionar ambas cosas y la más específica es la de la GPU.
         */
        fun fromDeviceStrings(vararg sources: String?): GpuVendor {
            val haystack = sources
                .filterNotNull()
                .joinToString(separator = " ")
                .lowercase()
            if (haystack.isBlank()) return UNKNOWN

            GPU_KEYWORDS.forEach { (vendor, keywords) ->
                if (keywords.any { haystack.contains(it) }) return vendor
            }
            SOC_KEYWORDS.forEach { (vendor, keywords) ->
                if (keywords.any { haystack.contains(it) }) return vendor
            }
            return UNKNOWN
        }

        /** Nombre explícito de GPU -> fabricante. */
        private val GPU_KEYWORDS: List<Pair<GpuVendor, List<String>>> = listOf(
            MALI to listOf("mali"),
            ADRENO to listOf("adreno"),
            POWERVR to listOf("powervr", "imagination", "rogue", "img "),
            TEGRA to listOf("tegra", "nvidia", "geforce"),
            INTEL to listOf("intel", "iris", "gen9", "gen11", "gen12"),
        )

        /** Familia del SoC -> fabricante de su GPU integrada. */
        private val SOC_KEYWORDS: List<Pair<GpuVendor, List<String>>> = listOf(
            MALI to listOf("helio", "dimensity", "mt65", "mt66", "mt67", "mt68", "mt69", "exynos"),
            ADRENO to listOf("snapdragon", "msm", "sdm", "sm6", "sm7", "sm8"),
            TEGRA to listOf("tegra"),
        )
    }
}
