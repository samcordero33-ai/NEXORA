package com.nexora.android.runtime.diagnostics

/**
 * Nivel de severidad de un evento de diagnóstico. Se mantienen tres niveles
 * para que la UI pueda filtrar por color/severidad.
 */
enum class DiagnosticsLevel(val tag: String) {
    INFO("INFO"),
    WARN("WARN"),
    ERROR("ERROR"),
}

/**
 * Evento registrado por [DiagnosticsLogger].
 *
 * @property timestampMs Épica usar para mostrar "HH:mm:ss".
 * @property level Severidad.
 * @property source Origen del evento: `"runtime"` para stdout/stderr del motor,
 *   `"engine"` para transiciones de estado, `"app"` para eventos de UI.
 * @property message Mensaje textual.
 */
data class DiagnosticsEvent(
    val timestampMs: Long,
    val level: DiagnosticsLevel,
    val source: String,
    val message: String,
) {
    /** Formato compatible con `Logcat`: `HH:mm:ss LEVEL source: message`. */
    fun formatLine(): String {
        val ts = formatTimestamp(timestampMs)
        return "$ts ${level.tag} $source: $message"
    }

    companion object {
        /** Formato de hora local; tests no dependen de la zona. */
        fun formatTimestamp(epochMs: Long): String {
            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = epochMs
            }
            return "%02d:%02d:%02d".format(
                cal.get(java.util.Calendar.HOUR_OF_DAY),
                cal.get(java.util.Calendar.MINUTE),
                cal.get(java.util.Calendar.SECOND),
            )
        }
    }
}