package com.nexora.android.runtime.engine

import com.nexora.android.runtime.binary.RuntimeLayout

/**
 * Completa el entorno que hereda el proceso hijo.
 *
 * El compositor de la Fase 6 decide **qué** variables de juego se exportan
 * (`DXVK_HUD`, `BOX64_DYNAREC`, `WINEPREFIX`…); esta clase añade las que
 * dependen de **dónde** quedó instalado el runtime y que Wine no puede
 * adivinar:
 *
 * | Variable | Valor | Por qué |
 * |---|---|---|
 * | `PATH` | `<binDir>:/system/bin:…` | encontrar `wine64`, `box64`, `wineserver` |
 * | `LD_LIBRARY_PATH` | `<libDir>:<libDir>/wine:…` | cargar `libwine.so`, DXVK y Turnip |
 * | `BOX64_LD_LIBRARY_PATH` | `<libDir>` | Box64 busca sus `.so` ahí, no en el del sistema |
 * | `HOME` | prefijo | Wine escribe `.wine`, `.cache` y `dosdevices` bajo `HOME` |
 * | `TMPDIR` / `XDG_RUNTIME_DIR` | `<root>/tmp` | sockets y temporales dentro de la app |
 * | `PULSE_SERVER` | `unix:<tmp>/pulse/native` | el sink de PulseAudio del contenedor |
 * | `NEXORA_RUNTIME_ROOT` | `<root>` | trazabilidad en los logs |
 *
 * Es una función pura: mismo entorno + mismo layout = mismo mapa. Así se
 * verifica en JVM sin lanzar un proceso.
 */
object ExecEnvironment {

    /** Separa rutas con `:` (convención POSIX, no la del sistema de archivos). */
    private const val SEP = ":"

    fun build(
        base: Map<String, String>,
        layout: RuntimeLayout,
        usingTranslator: Boolean,
        tmpDir: String? = null,
    ): Map<String, String> {
        val env = base.filterValues { it.isNotBlank() }.toMutableMap()

        env["PATH"] = prepend(layout.binDir, env["PATH"] ?: DEFAULT_PATH)
        env["LD_LIBRARY_PATH"] = prependAll(
            listOf(
                layout.libDir,
                "${layout.libDir}/wine",
                "${layout.libDir}/wine/x86_64-unix",
                "${layout.libDir}/wine/i386-unix",
            ),
            env["LD_LIBRARY_PATH"],
        )
        if (usingTranslator) {
            env["BOX64_LD_LIBRARY_PATH"] = prepend(layout.libDir, env["BOX64_LD_LIBRARY_PATH"])
        }

        val tmp = tmpDir?.takeIf { it.isNotBlank() } ?: "${layout.rootPath}/tmp"
        env["TMPDIR"] = env["TMPDIR"] ?: tmp
        env["XDG_RUNTIME_DIR"] = env["XDG_RUNTIME_DIR"] ?: tmp
        env["HOME"] = env["HOME"] ?: layout.prefixesDir
        env["PULSE_SERVER"] = env["PULSE_SERVER"] ?: "unix:$tmp/pulse/native"
        env["NEXORA_RUNTIME_ROOT"] = layout.rootPath
        env["NEXORA_RUNTIME_VERSION"] = layout.version

        // Wine necesita saber que no hay consola interactiva: si no, algunos
        // instaladores esperan una entrada que nunca llega.
        env.putIfAbsent("WINEDEBUG", "-all")
        return env
    }

    private fun prepend(first: String, rest: String?): String =
        if (rest.isNullOrBlank()) first else "$first$SEP$rest"

    private fun prependAll(parts: List<String>, rest: String?): String {
        val all = parts.filter { it.isNotBlank() }.toMutableList()
        if (!rest.isNullOrBlank()) all += rest
        return all.joinToString(SEP)
    }

    /** `PATH` mínimo de Android, sin depender de variables del shell. */
    const val DEFAULT_PATH = "/system/bin:/system/xbin:/vendor/bin"
}
