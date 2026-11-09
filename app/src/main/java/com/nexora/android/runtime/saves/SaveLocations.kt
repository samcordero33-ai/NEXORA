package com.nexora.android.runtime.saves

import java.io.File

/**
 * Carpeta del prefijo que parece contener partidas guardadas.
 *
 * @property path Ruta absoluta en disco.
 * @property label Ruta relativa a `drive_c`, que es lo que el usuario reconoce
 *   (`users/steamuser/Saved Games/MiJuego`).
 * @property fileCount Archivos encontrados (recursivo, con tope).
 * @property bytes Tamaño total en bytes.
 * @property lastModifiedMs Modificación más reciente.
 * @property matchesGame `true` si el nombre se parece al del juego.
 */
data class SaveFolder(
    val path: String,
    val label: String,
    val fileCount: Int,
    val bytes: Long,
    val lastModifiedMs: Long,
    val matchesGame: Boolean,
)

/**
 * Busca carpetas de partidas guardadas dentro de un prefijo de Wine.
 *
 * En Windows no hay una única ruta de guardado: cada juego elige. Las
 * convenciones reales son cinco, y son las que se miran:
 *
 * | Ruta | Quién la usa |
 * |---|---|
 * | `users/<u>/Saved Games` | juegos de Microsoft y la mayoría de AAA |
 * | `users/<u>/Documents/My Games` | Unreal Engine, Civilization |
 * | `users/<u>/Documents` | juegos viejos que escriben ahí directamente |
 * | `users/<u>/AppData/Roaming` | Unity, indie, Electron |
 * | `users/<u>/AppData/Local` | algunos motores propios |
 *
 * El escáner **no adivina**: mide lo que hay. Si una carpeta no tiene ningún
 * archivo, no se lista; si hay demasiados archivos, se corta el conteo para no
 * bloquear la UI en un prefijo con miles de ficheros.
 */
object SaveLocationScanner {

    /** Contenedores habituales, relativos a la carpeta de cada usuario. */
    private val CONTAINERS: List<String> = listOf(
        "Saved Games",
        "Documents/My Games",
        "Documents",
        "AppData/Roaming",
        "AppData/Local",
    )

    /** Palabras que delatan una carpeta de partidas. */
    private val SAVE_TOKENS: List<String> = listOf(
        "save", "saves", "saved", "savegame", "savegames", "slot", "profile", "partida", "guardado",
    )

    /** Palabras que no identifican un juego y se ignoran al comparar. */
    private val STOP_WORDS: Set<String> = setOf(
        "the", "of", "and", "game", "games", "edition", "remaster", "remastered", "definitive",
        "deluxe", "goty", "la", "el", "los", "las", "de", "del", "y",
    )

    private const val MAX_FILES_PER_FOLDER = 5_000
    private const val MAX_DEPTH = 3

    /**
     * Escanea [driveC] y devuelve las carpetas con contenido.
     *
     * @param gameName Nombre del juego, para ordenar primero las coincidencias.
     * @param limit Número máximo de carpetas devueltas.
     */
    fun scan(
        driveC: File,
        gameName: String? = null,
        limit: Int = 40,
    ): List<SaveFolder> {
        if (!driveC.isDirectory) return emptyList()
        val usersRoot = File(driveC, "users")
        val users = usersRoot.listFiles { file -> file.isDirectory }?.sortedBy { it.name }.orEmpty()
        if (users.isEmpty()) return emptyList()

        val tokens = tokensOf(gameName)
        val candidates = LinkedHashMap<String, SaveFolder>()

        for (user in users) {
            for (container in CONTAINERS) {
                val dir = File(user, container)
                if (!dir.isDirectory) continue
                // La propia carpeta contenedora, si tiene archivos sueltos.
                collect(dir, driveC, tokens, candidates)
                // Y sus hijos directos, que es donde vive cada juego.
                dir.listFiles { file -> file.isDirectory }
                    ?.sortedBy { it.name }
                    ?.forEach { child -> collect(child, driveC, tokens, candidates) }
            }
        }

        return candidates.values
            .filter { it.fileCount > 0 }
            // Si una carpeta contiene a otra, sobra la de arriba: el usuario
            // quiere la partida concreta, no el árbol entero.
            .filterNot { parent -> candidates.values.any { it !== parent && it.path.startsWith(parent.path + File.separator) } }
            .sortedWith(
                compareByDescending<SaveFolder> { it.matchesGame }
                    .thenByDescending { it.lastModifiedMs }
                    .thenBy { it.label },
            )
            .take(limit)
    }

    private fun collect(
        dir: File,
        driveC: File,
        tokens: List<String>,
        into: MutableMap<String, SaveFolder>,
    ) {
        if (!dir.isDirectory) return
        val path = dir.absolutePath
        if (into.containsKey(path)) return
        val stats = statsOf(dir) ?: return
        val relative = relativeLabel(driveC, dir)
        into[path] = SaveFolder(
            path = path,
            label = relative,
            fileCount = stats.first,
            bytes = stats.second,
            lastModifiedMs = stats.third,
            matchesGame = matches(relative, tokens),
        )
    }

    /** `(archivos, bytes, última modificación)` o `null` si está vacía. */
    private fun statsOf(dir: File): Triple<Int, Long, Long>? {
        var files = 0
        var bytes = 0L
        var lastModified = 0L
        dir.walkTopDown()
            .maxDepth(MAX_DEPTH)
            .onEnter { it == dir || !it.name.startsWith(".") }
            .forEach { file ->
                if (files >= MAX_FILES_PER_FOLDER) return@forEach
                if (file.isFile) {
                    files += 1
                    bytes += file.length().coerceAtLeast(0L)
                    if (file.lastModified() > lastModified) lastModified = file.lastModified()
                }
            }
        return if (files == 0) null else Triple(files, bytes, lastModified)
    }

    private fun relativeLabel(driveC: File, dir: File): String {
        val root = driveC.absolutePath.trimEnd(File.separatorChar)
        val path = dir.absolutePath
        return if (path.startsWith(root + File.separator)) {
            path.removePrefix(root + File.separator)
        } else {
            dir.name
        }
    }

    /** `true` si [label] contiene alguna palabra significativa del juego. */
    fun matches(label: String, tokens: List<String>): Boolean {
        if (tokens.isEmpty()) return false
        val normalized = normalize(label)
        return tokens.any { normalized.contains(it) }
    }

    /**
     * Palabras significativas de un nombre de juego.
     *
     * `"The Witcher 3: Wild Hunt"` → `["witcher", "wild", "hunt"]`. Se
     * descartan artículos, sufijos comerciales y palabras de menos de tres
     * letras, que son las que producen falsos positivos.
     */
    fun tokensOf(gameName: String?): List<String> {
        if (gameName.isNullOrBlank()) return emptyList()
        return normalize(gameName)
            .split(' ')
            .filter { it.length >= 3 && it !in STOP_WORDS }
            .distinct()
    }

    /** Minúsculas y solo letras/dígitos: `"My Games_2"` → `"my games 2"`. */
    private fun normalize(text: String): String = text
        .lowercase()
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .split(' ')
        .filter { it.isNotEmpty() }
        .joinToString(" ")

    /** `true` si la carpeta parece de guardado por su nombre. */
    fun looksLikeSaveFolder(name: String): Boolean {
        val normalized = normalize(name).replace(" ", "")
        return SAVE_TOKENS.any { normalized.contains(it) }
    }
}
