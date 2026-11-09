package com.nexora.android.runtime.overlay

/**
 * Extrae los FPS que publica `DXVK_HUD` en la salida del motor.
 *
 * `DXVK_HUD=fps` escribe una línea por segundo en stderr con formas que
 * cambian según la versión y el idioma:
 *
 * ```
 * FPS: 58
 * fps: 58.3
 * 58 fps
 * Framerate 60
 * ```
 *
 * En vez de intentar reconocer la versión de DXVK, se aceptan las tres formas
 * y se descarta todo lo que no sea un número plausible (1–999). Es
 * deliberadamente tolerante: si un día DXVK cambia el formato, el HUD sigue
 * funcionando con la medida del `Choreographer` en vez de romperse.
 */
object DxvkHudParser {

    /** Rango aceptado como FPS. Fuera de aquí es basura de parseo. */
    private val VALID_RANGE = 1..999

    private val PATTERNS: List<Regex> = listOf(
        // "FPS: 58" / "fps=58.3"
        Regex("""\bfps\b\s*[:=]\s*(\d{1,4})(?:[.,]\d+)?""", RegexOption.IGNORE_CASE),
        // "58 fps" / "58.3 FPS"
        Regex("""(\d{1,4})(?:[.,]\d+)?\s*\bfps\b""", RegexOption.IGNORE_CASE),
        // "Framerate 60"
        Regex("""\bframerate\b\s*[:=]?\s*(\d{1,4})""", RegexOption.IGNORE_CASE),
    )

    /**
     * Devuelve los FPS contenidos en [line], o `null` si la línea no los trae.
     *
     * Se queda con el **primer** patrón que case: el orden importa porque
     * `"58 fps"` también contiene la palabra `fps` y un segundo número podría
     * pertenecer a otra métrica de la misma línea (`FPS: 58 GPU: 72%`).
     */
    fun parseFps(line: String): Int? {
        if (line.isBlank()) return null
        for (pattern in PATTERNS) {
            val match = pattern.find(line) ?: continue
            val raw = match.groupValues.getOrNull(1)?.toIntOrNull() ?: continue
            if (raw in VALID_RANGE) return raw
        }
        return null
    }

    /** `true` si la línea parece parte del HUD de DXVK. */
    fun isHudLine(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("dxvk") ||
            lower.contains("fps") ||
            lower.contains("framerate") ||
            lower.contains("gpu:") ||
            lower.contains("vram")
    }
}
