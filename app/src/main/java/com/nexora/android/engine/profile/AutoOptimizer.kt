package com.nexora.android.engine.profile

import com.nexora.android.engine.DeviceInfo
import com.nexora.android.engine.DeviceTier
import com.nexora.android.engine.GpuVendor

/**
 * Recomendación del AutoOptimizer para un juego.
 *
 * @property profile Perfil recomendado. [PerformanceProfile.AUTO_CUSTOM]
 *   significa que ningún perfil cerrado encajaba y los parámetros se han
 *   calculado expresamente.
 * @property parameters Parámetros efectivos: los del perfil, o los derivados
 *   cuando el perfil es [PerformanceProfile.AUTO_CUSTOM].
 * @property reasons Motivos técnicos de la recomendación, en orden de peso.
 *   Se muestran en la UI para que la sugerencia se pueda discutir.
 * @property confidence Confianza de la recomendación, de 0 a 100.
 * @property deviceTier Clase de hardware en la que se apoya la decisión.
 */
data class ProfileRecommendation(
    val profile: PerformanceProfile,
    val parameters: PerformanceParameters,
    val reasons: List<String>,
    val confidence: Int,
    val deviceTier: DeviceTier,
)

/**
 * AutoOptimizer: elige el perfil de rendimiento para un juego.
 *
 * Funciona por **puntuación**: cada perfil suma puntos según las señales del
 * dispositivo (memoria, núcleos, GPU, GL ES) y del juego (API gráfica, género),
 * y gana el que más sume. Cada punto lleva su motivo, así que la recomendación
 * se puede explicar en la pantalla de detalle en vez de ser una caja negra.
 *
 * Hay dos salidas especiales:
 *
 * - Si el perfil ganador pide un backend que el dispositivo no puede servir
 *   (Vulkan sin GL ES 3.1), se descarta y se deriva un perfil personalizado.
 * - Si hay empate por debajo de una señal fuerte, tampoco hay ganador claro y se
 *   deriva el perfil personalizado ([PerformanceProfile.AUTO_CUSTOM]).
 *
 * La clase es estado puro: mismo dispositivo y mismas pistas, misma respuesta.
 * No toca Android, así que se prueba en JVM.
 */
class AutoOptimizer {

    /**
     * Recomienda un perfil para un juego en un dispositivo.
     *
     * @param device Datos del dispositivo, tal y como los lee
     *   `DeviceInfoProvider`.
     * @param hints Pistas del juego, deducidas por `GameProfileInspector`.
     */
    fun recommend(device: DeviceInfo, hints: GameProfileHints): ProfileRecommendation {
        val tier = DeviceTier.of(device)
        val ranked = PerformanceProfile.ALL
            .filter { it != PerformanceProfile.AUTO_CUSTOM }
            .map { profile -> profile to score(profile, device, hints, tier) }
            .sortedByDescending { (_, score) -> score.points }

        val (winner, winnerScore) = ranked.first()
        val runnerUpScore = ranked.getOrNull(1)?.second?.points ?: 0
        val tied = runnerUpScore == winnerScore.points

        return if (tied && winnerScore.points < STRONG_SIGNAL) {
            custom(device, hints, tier, winnerScore.points)
        } else if (!isFeasible(winner, device)) {
            custom(
                device = device,
                hints = hints,
                tier = tier,
                points = winnerScore.points,
                extraReason = "El perfil «${winner.technicalName}» pide un backend " +
                    "que este dispositivo no puede servir: se deriva una alternativa.",
            )
        } else {
            ProfileRecommendation(
                profile = winner,
                parameters = winner.parameters,
                reasons = winnerScore.reasons,
                confidence = confidenceOf(winnerScore.points),
                deviceTier = tier,
            )
        }
    }

    /**
     * Parámetros efectivos de un perfil en un dispositivo concreto.
     *
     * Solo [PerformanceProfile.AUTO_CUSTOM] se calcula: el resto lleva sus
     * parámetros ya definidos.
     */
    fun parameters(
        profile: PerformanceProfile,
        device: DeviceInfo,
        hints: GameProfileHints,
    ): PerformanceParameters = if (profile == PerformanceProfile.AUTO_CUSTOM) {
        deriveParameters(device, hints, DeviceTier.of(device))
    } else {
        profile.parameters
    }

    /**
     * Comprueba que el backend del perfil es viable en el dispositivo.
     *
     * Vulkan exige GL ES 3.1: por debajo de eso, la ruta gráfica moderna no
     * existe y recomendarla solo produciría una sesión que no arranca.
     */
    private fun isFeasible(profile: PerformanceProfile, device: DeviceInfo): Boolean = when (
        profile.parameters.backend
    ) {
        RenderBackend.VULKAN -> device.glEsVersion >= GL_ES_31
        else -> true
    }

    private fun custom(
        device: DeviceInfo,
        hints: GameProfileHints,
        tier: DeviceTier,
        points: Int,
        extraReason: String? = null,
    ): ProfileRecommendation {
        val reasons = buildList {
            extraReason?.let(::add)
            add("Sin una señal dominante: los parámetros se derivan del hardware.")
            add("Memoria: ${"%.1f".format(device.totalMemoryGib)} GB · ${device.cpuCoreCount} núcleos.")
            add("GPU: ${device.gpuVendor.label} · clase ${tier.label}.")
            hints.primaryApi?.let { add("API detectada: ${it.label}.") }
            if (hints.genre != GameGenre.UNKNOWN) add("Género detectado: ${hints.genre.label}.")
        }
        return ProfileRecommendation(
            profile = PerformanceProfile.AUTO_CUSTOM,
            parameters = deriveParameters(device, hints, tier),
            reasons = reasons,
            confidence = (confidenceOf(points) / 2).coerceAtLeast(MIN_CUSTOM_CONFIDENCE),
            deviceTier = tier,
        )
    }

    /**
     * Parámetros derivados del hardware y del juego.
     *
     * Es el "perfil 10" cuando ninguno de los nueve cerrados encaja: se parte de
     * la clase de hardware y se corrige con lo que el juego pide.
     */
    private fun deriveParameters(
        device: DeviceInfo,
        hints: GameProfileHints,
        tier: DeviceTier,
    ): PerformanceParameters {
        val cores = device.cpuCoreCount.coerceAtLeast(1)
        val backend = when (hints.primaryApi) {
            GraphicsApi.VULKAN -> if (device.glEsVersion >= GL_ES_31) {
                RenderBackend.VULKAN
            } else {
                RenderBackend.DIRECTX_10_11
            }

            GraphicsApi.D3D11, GraphicsApi.D3D10 -> RenderBackend.DIRECTX_10_11
            GraphicsApi.D3D9 -> RenderBackend.DIRECTX_9
            GraphicsApi.OPENGL -> RenderBackend.OPENGL
            null -> RenderBackend.AUTO
        }
        val quality = when (tier) {
            DeviceTier.LOW -> QualityLevel.LOW
            DeviceTier.MID -> QualityLevel.MEDIUM
            DeviceTier.HIGH -> QualityLevel.HIGH
        }
        // Un juego de estrategia o de conducción sufre más un tirón que una
        // bajada de resolución: ahí el techo de fotogramas manda.
        val fpsCap = when {
            tier == DeviceTier.LOW -> 30
            hints.genre == GameGenre.RTS -> 30
            tier == DeviceTier.HIGH -> 0
            else -> 60
        }

        return PerformanceParameters(
            resolution = when (tier) {
                DeviceTier.LOW -> ResolutionPreset.SVGA_800_600
                DeviceTier.MID -> ResolutionPreset.HD_720P
                DeviceTier.HIGH -> ResolutionPreset.HD_PLUS_900P
            },
            fpsCap = fpsCap,
            backend = backend,
            cpuPriority = when (tier) {
                DeviceTier.LOW -> CpuPriority.NORMAL
                DeviceTier.MID, DeviceTier.HIGH -> CpuPriority.HIGH
            },
            workerThreads = when (tier) {
                DeviceTier.LOW -> cores.coerceAtMost(2)
                DeviceTier.MID -> cores.coerceAtMost(4)
                DeviceTier.HIGH -> cores.coerceAtMost(8)
            }.coerceAtLeast(1),
            effectsQuality = quality,
            textureQuality = quality,
            vsync = tier != DeviceTier.HIGH,
            audioLatencyMs = when {
                hints.genre == GameGenre.RACING || hints.genre == GameGenre.FPS -> 16
                tier == DeviceTier.LOW -> 60
                tier == DeviceTier.HIGH -> 20
                else -> 40
            },
            videoMemoryBudgetMb = when (tier) {
                DeviceTier.LOW -> 192
                DeviceTier.MID -> 512
                DeviceTier.HIGH -> 1024
            },
        )
    }

    /**
     * Puntúa un perfil.
     *
     * Cada regla suma (o resta) puntos y aporta su motivo. Las reglas son
     * deliberadamente independientes: así se puede cambiar el peso de una señal
     * sin reescribir las demás.
     */
    private fun score(
        profile: PerformanceProfile,
        device: DeviceInfo,
        hints: GameProfileHints,
        tier: DeviceTier,
    ): Score {
        val reasons = mutableListOf<String>()
        var points = 0

        fun add(value: Int, reason: String) {
            points += value
            if (value > 0) reasons += reason
        }

        val api = hints.primaryApi
        val gpu = device.gpuVendor
        val mobileGpu = gpu == GpuVendor.MALI || gpu == GpuVendor.ADRENO

        when (profile) {
            PerformanceProfile.RETRO_DX9 -> {
                if (api == GraphicsApi.D3D9 || api == GraphicsApi.D3D10) {
                    add(4, "El juego se acompaña de ${api.label}: ruta DirectX 9/10.")
                }
                if (tier != DeviceTier.HIGH) {
                    add(2, "Hardware de ${tier.label}: el backend más ligero es el más estable.")
                }
                if (mobileGpu) {
                    add(1, "GPU ${gpu.label}: la ruta DirectX 9 es la más probada en móviles.")
                }
                if (device.cpuCoreCount <= 4) {
                    add(1, "Pocos núcleos: menos carga de traducción gráfica.")
                }
                if (hints.genre == GameGenre.RTS) add(-2, "")
            }

            PerformanceProfile.DX10_11 -> {
                if (api == GraphicsApi.D3D11 || api == GraphicsApi.D3D10) {
                    add(4, "El juego enlaza ${api.label}.")
                }
                if (device.totalMemoryGib >= 6.0) {
                    add(2, "Memoria suficiente para la ruta DirectX 10/11.")
                }
                if (device.glEsVersion >= GL_ES_30) {
                    add(1, "GPU con OpenGL ES 3.0 o superior.")
                }
                if (tier == DeviceTier.LOW) add(-2, "")
            }

            PerformanceProfile.VULKAN_BASE -> {
                // La capacidad del dispositivo solo cuenta si el juego la puede
                // aprovechar: sin API Vulkan en el juego, el hardware no es un
                // motivo para recomendar esta ruta.
                if (api == GraphicsApi.VULKAN) {
                    add(5, "El juego enlaza Vulkan.")
                    if (device.glEsVersion >= GL_ES_31) {
                        add(2, "La GPU declara OpenGL ES 3.1: la ruta Vulkan es viable.")
                    }
                    if (device.totalMemoryGib >= 6.0) {
                        add(2, "Memoria suficiente para la sobrecarga de Vulkan.")
                    }
                    if (tier == DeviceTier.HIGH) {
                        add(1, "Hardware de gama alta: Vulkan aprovecha los núcleos sobrantes.")
                    }
                    if (device.glEsVersion < GL_ES_31) add(-3, "")
                } else if (api == null && tier == DeviceTier.HIGH && device.glEsVersion >= GL_ES_31) {
                    add(1, "Hardware con Vulkan disponible y juego sin API detectada.")
                }
            }

            PerformanceProfile.RTS_PERFORMANCE -> {
                if (hints.genre == GameGenre.RTS) {
                    add(5, "Estrategia en tiempo real: manda la CPU y la estabilidad de fotogramas.")
                }
                if (api == GraphicsApi.D3D9) {
                    add(2, "DirectX 9 es la API habitual de este género.")
                }
                if (mobileGpu && tier != DeviceTier.LOW) {
                    add(2, "GPU ${gpu.label} de ${tier.label}: 30 fps estables antes que efectos.")
                }
                if (device.cpuCoreCount >= 6) {
                    add(1, "Núcleos suficientes para unidades y pathfinding.")
                }
                if (hints.genre != GameGenre.RTS) add(-3, "")
            }

            PerformanceProfile.MAX_PERFORMANCE -> {
                if (tier == DeviceTier.HIGH) {
                    add(4, "Hardware de gama alta: hay margen para no recortar nada.")
                }
                if (device.cpuCoreCount >= 8) {
                    add(2, "Ocho núcleos o más.")
                }
                if (!device.isLowRamDevice) {
                    add(1, "El sistema no marca el dispositivo como de memoria reducida.")
                }
                if (tier == DeviceTier.LOW) add(-4, "")
            }

            PerformanceProfile.POWER_SAVER -> {
                if (device.isLowRamDevice) {
                    add(4, "El sistema marca el dispositivo como de memoria reducida.")
                }
                if (tier == DeviceTier.LOW) {
                    add(3, "Hardware de gama baja: recortar resolución es la única vía.")
                }
                if (device.cpuCoreCount <= 4) {
                    add(2, "Pocos núcleos: menos hilos y menos calor.")
                }
                if (device.totalMemoryGib <= 4.0) {
                    add(2, "Memoria total ajustada.")
                }
            }

            PerformanceProfile.MID_COMPATIBILITY -> {
                add(2, "Opción segura: no asume nada del juego ni del hardware.")
                if (gpu == GpuVendor.UNKNOWN) {
                    add(1, "GPU sin identificar: mejor no forzar un backend concreto.")
                }
                if (api == null) {
                    add(1, "No se detectó ninguna API gráfica junto al ejecutable.")
                }
            }

            PerformanceProfile.CPU_FOCUS_BOX64 -> {
                if (api == GraphicsApi.OPENGL) {
                    add(3, "El juego usa OpenGL: la traducción recae en la CPU.")
                }
                if (api == null && !hints.inspected) {
                    add(1, "Sin datos de la carpeta: se prioriza la traducción en CPU.")
                }
                if (hints.genre == GameGenre.SIMULATION) {
                    add(3, "Simulación: carga de CPU sostenida, poca exigencia gráfica.")
                }
                if (device.cpuCoreCount >= 8) {
                    add(2, "Núcleos de sobra para hilos de traducción.")
                }
                if (tier == DeviceTier.LOW) add(-2, "")
            }

            PerformanceProfile.LOW_LATENCY_AUDIO -> {
                if (hints.genre == GameGenre.RACING) {
                    add(4, "Conducción: el retardo de audio se nota más que un efecto.")
                }
                if (hints.genre == GameGenre.FPS) {
                    add(2, "Disparos: conviene un buffer de audio corto.")
                }
                if (tier != DeviceTier.LOW) {
                    add(1, "Hardware con margen para un buffer de audio corto.")
                }
            }

            // AUTO_CUSTOM no compite: es la salida cuando ninguno convence.
            PerformanceProfile.AUTO_CUSTOM -> Unit
        }

        return Score(points = points, reasons = reasons)
    }

    private fun confidenceOf(points: Int): Int =
        (points * 100 / STRONG_SIGNAL).coerceIn(0, 100)

    /** Puntuación de un perfil para un juego. */
    private data class Score(
        val points: Int,
        val reasons: List<String>,
    )

    private companion object {

        /** OpenGL ES 3.0, tal y como lo codifica `reqGlEsVersion`. */
        const val GL_ES_30 = 0x0003_0000

        /** OpenGL ES 3.1: mínimo para considerar viable una ruta Vulkan. */
        const val GL_ES_31 = 0x0003_0001

        /** Puntuación a partir de la cual la señal se considera concluyente. */
        const val STRONG_SIGNAL = 5

        /** Confianza mínima de un perfil personalizado derivado. */
        const val MIN_CUSTOM_CONFIDENCE = 25
    }
}
