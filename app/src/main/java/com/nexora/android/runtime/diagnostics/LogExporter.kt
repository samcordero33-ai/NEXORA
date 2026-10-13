package com.nexora.android.runtime.diagnostics

import java.io.File

/**
 * Escribe la lista de eventos a un archivo `.txt` en formato `Logcat-like`.
 *
 * El formato elegido es:
 * ```
 * NEXORA diagnostics log
 * Generado: <ISO-8601>
 * Eventos: N
 * --
 * HH:mm:ss LEVEL source: message
 * HH:mm:ss LEVEL source: message
 * ...
 * ```
 *
 * Si [output] apunta a un directorio, se genera `nexora-diagnostics-<timestamp>.txt`
 * dentro de él. Esto es lo que pide el operador cuando dice "comparte el log".
 */
object LogExporter {

    /**
     * Escribe [events] en [output]. Si [output] es directorio, genera un
     * nombre basado en timestamp.
     *
     * @return el archivo real en el que se escribió.
     */
    fun export(events: List<DiagnosticsEvent>, output: File): File {
        val target = if (output.isDirectory) {
            val name = "nexora-diagnostics-${System.currentTimeMillis()}.txt"
            File(output, name)
        } else {
            output.parentFile?.mkdirs()
            output
        }

        target.bufferedWriter().use { writer ->
            writer.appendLine("NEXORA diagnostics log")
            writer.appendLine("Generado: ${formatIso(System.currentTimeMillis())}")
            writer.appendLine("Eventos: ${events.size}")
            writer.appendLine("--")
            for (event in events) {
                writer.appendLine(event.formatLine())
            }
        }
        return target
    }

    private fun formatIso(epochMs: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMs }
        return "%04d-%02d-%02dT%02d:%02d:%02d".format(
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
            cal.get(java.util.Calendar.SECOND),
        )
    }
}