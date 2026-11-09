package com.nexora.android.runtime.saves

import java.util.Calendar

/**
 * Identidad de un respaldo: a qué juego pertenece y cuándo se creó.
 *
 * El nombre del archivo es la única base de datos: no hay índice ni tabla que
 * se pueda desincronizar. Si el usuario copia el ZIP a otra carpeta y lo
 * devuelve, sigue funcionando.
 */
data class BackupStamp(
    val gameId: String,
    val epochMs: Long,
)

/**
 * Convención de nombres de los respaldos de partidas.
 *
 * ```
 * nexora-saves_<gameId>_20250312-180412.zip
 * ```
 *
 * - El prefijo `nexora-saves` evita confundir estos ZIP con cualquier otro.
 * - El `gameId` va saneado (solo letras, dígitos, `-` y `_`), porque un id con
 *   `/` o `:` rompería la ruta en cualquier sistema de archivos.
 * - La fecha va en hora local y con segundos: dos respaldos seguidos no se
 *   pisan.
 */
object BackupNaming {

    const val PREFIX: String = "nexora-saves"

    private val PATTERN = Regex("""^nexora-saves_(.+)_(\d{8}-\d{6})\.zip$""")

    /** Nombre de archivo para un respaldo nuevo. */
    fun fileName(gameId: String, epochMs: Long): String =
        "${PREFIX}_${sanitize(gameId)}_${formatTimestamp(epochMs)}.zip"

    /** `20250312-180412` en hora local. */
    fun formatTimestamp(epochMs: Long): String {
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

    /**
     * Reconstruye el sello desde el nombre del archivo.
     *
     * @return `null` si el nombre no sigue la convención (por ejemplo, un ZIP
     *   importado a mano): en ese caso se ignora en vez de inventar una fecha.
     */
    fun parse(fileName: String): BackupStamp? {
        val match = PATTERN.matchEntire(fileName) ?: return null
        val gameId = match.groupValues[1]
        val epoch = parseTimestamp(match.groupValues[2]) ?: return null
        return BackupStamp(gameId = gameId, epochMs = epoch)
    }

    /** `20250312-180412` → milisegundos de época, o `null` si no es válido. */
    fun parseTimestamp(raw: String): Long? {
        if (raw.length != 15 || raw[8] != '-') return null
        val digits = raw.removeRange(8, 9)
        if (!digits.all { it.isDigit() }) return null
        val year = digits.substring(0, 4).toIntOrNull() ?: return null
        val month = digits.substring(4, 6).toIntOrNull() ?: return null
        val day = digits.substring(6, 8).toIntOrNull() ?: return null
        val hour = digits.substring(8, 10).toIntOrNull() ?: return null
        val minute = digits.substring(10, 12).toIntOrNull() ?: return null
        val second = digits.substring(12, 14).toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31 || hour !in 0..23 || minute !in 0..59 || second !in 0..59) {
            return null
        }
        return Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, second)
        }.timeInMillis
    }

    /** Etiqueta legible: `12/03/2025 · 18:04`. */
    fun label(stamp: BackupStamp): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = stamp.epochMs }
        return "%02d/%02d/%04d · %02d:%02d".format(
            calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
        )
    }

    /** Deja el id en algo que cualquier sistema de archivos acepta. */
    fun sanitize(gameId: String): String {
        val cleaned = gameId.map { char ->
            if (char.isLetterOrDigit() || char == '-' || char == '_') char else '-'
        }.joinToString("")
        return cleaned.trim('-').ifEmpty { "game" }
    }
}
