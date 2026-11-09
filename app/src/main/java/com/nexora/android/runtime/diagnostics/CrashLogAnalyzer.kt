package com.nexora.android.runtime.diagnostics

/**
 * Fallos que se pueden reconocer en la salida de Wine, Box64 o DXVK.
 *
 * El orden del enum es el de **gravedad** de la comprobación: si un log
 * contiene un `SIGSEGV` y además un `permission denied`, el diagnóstico útil
 * es el segfault, no el permiso.
 */
enum class CrashSignature(val label: String) {
    NONE("Sin fallo reconocible"),
    MISSING_BINARY("Runtime sin binarios"),
    PERMISSION_DENIED("Permiso denegado"),
    MISSING_DLL("DLL no encontrada"),
    OUT_OF_MEMORY("Memoria agotada"),
    DEVICE_LOST("Dispositivo gráfico perdido"),
    ILLEGAL_INSTRUCTION("Instrucción ilegal"),
    UNHANDLED_EXCEPTION("Excepción no controlada"),
    SEGFAULT("Violación de segmento"),
}

/**
 * Resultado del análisis de un log.
 *
 * @property signature Fallo reconocido (`NONE` si no hay ninguno).
 * @property evidence Hasta tres líneas que lo demuestran, recortadas.
 * @property suggestion Qué hacer al respecto, en una frase accionable.
 */
data class CrashFinding(
    val signature: CrashSignature,
    val evidence: List<String> = emptyList(),
    val suggestion: String = "",
) {
    val detected: Boolean get() = signature != CrashSignature.NONE
}

/**
 * Diagnóstico de un log de sesión por patrones.
 *
 * No es un depurador: es un lector de síntomas. Los mensajes de Wine, Box64 y
 * DXVK son lo bastante estables como para reconocer las causas habituales de
 * un cierre, y decir "esto parece un SIGSEGV del traductor" ahorra media hora
 * de lectura de log. Cuando no reconoce nada, lo dice: no inventa una causa.
 */
object CrashLogAnalyzer {

    private class Rule(
        val signature: CrashSignature,
        val patterns: List<Regex>,
        val suggestion: String,
    )

    private val RULES: List<Rule> = listOf(
        Rule(
            signature = CrashSignature.SEGFAULT,
            patterns = listOf(
                Regex("""segmentation fault""", RegexOption.IGNORE_CASE),
                Regex("""signal 11""", RegexOption.IGNORE_CASE),
                Regex("""SIGSEGV"""),
                Regex("""page fault on (read|write)""", RegexOption.IGNORE_CASE),
            ),
            suggestion = "El proceso murió por SIGSEGV. Suele ser una instrucción x86 que el " +
                "traductor no implementa: prueba BOX64_DYNAREC_BIGBLOCK=0 o cambia a FEX-Emu.",
        ),
        Rule(
            signature = CrashSignature.UNHANDLED_EXCEPTION,
            patterns = listOf(
                Regex("""unhandled exception""", RegexOption.IGNORE_CASE),
                Regex("""0xc0000005""", RegexOption.IGNORE_CASE),
                Regex("""terminate called after throwing""", RegexOption.IGNORE_CASE),
                Regex("""wine: Unhandled""", RegexOption.IGNORE_CASE),
            ),
            suggestion = "Wine capturó una excepción dentro del .exe. Lo habitual es que falte " +
                "una dependencia de Visual C++ (vcrun2019) en el prefijo.",
        ),
        Rule(
            signature = CrashSignature.ILLEGAL_INSTRUCTION,
            patterns = listOf(
                Regex("""illegal instruction""", RegexOption.IGNORE_CASE),
                Regex("""signal 4""", RegexOption.IGNORE_CASE),
                Regex("""SIGILL"""),
                Regex("""unimplemented opcode""", RegexOption.IGNORE_CASE),
            ),
            suggestion = "Instrucción no soportada por la CPU o por el traductor. Reduce la " +
                "optimización de Box64 o usa un binario x86_64 en vez de i386.",
        ),
        Rule(
            signature = CrashSignature.DEVICE_LOST,
            patterns = listOf(
                Regex("""VK_ERROR_DEVICE_LOST""", RegexOption.IGNORE_CASE),
                Regex("""device lost""", RegexOption.IGNORE_CASE),
                Regex("""vkQueueSubmit failed""", RegexOption.IGNORE_CASE),
                Regex("""DXVK: Device removed""", RegexOption.IGNORE_CASE),
            ),
            suggestion = "El driver Vulkan perdió el dispositivo. Baja la resolución del perfil, " +
                "desactiva DXVK_HUD o cambia entre Mesa y Turnip.",
        ),
        Rule(
            signature = CrashSignature.OUT_OF_MEMORY,
            patterns = listOf(
                Regex("""out of memory""", RegexOption.IGNORE_CASE),
                Regex("""bad_alloc""", RegexOption.IGNORE_CASE),
                Regex("""cannot allocate memory""", RegexOption.IGNORE_CASE),
                Regex("""ENOMEM"""),
            ),
            suggestion = "El juego se quedó sin memoria. Baja la calidad de texturas, reduce la " +
                "resolución y cierra apps en segundo plano.",
        ),
        Rule(
            signature = CrashSignature.MISSING_DLL,
            patterns = listOf(
                Regex("""err:module:import_dll""", RegexOption.IGNORE_CASE),
                Regex("""library not found""", RegexOption.IGNORE_CASE),
                Regex("""failed to load""", RegexOption.IGNORE_CASE),
                Regex("""cannot find .*\.dll""", RegexOption.IGNORE_CASE),
            ),
            suggestion = "Falta una DLL en el prefijo. Instálala con winetricks (por ejemplo " +
                "vcrun2019 o dotnet48) y vuelve a lanzar.",
        ),
        Rule(
            signature = CrashSignature.PERMISSION_DENIED,
            patterns = listOf(
                Regex("""permission denied""", RegexOption.IGNORE_CASE),
                Regex("""operation not permitted""", RegexOption.IGNORE_CASE),
                Regex("""EACCES"""),
            ),
            suggestion = "Un archivo o nodo no es accesible. Comprueba los permisos de " +
                "filesDir/runtime y que los binarios sean ejecutables.",
        ),
        Rule(
            signature = CrashSignature.MISSING_BINARY,
            patterns = listOf(
                Regex("""BinaryNotInstalledException"""),
                Regex("""no hay binarios de wine""", RegexOption.IGNORE_CASE),
                Regex("""runtime sin instalar""", RegexOption.IGNORE_CASE),
            ),
            suggestion = "No hay binarios de Wine en filesDir/runtime. Copia el runtime en " +
                "assets/runtime/, revisa el manifiesto y pulsa Reinstalar.",
        ),
    )

    /**
     * Analiza [lines] y devuelve el fallo más grave reconocido.
     *
     * @param maxEvidence cuántas líneas de prueba se adjuntan (por defecto 3).
     */
    fun analyze(lines: List<String>, maxEvidence: Int = 3): CrashFinding {
        if (lines.isEmpty()) return CrashFinding(CrashSignature.NONE, emptyList(), NO_DATA)
        for (rule in RULES) {
            val hits = lines.filter { line -> rule.patterns.any { it.containsMatchIn(line) } }
            if (hits.isNotEmpty()) {
                return CrashFinding(
                    signature = rule.signature,
                    evidence = hits.take(maxEvidence).map { shorten(it) },
                    suggestion = rule.suggestion,
                )
            }
        }
        return CrashFinding(CrashSignature.NONE, emptyList(), NO_FAILURE)
    }

    /** Recorta una línea de evidencia para que quepa en la UI. */
    private fun shorten(line: String, max: Int = 160): String {
        val trimmed = line.trim()
        return if (trimmed.length <= max) trimmed else trimmed.take(max - 1) + "…"
    }

    private const val NO_DATA: String =
        "El registro está vacío: la sesión no llegó a escribir nada."

    private const val NO_FAILURE: String =
        "No se ha reconocido ningún patrón de fallo conocido. Revisa las últimas líneas del " +
            "registro: el motor pudo salir con un código de error sin mensaje."
}
