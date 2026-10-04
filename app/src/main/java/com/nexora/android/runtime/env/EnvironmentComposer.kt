package com.nexora.android.runtime.env

import com.nexora.android.engine.profile.CpuPriority
import com.nexora.android.engine.profile.PerformanceParameters
import com.nexora.android.engine.profile.PerformanceProfile
import com.nexora.android.engine.profile.RenderBackend

/**
 * Resultado de componer el entorno y el comando de lanzamiento.
 *
 * Es un **dato inmutable** que la capa del motor de ejecución consume: la
 * UI nunca monta el mapa `env` a mano ni decide los flags del binario.
 *
 * @property environment Mapa nombre→valor de las variables que se exportarán
 *   al proceso. Las claves vacías no se emiten: si el usuario eligió
 *   `AUTO` en un driver, la variable simplemente no aparece.
 * @property warnings Avisos no bloqueantes (perfil desconocido, GPU sin
 *   Turnip, …). La pantalla de ejecución los muestra para que el usuario
 *   sepa qué se ha asumido.
 * @property command Línea de comandos que se le pasará a `ProcessBuilder`.
 *   Vacía si el motor todavía no tiene un binario concreto que lanzar; la UI
 *   muestra entonces un mensaje claro en lugar de intentar ejecutar nada.
 * @property winePrefixPath Ruta absoluta del `WINEPREFIX` que se usará.
 */
data class ComposedEnvironment(
    val environment: Map<String, String>,
    val warnings: List<String>,
    val command: List<String>,
    val winePrefixPath: String,
)

/**
 * Composición pura del entorno de ejecución.
 *
 * Es una **función pura** sobre sus argumentos: mismo perfil + misma config
 * + misma ruta = mismo `ComposedEnvironment`. Eso permite probarla en JVM sin
 * Android y previsualizar qué se exportará antes de pulsar "Jugar".
 *
 * El compositor **no lanza nada**: devuelve la configuración lista para que
 * [com.nexora.android.runtime.engine.RuntimeEngine] la pase a `ProcessBuilder`.
 */
class EnvironmentComposer {

    /**
     * Compone el entorno.
     *
     * @param gameId Identificador del juego (clave del prefijo).
     * @param exePath Ruta del `.exe` que el usuario quiere lanzar.
     * @param parameters Parámetros del perfil de rendimiento elegido.
     * @param profileId Identificador del perfil, para adjuntarlo a los avisos
     *   y para la trazabilidad.
     * @param settings Variables de entorno configuradas por el usuario.
     * @param prefixPathFor Fábrica de la ruta absoluta del prefijo (testeable).
     */
    fun compose(
        gameId: String,
        exePath: String,
        parameters: PerformanceParameters,
        profileId: String,
        settings: EnvironmentSettings,
        prefixPathFor: (String) -> String,
    ): ComposedEnvironment {
        val warnings = mutableListOf<String>()
        val env = mutableMapOf<String, String>()

        // 1) Prefijo de Wine por juego: cada uno aísla su caché de DXVK y sus DLLs.
        val winePrefix = prefixPathFor(gameId)
        env["WINEPREFIX"] = winePrefix
        env["WINEDLLOVERRIDES"] = "d3d11=n,b;d3d10core=n,b;d3d9=n,b"

        // 2) Backend gráfico → variables de DXVK / Mesa.
        applyDxvk(env, parameters.backend, settings.dxvkHud)
        applyMesa(env, settings.galliumDriver, settings.mesaVkWinsys)

        // 3) Box64: solo si la arquitectura lo necesita; en ARM64 sí, en x86_64 no.
        env["BOX64_DYNAREC"] = settings.box64Dynarec.wireValue
        env["BOX64_LOG"] = settings.box64Log.wireValue
        if (parameters.workerThreads > 0) {
            env["BOX64_DYNAREC_BIGBLOCK"] = if (parameters.workerThreads >= 6) "2" else "1"
        }

        // 4) Prioridad de CPU y techo de FPS: lo aplica el runtime en Android,
        //    pero dejamos constancia en el entorno para binarios que lo lean.
        env["NEXORA_CPU_PRIORITY"] = cpuPriorityWire(parameters.cpuPriority)
        if (parameters.fpsCap > 0) env["NEXORA_FPS_CAP"] = parameters.fpsCap.toString()

        // 5) Debug de Wine bajo demanda.
        if (settings.wineDebug.isNotBlank()) {
            env["WINEDEBUG"] = settings.wineDebug
        }

        // 6) Verbos de winetricks: se ejecutan **antes** del juego, así que el
        //    compositor solo los registra; el motor los aplicará al preparar.
        if (settings.winetricksVerbs.isNotEmpty()) {
            env["NEXORA_WINETRICKS"] = settings.winetricksVerbs.joinToString(",")
        }

        // 7) Avisos: el compositor no aborta por estas cosas, pero el runner
        //    las enseña al usuario.
        if (parameters.backend == RenderBackend.VULKAN &&
            settings.galliumDriver == GalliumDriver.SOFTPIPE
        ) {
            warnings += "Has pedido Vulkan pero el driver Gallium es software; " +
                "el juego se renderizará por CPU."
        }
        if (parameters.workerThreads == 0) {
            warnings += "El perfil «$profileId» no reserva hilos de traducción."
        }

        // 8) Línea de comandos: la decisión de qué binario concreto lanzar
        //    (wine, wine64, box64, …) es del motor y depende del dispositivo.
        //    Mientras tanto, dejamos el `.exe` como argumento único para que el
        //    motor pueda reescribirlo.
        val command = listOf(exePath)

        return ComposedEnvironment(
            environment = env.filterValues { it.isNotEmpty() },
            warnings = warnings,
            command = command,
            winePrefixPath = winePrefix,
        )
    }

    private fun applyDxvk(env: MutableMap<String, String>, backend: RenderBackend, hud: DxvkHudPreset) {
        // DXVK solo aplica a DirectX 9/10/11. Vulkan y OpenGL no lo usan.
        val dxvkEnabled = when (backend) {
            RenderBackend.DIRECTX_9, RenderBackend.DIRECTX_10_11 -> true
            RenderBackend.VULKAN, RenderBackend.OPENGL, RenderBackend.AUTO -> false
        }
        if (dxvkEnabled) {
            env["DXVK_ENABLE_ASYNC"] = "1"
            env["DXVK_ASYNC"] = "1"
            env["DXVK_HUD"] = hud.wireValue
        }
    }

    private fun applyMesa(
        env: MutableMap<String, String>,
        driver: GalliumDriver,
        winsys: MesaVkWinsys,
    ) {
        if (driver.wireValue.isNotEmpty()) {
            env["GALLIUM_DRIVER"] = driver.wireValue
        }
        if (winsys.wireValue.isNotEmpty()) {
            env["MESA_VK_WINSYS"] = winsys.wireValue
        }
    }

    private fun cpuPriorityWire(priority: CpuPriority): String = when (priority) {
        CpuPriority.LOW -> "low"
        CpuPriority.NORMAL -> "normal"
        CpuPriority.HIGH -> "high"
        CpuPriority.MAX -> "max"
    }

    companion object {

        /**
         * Fábrica de rutas para un caso concreto: el `filesDir` de la app.
         *
         * Se pasa como lambda para que el compositor sea puro.
         */
        fun prefixPathFor(filesDirPath: String): (String) -> String = { gameId ->
            "$filesDirPath/prefixes/$gameId"
        }
    }
}