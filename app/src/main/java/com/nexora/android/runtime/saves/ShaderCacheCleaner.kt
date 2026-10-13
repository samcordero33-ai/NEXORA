package com.nexora.android.runtime.saves

import java.io.File

/**
 * Limpia la caché de shaders de DXVK de un prefijo de Wine.
 *
 * DXVK guarda por defecto en:
 *   `$WINEPREFIX/drive_c/users/steamuser/AppData/Roaming/DXVK/`
 *   `*_state_cache`  (mapa de SPIR-V generado por tubería)
 *
 * Borrar estos archivos es seguro: DXVK los regenera la próxima vez que el
 * juego pida cada pipeline, con la única penalización de algunos segundos
 * extra de compilación al inicio.
 *
 * También limpiamos `dxvk-cache` por si el usuario lo activó explícitamente.
 */
object ShaderCacheCleaner {

    /**
     * @return bytes liberados y número de archivos eliminados. Si el prefijo
     *   no existe, devuelve `(0, 0)` y `ok = true` (idempotente).
     */
    data class Result(
        val deletedFiles: Int,
        val freedBytes: Long,
    )

    fun clean(winePrefix: File): Result {
        if (!winePrefix.exists()) return Result(0, 0)
        var deleted = 0
        var bytes = 0L

        val candidates = listOf(
            // DXVK state cache por defecto.
            winePrefix.resolve("drive_c/users/steamuser/AppData/Roaming/DXVK"),
            // Algunos juegos usan AppData/Local.
            winePrefix.resolve("drive_c/users/steamuser/AppData/Local/DXVK"),
        )

        for (root in candidates) {
            if (!root.exists()) continue
            for (file in root.walkTopDown()) {
                if (file.isFile && (
                        file.name.endsWith(".dxvk-cache") ||
                        file.name.endsWith("_state_cache")
                    )
                ) {
                    bytes += file.length()
                    if (file.delete()) deleted += 1
                }
            }
        }
        return Result(deleted, bytes)
    }
}