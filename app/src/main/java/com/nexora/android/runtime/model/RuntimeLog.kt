package com.nexora.android.runtime.model

/**
 * Origen de un mensaje de log del proceso del juego.
 *
 * stdout y stderr se tratan por separado: stderr suele ser el canal de los
 * motores (DXVK, Box64, Wine) y se filtra visualmente distinto al stdout
 * del propio juego.
 */
enum class LogStream(val label: String) {
    STDOUT("stdout"),
    STDERR("stderr"),
    SYSTEM("sistema"),
}

/**
 * Una línea del log del runtime.
 *
 * @property timestampMs Marca de tiempo en milisegundos de época, tal y como
 *   la devuelve `System.currentTimeMillis()` cuando el motor la emite.
 * @property stream Canal de origen.
 * @property message Texto de la línea, recortado y con saltos normalizados.
 */
data class RuntimeLog(
    val timestampMs: Long,
    val stream: LogStream,
    val message: String,
)