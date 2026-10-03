package com.nexora.android.ui.util

import java.util.Locale

private const val BYTES_PER_KILOBYTE = 1024.0
private const val BYTES_PER_MEGABYTE = BYTES_PER_KILOBYTE * 1024.0
private const val BYTES_PER_GIGABYTE = BYTES_PER_MEGABYTE * 1024.0

/**
 * Formatea un tamaño en bytes con la unidad adecuada.
 *
 * @return `Desconocido` es responsabilidad de la UI; aquí un valor no positivo
 *   se muestra como `0 B` para no mentir sobre el tamaño.
 */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    return when {
        bytes >= BYTES_PER_GIGABYTE -> String.format(Locale.US, "%.2f GB", bytes / BYTES_PER_GIGABYTE)
        bytes >= BYTES_PER_MEGABYTE -> String.format(Locale.US, "%.1f MB", bytes / BYTES_PER_MEGABYTE)
        bytes >= BYTES_PER_KILOBYTE -> String.format(Locale.US, "%.0f KB", bytes / BYTES_PER_KILOBYTE)
        else -> "$bytes B"
    }
}
