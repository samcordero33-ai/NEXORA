package com.nexora.android.runtime.env

/**
 * Variables de entorno que NEXORA entiende para configurar el motor de
 * traducción (Wine + Box64/FEX-Emu + DXVK + Mesa/Turnip).
 *
 * Cada tipo enum tiene **dos presentaciones**: el valor que se vuelca al
 * entorno (el que entiende el binario de Windows o de Linux) y una etiqueta
 * legible en español para la UI. Así la capa de UI y la capa de runtime
 * hablan idiomas distintos sin tocarse.
 *
 * Es deliberadamente **delgado**: aquí solo se modelan las variables que el
 * usuario puede ajustar. Los valores internos que el motor pueda inyectar
 * (por ejemplo rutas a `wine` o `box64`) se calculan en [EnvironmentComposer]
 * y nunca forman parte de este enumerado.
 */

/** Preset de la HUD de DXVK; "1" muestra solo FPS, "full" muestra todo. */
enum class DxvkHudPreset(val wireValue: String, val label: String) {
    OFF("0", "Desactivada"),
    FPS("1", "Solo FPS"),
    BASIC("devinfo", "Información del dispositivo"),
    FRAMETIMES("frametimes", "Tiempos por fotograma"),
    ALL("full", "Estadísticas completas"),
    ;

    companion object {
        /** Devuelve el preset cuyo `wireValue` coincide; `OFF` si no se reconoce. */
        fun fromWireValue(value: String?): DxvkHudPreset =
            value?.let { v -> entries.firstOrNull { it.wireValue == v } } ?: OFF
    }
}

/** Habilita el log de Box64 y su nivel. */
enum class Box64LogLevel(val wireValue: String, val label: String) {
    NONE("0", "Sin log"),
    INFO("1", "Información"),
    DEBUG("2", "Depuración"),
    ;

    companion object {
        fun fromWireValue(value: String?): Box64LogLevel =
            value?.let { v -> entries.firstOrNull { it.wireValue == v } } ?: NONE
    }
}

/** Tamaño del caché de recompilación dinámica de Box64. */
enum class Box64DynarecSize(val wireValue: String, val label: String) {
    SMALL("small", "Pequeño (poco consumo de RAM)"),
    MEDIUM("medium", "Medio"),
    BIG("big", "Grande (más rápido, más RAM)"),
    AUTO("1", "Decisión automática"),
    ;

    companion object {
        fun fromWireValue(value: String?): Box64DynarecSize =
            value?.let { v -> entries.firstOrNull { it.wireValue == v } } ?: AUTO
    }
}

/** Driver Gallium que Mesa cargará para OpenGL/Vulkan. */
enum class GalliumDriver(val wireValue: String, val label: String) {
    AUTO("", "Por defecto del sistema"),
    TURNIP("turnip", "Turnip (Adreno, Vulkan)"),
    FREEDRENO("freedreno", "Freedreno (Adreno, OpenGL)"),
    PANFROST("panfrost", "Panfrost (Mali)"),
    VIRGL("virgl", "Virgl (paso intermedio)"),
    SOFTPIPE("softpipe", "Software (sin GPU)"),
    LLVMPIPE("llvmpipe", "LLVM (CPU)"),
    ;

    companion object {
        fun fromWireValue(value: String?): GalliumDriver =
            value?.let { v -> entries.firstOrNull { it.wireValue == v } } ?: AUTO
    }
}

/** Winsys que Mesa elige para Vulkan en Android. */
enum class MesaVkWinsys(val wireValue: String, val label: String) {
    AUTO("", "Por defecto del sistema"),
    ANDROID("android", "Android nativo"),
    WAYLAND("wayland", "Wayland"),
    ;

    companion object {
        fun fromWireValue(value: String?): MesaVkWinsys =
            value?.let { v -> entries.firstOrNull { it.wireValue == v } } ?: AUTO
    }
}

/**
 * Ajustes del entorno de ejecución configurables por el usuario.
 *
 * Los valores se guardan en Room como cadenas (no como ordinales) para que
 * añadir un preset no reinterprete la base de datos.
 */
data class EnvironmentSettings(
    val dxvkHud: DxvkHudPreset = DxvkHudPreset.OFF,
    val box64Log: Box64LogLevel = Box64LogLevel.NONE,
    val box64Dynarec: Box64DynarecSize = Box64DynarecSize.AUTO,
    val galliumDriver: GalliumDriver = GalliumDriver.AUTO,
    val mesaVkWinsys: MesaVkWinsys = MesaVkWinsys.AUTO,
    /** Bandera `WINEDEBUG`; vacío = desactivada. */
    val wineDebug: String = "",
    /** Aplica `winetricks` con esta lista de verbos al preparar el prefijo. */
    val winetricksVerbs: List<String> = emptyList(),
) {

    /**
     * Devuelve una copia en la que los valores "vacíos" del estilo `WINEDEBUG=`
     * se han sustituido por `null` lógico (campo en blanco).
     */
    fun normalized(): EnvironmentSettings = copy(
        wineDebug = wineDebug.trim(),
        winetricksVerbs = winetricksVerbs.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
    )

    companion object {

        /** Ajustes de fábrica: el mínimo que un usuario nuevo espera ver. */
        val DEFAULT: EnvironmentSettings = EnvironmentSettings()

        /**
         * Lee los ajustes de un mapa tal cual lo devuelve la base de datos: las
         * columnas nullable llegan como `null`, los valores desconocidos se
         * mapean al "por defecto" del enumerado correspondiente.
         */
        fun fromStorage(
            dxvkHud: String?,
            box64Log: String?,
            box64Dynarec: String?,
            galliumDriver: String?,
            mesaVkWinsys: String?,
            wineDebug: String?,
            winetricksVerbs: List<String>,
        ): EnvironmentSettings = EnvironmentSettings(
            dxvkHud = DxvkHudPreset.fromWireValue(dxvkHud),
            box64Log = Box64LogLevel.fromWireValue(box64Log),
            box64Dynarec = Box64DynarecSize.fromWireValue(box64Dynarec),
            galliumDriver = GalliumDriver.fromWireValue(galliumDriver),
            mesaVkWinsys = MesaVkWinsys.fromWireValue(mesaVkWinsys),
            wineDebug = wineDebug.orEmpty(),
            winetricksVerbs = winetricksVerbs,
        ).normalized()
    }
}