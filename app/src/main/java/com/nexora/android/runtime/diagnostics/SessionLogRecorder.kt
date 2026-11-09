package com.nexora.android.runtime.diagnostics

import com.nexora.android.runtime.model.LogStream
import com.nexora.android.runtime.model.RuntimeLog
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.Calendar

/**
 * Escribe la salida del motor a un archivo de sesión.
 *
 * La Fase 8 ya volcaba `stdout`/`stderr` al visor en memoria y al ring buffer
 * de [DiagnosticsLogger], pero ambos viven en RAM: cuando el proceso muere —o
 * cuando el juego cuelga la app— se van con él. Un fallo de Box64 o de Wine se
 * diagnostica **después**, y para eso hace falta que el log esté en disco.
 *
 * Decisiones:
 * - **`flush()` en cada línea.** Es lo que hace que el archivo sirva para un
 *   cuelgue: un buffer de 8 KB sin volcar no contiene las últimas líneas, que
 *   son justo las interesantes. El coste es despreciable frente a ejecutar un
 *   `.exe` bajo emulación.
 * - **Tope de tamaño.** Un juego con `WINEDEBUG=+relay` puede escribir cientos
 *   de MB. Al llegar al tope se deja de escribir y se anota el motivo, en vez
 *   de llenar el almacenamiento del teléfono.
 * - **Un archivo por sesión**, con la fecha en el nombre, para que el usuario
 *   pueda comparar dos ejecuciones del mismo juego.
 */
class SessionLogRecorder(
    private val logDir: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) {

    private val lock = Any()
    private var writer: BufferedWriter? = null
    private var file: File? = null
    private var written: Long = 0L
    private var truncated: Boolean = false

    /** Archivo de la sesión en curso, o `null` si no hay ninguna. */
    val currentFile: File?
        get() = synchronized(lock) { file }

    /**
     * Abre un archivo nuevo para [gameId] y escribe la cabecera.
     *
     * @return el archivo creado, o `null` si no se pudo abrir (disco lleno,
     *   permisos). Nunca lanza: quedarse sin log no puede tumbar la sesión.
     */
    fun start(gameId: String, header: List<String> = emptyList()): File? {
        close()
        return synchronized(lock) {
            runCatching {
                logDir.mkdirs()
                val target = File(logDir, fileName(gameId, clock()))
                val stream = BufferedWriter(FileWriter(target, true))
                stream.appendLine("# NEXORA session log")
                stream.appendLine("# Juego: $gameId")
                stream.appendLine("# Inicio: ${isoTimestamp(clock())}")
                header.forEach { stream.appendLine("# $it") }
                stream.appendLine("# ---")
                stream.flush()
                writer = stream
                file = target
                written = 0L
                truncated = false
                target
            }.getOrNull()
        }
    }

    /** Añade una línea del motor. Silencioso si no hay sesión abierta. */
    fun append(log: RuntimeLog) {
        appendLine("${timestampOf(log.timestampMs)} [${log.stream.label}] ${log.message}")
    }

    /** Añade una línea suelta (avisos propios de la app). */
    fun appendLine(line: String) {
        synchronized(lock) {
            val stream = writer ?: return
            if (truncated) return
            if (written >= maxBytes) {
                runCatching {
                    stream.appendLine("# log truncado: se alcanzó el tope de $maxBytes bytes")
                    stream.flush()
                }
                truncated = true
                return
            }
            runCatching {
                stream.appendLine(line)
                stream.flush()
                written += line.length + 1L
            }
        }
    }

    /** Cierra el archivo y devuelve el archivo escrito (o `null`). */
    fun close(): File? = synchronized(lock) {
        val finished = file
        runCatching { writer?.flush() }
        runCatching { writer?.close() }
        writer = null
        file = null
        finished
    }

    companion object {
        /** 8 MB: suficiente para un fallo con contexto, lejos de llenar el disco. */
        const val DEFAULT_MAX_BYTES: Long = 8L * 1024L * 1024L

        /** `session-<gameId>-20250312-180412.log` */
        fun fileName(gameId: String, epochMs: Long): String =
            "session-${sanitize(gameId)}-${stamp(epochMs)}.log"

        /** Nombre seguro para cualquier sistema de archivos. */
        fun sanitize(gameId: String): String {
            val cleaned = gameId.map { char ->
                if (char.isLetterOrDigit() || char == '-' || char == '_') char else '-'
            }.joinToString("")
            return cleaned.trim('-').ifEmpty { "session" }
        }

        private fun stamp(epochMs: Long): String {
            val calendar = Calendar.getInstance().apply { timeInMillis = epochMs }
            return "%04d%02d%02d-%02d%02d%02d".format(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                calendar.get(Calendar.SECOND),
            )
        }

        private fun isoTimestamp(epochMs: Long): String {
            val calendar = Calendar.getInstance().apply { timeInMillis = epochMs }
            return "%04d-%02d-%02d %02d:%02d:%02d".format(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                calendar.get(Calendar.SECOND),
            )
        }

        private fun timestampOf(epochMs: Long): String {
            val calendar = Calendar.getInstance().apply { timeInMillis = epochMs }
            return "%02d:%02d:%02d".format(
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                calendar.get(Calendar.SECOND),
            )
        }
    }
}
