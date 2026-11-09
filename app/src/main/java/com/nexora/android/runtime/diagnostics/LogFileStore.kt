package com.nexora.android.runtime.diagnostics

import java.io.File

/**
 * Un archivo de log de sesión en disco.
 *
 * @property file Archivo real.
 * @property name Nombre (`session-<juego>-<fecha>.log`).
 * @property bytes Tamaño.
 * @property modifiedMs Última escritura.
 */
data class LogFileInfo(
    val file: File,
    val name: String,
    val bytes: Long,
    val modifiedMs: Long,
)

/**
 * Lectura de los logs de sesión guardados por [SessionLogRecorder].
 *
 * Todo es `java.io`: no hay Android, así que se puede probar en JVM con
 * archivos temporales. La lectura es **por cola** (`readTail`) porque el
 * interés está en el final —donde aparece el fallo— y un log de 8 MB no cabe
 * con dignidad en una pantalla de móvil.
 */
object LogFileStore {

    /** Líneas que se cargan por defecto en el visor. */
    const val DEFAULT_TAIL_LINES: Int = 500

    /** Logs de sesión, del más reciente al más antiguo. */
    fun list(dir: File): List<LogFileInfo> {
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { file -> file.isFile && file.name.endsWith(".log") }
            .orEmpty()
            .map { file ->
                LogFileInfo(
                    file = file,
                    name = file.name,
                    bytes = file.length(),
                    modifiedMs = file.lastModified(),
                )
            }
            .sortedByDescending { it.modifiedMs }
    }

    /**
     * Últimas [maxLines] líneas de [file].
     *
     * Se guardan en un bucle acotado en memoria: da igual que el archivo tenga
     * 10 líneas o 400 000.
     */
    fun readTail(file: File, maxLines: Int = DEFAULT_TAIL_LINES): List<String> {
        if (!file.isFile) return emptyList()
        require(maxLines > 0) { "maxLines debe ser > 0" }
        val window = ArrayDeque<String>(maxLines)
        runCatching {
            file.useLines { lines ->
                lines.forEach { line ->
                    if (window.size >= maxLines) window.removeFirst()
                    window.addLast(line)
                }
            }
        }
        return window.toList()
    }

    /** Primeras [maxLines] líneas (cabecera de la sesión). */
    fun readHead(file: File, maxLines: Int = 40): List<String> {
        if (!file.isFile) return emptyList()
        return runCatching {
            file.useLines { lines -> lines.take(maxLines).toList() }
        }.getOrDefault(emptyList())
    }

    /** Borra un log. Devuelve `true` si ya no existe. */
    fun delete(file: File): Boolean {
        val deleted = runCatching { file.delete() }.getOrDefault(false)
        return deleted || !file.exists()
    }

    /** Borra todos los logs de la carpeta. Devuelve cuántos se borraron. */
    fun deleteAll(dir: File): Int = list(dir).count { delete(it.file) }
}
