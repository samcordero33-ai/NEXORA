package com.nexora.android.runtime.saves

import java.io.File

/**
 * Borrado seguro-ish de temporales del runtime.
 *
 * Qué se considera "temporal" del runtime:
 * - `tmp/` en la raíz de cada `WINEPREFIX` (lo que Wine/Winetricks crean
 *   al instalar paquetes).
 * - Carpeta `cache/nexora-tmp/` dentro de `filesDir` (logs viejos, dumps
 *   de monitor, exports parciales).
 *
 * Política: **solo** borramos archivos de menos de 7 días. Esto evita que un
 * crash reciente (que el usuario quiere adjuntar a un bug) se evapore sin
 * querer. Para borrado total el usuario tiene un botón explícito "Limpieza
 * profunda" en los ajustes.
 */
object TempFileCleaner {

    data class Result(
        val deletedFiles: Int,
        val freedBytes: Long,
    )

    private const val MAX_AGE_MILLIS: Long = 7L * 24L * 3600L * 1000L

    fun clean(targets: List<File>, deep: Boolean = false): Result {
        val now = System.currentTimeMillis()
        var deleted = 0
        var bytes = 0L
        for (target in targets) {
            if (!target.exists()) continue
            val files = target.walkTopDown().filter { it.isFile }
            for (file in files) {
                val age = now - file.lastModified()
                val eligible = deep || age <= MAX_AGE_MILLIS
                if (eligible) {
                    bytes += file.length()
                    if (file.delete()) deleted += 1
                }
            }
        }
        return Result(deleted, bytes)
    }
}