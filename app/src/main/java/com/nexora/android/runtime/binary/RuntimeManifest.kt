package com.nexora.android.runtime.binary

/**
 * Manifiesto del runtime: qué componentes se esperan, dónde y con qué huella.
 *
 * Se lee de `assets/runtime/manifest.txt`, un formato de líneas `clave=valor`
 * con secciones `[componente]`. Es deliberadamente **texto plano y sin
 * dependencias**: se puede editar sin herramientas, se revisa en un diff y se
 * parsea en JVM pura (sin `org.json`, que en pruebas unitarias es un stub).
 *
 * ```
 * version=8
 * [wine]
 * asset=bin/wine64
 * target=bin/wine64
 * sha256=9f2c…
 * executable=true
 * size=104857600
 * ```
 *
 * Líneas vacías y comentarios (`#`) se ignoran. Una clave desconocida también:
 * añadir metadatos no debe romper una versión anterior del parser.
 */
data class RuntimeManifest(
    val version: String,
    val entries: List<BinaryEntry>,
) {

    /** Entradas agrupadas por componente. */
    fun byComponent(): Map<BinaryComponent, List<BinaryEntry>> =
        entries.groupBy { it.component }

    companion object {

        const val CURRENT_VERSION = "8"

        /** Manifiesto vacío: la app arranca y reporta que no hay motor. */
        val EMPTY = RuntimeManifest(version = CURRENT_VERSION, entries = emptyList())

        /**
         * Parsea el manifiesto.
         *
         * No lanza: una entrada inválida se descarta y se anota en
         * [ParseResult.problems], porque un manifiesto corrupto no debe impedir
         * que la app abra la biblioteca.
         */
        fun parse(text: String): ParseResult {
            var version = CURRENT_VERSION
            var current: String? = null
            val fields = linkedMapOf<String, MutableMap<String, String>>()
            val problems = mutableListOf<String>()

            text.lineSequence().forEachIndexed { index, raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                    return@forEachIndexed
                }
                if (line.startsWith("[") && line.endsWith("]")) {
                    val section = line.substring(1, line.length - 1).trim().lowercase()
                    if (section.isEmpty()) {
                        problems += "Línea ${index + 1}: sección sin nombre"
                        current = null
                    } else {
                        current = section
                        fields.getOrPut(section) { linkedMapOf() }
                    }
                    return@forEachIndexed
                }
                val eq = line.indexOf('=')
                if (eq <= 0) {
                    problems += "Línea ${index + 1}: se esperaba clave=valor"
                    return@forEachIndexed
                }
                val key = line.substring(0, eq).trim().lowercase()
                val value = line.substring(eq + 1).trim()
                val section = current
                if (section == null) {
                    if (key == "version") version = value.ifEmpty { version }
                    return@forEachIndexed
                }
                fields.getOrPut(section) { linkedMapOf() }[key] = value
            }

            val entries = mutableListOf<BinaryEntry>()
            for ((section, values) in fields) {
                val component = BinaryComponent.fromId(section)
                if (component == null) {
                    problems += "Componente desconocido: «$section»"
                    continue
                }
                val asset = values["asset"].orEmpty()
                if (asset.isEmpty()) {
                    problems += "«$section» no declara asset"
                    continue
                }
                val target = values["target"].orEmpty().ifEmpty { asset }
                val size = values["size"]?.toLongOrNull() ?: -1L
                entries += BinaryEntry(
                    component = component,
                    assetPath = asset.trimStart('/'),
                    targetPath = target.trimStart('/'),
                    sha256 = values["sha256"].orEmpty().removePrefix("sha256:"),
                    executable = values["executable"]?.toBooleanStrictOrNull() ?: false,
                    sizeBytes = size,
                )
            }

            return ParseResult(
                manifest = RuntimeManifest(version = version, entries = entries),
                problems = problems,
            )
        }
    }

    data class ParseResult(
        val manifest: RuntimeManifest,
        val problems: List<String>,
    )
}
