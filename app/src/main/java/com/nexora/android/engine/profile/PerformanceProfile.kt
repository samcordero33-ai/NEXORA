package com.nexora.android.engine.profile

/**
 * Perfiles de rendimiento y compatibilidad de NEXORA.
 *
 * Un perfil es un **conjunto de parámetros** que el runtime aplicará a un juego
 * cuando llegue el motor de ejecución (Fase 5 y posteriores). Aquí no se lanza
 * nada: son datos, y por eso viven en Kotlin puro, sin ninguna dependencia de
 * Android, para poder probarlos en JVM.
 *
 * Los nombres son **técnicos** y describen la configuración (API gráfica,
 * prioridad de CPU, objetivo de la sesión), nunca un modelo comercial de
 * dispositivo.
 */

/** Backend gráfico al que se traducen las llamadas del juego. */
enum class RenderBackend(
    /** Nombre técnico mostrado en la UI. */
    val label: String,
) {
    DIRECTX_9("DirectX 9"),
    DIRECTX_10_11("DirectX 10/11"),
    VULKAN("Vulkan"),
    OPENGL("OpenGL"),
    AUTO("Automático"),
}

/** Prioridad de CPU que el runtime pedirá para el proceso del juego. */
enum class CpuPriority(
    val label: String,
) {
    LOW("Baja"),
    NORMAL("Normal"),
    HIGH("Alta"),
    MAX("Máxima"),
}

/** Nivel de calidad de efectos y de texturas. */
enum class QualityLevel(
    val label: String,
) {
    LOW("Baja"),
    MEDIUM("Media"),
    HIGH("Alta"),
}

/**
 * Resolución de render sugerida.
 *
 * Son resoluciones de ventana de escritorio, que es el espacio en el que el
 * juego cree estar trabajando; el runtime las escala a la pantalla del
 * dispositivo. `NATIVE` significa "la que tenga el panel".
 */
enum class ResolutionPreset(
    val width: Int,
    val height: Int,
) {
    VGA_640_480(640, 480),
    SVGA_800_600(800, 600),
    XGA_1024_768(1024, 768),
    HD_720P(1280, 720),
    HD_PLUS_900P(1600, 900),
    FHD_1080P(1920, 1080),
    NATIVE(0, 0),
    ;

    /** Texto de la resolución; `NATIVE` no tiene dimensiones propias. */
    val label: String
        get() = if (this == NATIVE) "Nativa" else "$width × $height"
}

/**
 * Parámetros de un perfil de rendimiento.
 *
 * @property resolution Resolución de render sugerida.
 * @property fpsCap Límite de fotogramas por segundo; `0` es sin límite.
 * @property backend Backend gráfico al que se traducen las llamadas.
 * @property cpuPriority Prioridad del proceso del juego.
 * @property workerThreads Hilos dedicados a traducción y trabajo en segundo
 *   plano. Se acota al número de núcleos reales.
 * @property effectsQuality Calidad de efectos (sombras, postproceso).
 * @property textureQuality Calidad de texturas.
 * @property vsync Sincronización vertical: evita tearing a cambio de un techo
 *   de fotogramas.
 * @property audioLatencyMs Latencia objetivo del buffer de audio.
 * @property videoMemoryBudgetMb Presupuesto de memoria gráfica compartida.
 */
data class PerformanceParameters(
    val resolution: ResolutionPreset,
    val fpsCap: Int,
    val backend: RenderBackend,
    val cpuPriority: CpuPriority,
    val workerThreads: Int,
    val effectsQuality: QualityLevel,
    val textureQuality: QualityLevel,
    val vsync: Boolean,
    val audioLatencyMs: Int,
    val videoMemoryBudgetMb: Int,
)

/**
 * Los diez perfiles genéricos de NEXORA.
 *
 * @property id Identificador estable: es lo que se guarda en base de datos, así
 *   que **no se puede renombrar** sin migración.
 * @property technicalName Nombre técnico numerado, tal y como se muestra.
 * @property summary Descripción de una línea del objetivo del perfil.
 * @property parameters Parámetros por defecto del perfil. El perfil
 *   [AUTO_CUSTOM] los sustituye por los que calcule el AutoOptimizer.
 */
enum class PerformanceProfile(
    val id: String,
    val technicalName: String,
    val summary: String,
    val parameters: PerformanceParameters,
) {

    RETRO_DX9(
        id = "retro_dx9",
        technicalName = "1. Retro / DirectX 9",
        summary = "Juegos de la era DirectX 9: backend ligero y resolución contenida.",
        parameters = PerformanceParameters(
            resolution = ResolutionPreset.XGA_1024_768,
            fpsCap = 60,
            backend = RenderBackend.DIRECTX_9,
            cpuPriority = CpuPriority.NORMAL,
            workerThreads = 2,
            effectsQuality = QualityLevel.LOW,
            textureQuality = QualityLevel.MEDIUM,
            vsync = true,
            audioLatencyMs = 40,
            videoMemoryBudgetMb = 256,
        ),
    ),

    DX10_11(
        id = "dx10_11",
        technicalName = "2. DirectX 10/11",
        summary = "Ruta DirectX 10/11 para juegos que enlazan d3d10 o d3d11.",
        parameters = PerformanceParameters(
            resolution = ResolutionPreset.HD_720P,
            fpsCap = 60,
            backend = RenderBackend.DIRECTX_10_11,
            cpuPriority = CpuPriority.HIGH,
            workerThreads = 4,
            effectsQuality = QualityLevel.MEDIUM,
            textureQuality = QualityLevel.MEDIUM,
            vsync = true,
            audioLatencyMs = 30,
            videoMemoryBudgetMb = 512,
        ),
    ),

    VULKAN_BASE(
        id = "vulkan_base",
        technicalName = "3. Vulkan Base",
        summary = "Traducción a Vulkan: menos sobrecarga de CPU por llamada.",
        parameters = PerformanceParameters(
            resolution = ResolutionPreset.HD_720P,
            fpsCap = 60,
            backend = RenderBackend.VULKAN,
            cpuPriority = CpuPriority.HIGH,
            workerThreads = 4,
            effectsQuality = QualityLevel.MEDIUM,
            textureQuality = QualityLevel.HIGH,
            vsync = true,
            audioLatencyMs = 30,
            videoMemoryBudgetMb = 768,
        ),
    ),

    RTS_PERFORMANCE(
        id = "rts_performance",
        technicalName = "4. Rendimiento RTS / DirectX 9",
        summary = "Estrategia en tiempo real: CPU por delante de efectos, fotogramas estables.",
        parameters = PerformanceParameters(
            resolution = ResolutionPreset.XGA_1024_768,
            fpsCap = 30,
            backend = RenderBackend.DIRECTX_9,
            cpuPriority = CpuPriority.HIGH,
            workerThreads = 6,
            effectsQuality = QualityLevel.LOW,
            textureQuality = QualityLevel.LOW,
            vsync = false,
            audioLatencyMs = 40,
            videoMemoryBudgetMb = 384,
        ),
    ),

    MAX_PERFORMANCE(
        id = "max_performance",
        technicalName = "5. Máximo Rendimiento",
        summary = "Sin techo de fotogramas ni recorte de efectos: solo para hardware sobrado.",
        parameters = PerformanceParameters(
            resolution = ResolutionPreset.FHD_1080P,
            fpsCap = 0,
            backend = RenderBackend.AUTO,
            cpuPriority = CpuPriority.MAX,
            workerThreads = 8,
            effectsQuality = QualityLevel.HIGH,
            textureQuality = QualityLevel.HIGH,
            vsync = false,
            audioLatencyMs = 20,
            videoMemoryBudgetMb = 1024,
        ),
    ),

    POWER_SAVER(
        id = "power_saver",
        technicalName = "6. Ahorro de Energía",
        summary = "Resolución baja, 30 fps y CPU contenida para sesiones largas.",
        parameters = PerformanceParameters(
            resolution = ResolutionPreset.SVGA_800_600,
            fpsCap = 30,
            backend = RenderBackend.DIRECTX_9,
            cpuPriority = CpuPriority.LOW,
            workerThreads = 2,
            effectsQuality = QualityLevel.LOW,
            textureQuality = QualityLevel.LOW,
            vsync = true,
            audioLatencyMs = 60,
            videoMemoryBudgetMb = 192,
        ),
    ),

    MID_COMPATIBILITY(
        id = "mid_compatibility",
        technicalName = "7. Compatibilidad Media",
        summary = "Opción segura cuando no hay datos concluyentes del juego o del hardware.",
        parameters = PerformanceParameters(
            resolution = ResolutionPreset.HD_720P,
            fpsCap = 30,
            backend = RenderBackend.AUTO,
            cpuPriority = CpuPriority.NORMAL,
            workerThreads = 4,
            effectsQuality = QualityLevel.MEDIUM,
            textureQuality = QualityLevel.MEDIUM,
            vsync = true,
            audioLatencyMs = 40,
            videoMemoryBudgetMb = 384,
        ),
    ),

    CPU_FOCUS_BOX64(
        id = "cpu_focus_box64",
        technicalName = "8. CPU Focus / Box64",
        summary = "Carga ligera para la GPU y toda la CPU disponible para la traducción.",
        parameters = PerformanceParameters(
            resolution = ResolutionPreset.XGA_1024_768,
            fpsCap = 30,
            backend = RenderBackend.DIRECTX_9,
            cpuPriority = CpuPriority.MAX,
            workerThreads = 8,
            effectsQuality = QualityLevel.LOW,
            textureQuality = QualityLevel.LOW,
            vsync = true,
            audioLatencyMs = 40,
            videoMemoryBudgetMb = 256,
        ),
    ),

    LOW_LATENCY_AUDIO(
        id = "low_latency_audio",
        technicalName = "9. Audio Latencia Baja",
        summary = "Buffer de audio corto para juegos de ritmo, conducción o disparos.",
        parameters = PerformanceParameters(
            resolution = ResolutionPreset.HD_720P,
            fpsCap = 60,
            backend = RenderBackend.AUTO,
            cpuPriority = CpuPriority.HIGH,
            workerThreads = 4,
            effectsQuality = QualityLevel.MEDIUM,
            textureQuality = QualityLevel.MEDIUM,
            vsync = true,
            audioLatencyMs = 16,
            videoMemoryBudgetMb = 512,
        ),
    ),

    AUTO_CUSTOM(
        id = "auto_custom",
        technicalName = "10. Auto Personalizado",
        summary = "Parámetros calculados por AutoOptimizer a partir del hardware y del juego.",
        parameters = PerformanceParameters(
            resolution = ResolutionPreset.HD_720P,
            fpsCap = 60,
            backend = RenderBackend.AUTO,
            cpuPriority = CpuPriority.NORMAL,
            workerThreads = 4,
            effectsQuality = QualityLevel.MEDIUM,
            textureQuality = QualityLevel.MEDIUM,
            vsync = true,
            audioLatencyMs = 30,
            videoMemoryBudgetMb = 512,
        ),
    ),
    ;

    companion object {

        /** Todos los perfiles, en el orden en que se muestran. */
        val ALL: List<PerformanceProfile> get() = entries.toList()

        /**
         * Perfil al que se cae si un identificador guardado ya no existe.
         *
         * Es el más conservador de los que no asumen nada del juego.
         */
        val FALLBACK: PerformanceProfile = MID_COMPATIBILITY

        /**
         * Traduce el identificador guardado en base de datos a un perfil.
         *
         * Devuelve `null` si el texto es nulo, vacío o desconocido: `null` es
         * precisamente el valor que significa "decide AutoOptimizer", así que un
         * dato corrupto degrada a automático en vez de romper la UI.
         */
        fun fromId(id: String?): PerformanceProfile? =
            if (id.isNullOrBlank()) null else entries.firstOrNull { it.id == id }
    }
}