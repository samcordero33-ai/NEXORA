package com.nexora.android.runtime.binary

import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * SHA-256 en hexadecimal, sin dependencias de Android.
 *
 * Se usa para dos cosas: verificar la carga útil que se instala (un binario
 * truncado o cambiado es peor que no tener binario) y sellar el manifiesto
 * para no reinstalar en cada arranque.
 */
object Sha256 {

    private const val BUFFER = 64 * 1024

    /** Huella de un bloque de bytes, en minúsculas. */
    fun of(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    /** Huella de un flujo, consumiéndolo por completo. */
    fun of(stream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER)
        while (true) {
            val read = stream.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().toHex()
    }

    /** Huella de un archivo, o `null` si no se puede leer. */
    fun of(file: java.io.File): String? = try {
        file.inputStream().use { of(it) }
    } catch (_: Exception) {
        null
    }

    /**
     * Compara dos huellas ignorando mayúsculas y el prefijo `sha256:`.
     *
     * El prefijo aparece en la salida de `sha256sum` según la herramienta, y
     * rechazar un binario por eso sería un fallo de formato, no de seguridad.
     */
    fun matches(expected: String, actual: String?): Boolean {
        if (actual == null) return false
        return expected.trim().removePrefix("sha256:").equals(actual, ignoreCase = true)
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") {
        String.format(Locale.US, "%02x", it)
    }
}
