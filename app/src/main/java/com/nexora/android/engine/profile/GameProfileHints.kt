package com.nexora.android.engine.profile

/**
 * Lo que NEXORA sabe de un juego **sin ejecutarlo**.
 *
 * El AutoOptimizer necesita dos cosas: qué hardware hay y qué pide el juego. Lo
 * segundo no se puede leer del ejecutable (interpretar un binario de Windows
 * está fuera del alcance de NEXORA y además sería innecesario), así que se
 * deduce de dos señales observables y baratas:
 *
 * - Las librerías que acompañan al ejecutable: un `d3d9.dll` indica DirectX 9,
 *   un `d3d11.dll` indica DirectX 11 y un `vulkan-1.dll` indica Vulkan.
 * - El propio nombre del juego, del que se deduce el género.
 *
 * Ninguna de las dos es infalible; por eso son *pistas* que suman puntos en la
 * recomendación en vez de decisiones en firme.
 *
 * @property title Título del juego, base de la detección de género.
 * @property installDirectory Carpeta inspeccionada, o `null`.
 * @property sizeBytes Tamaño del ejecutable.
 * @property detectedApis APIs gráficas detectadas junto al ejecutable.
 * @property genre Género deducido; [GameGenre.UNKNOWN] si no se reconoce.
 * @property inspected `true` si se pudo mirar la carpeta de instalación. Si es
 *   `false`, la ausencia de APIs detectadas **no** significa nada.
 */
data class GameProfileHints(
    val title: String,
    val installDirectory: String? = null,
    val sizeBytes: Long = 0L,
    val detectedApis: Set<GraphicsApi> = emptySet(),
    val genre: GameGenre = GameGenre.UNKNOWN,
    val inspected: Boolean = false,
) {

    /**
     * API gráfica más moderna de las detectadas.
     *
     * Si un juego trae varias (algo habitual: motores con varios backends),
     * manda la más nueva, que es la que el juego usará por defecto.
     */
    val primaryApi: GraphicsApi?
        get() = detectedApis.maxByOrNull { it.modernity }

    companion object {

        /** Pistas vacías: ni género ni API, y carpeta no inspeccionada. */
        val EMPTY = GameProfileHints(title = "")

        /**
         * Construye las pistas a partir de los nombres de archivo de la carpeta
         * de instalación.
         */
        fun fromFiles(
            title: String,
            installDirectory: String?,
            sizeBytes: Long,
            fileNames: List<String>,
        ): GameProfileHints = GameProfileHints(
            title = title,
            installDirectory = installDirectory,
            sizeBytes = sizeBytes,
            detectedApis = fileNames.mapNotNull(::apiOfFile).toSet(),
            genre = genreOf(title),
            inspected = true,
        )

        /**
         * Construye las pistas sin haber podido mirar la carpeta: solo queda el
         * género deducido del título.
         */
        fun fromTitleOnly(
            title: String,
            installDirectory: String?,
            sizeBytes: Long,
        ): GameProfileHints = GameProfileHints(
            title = title,
            installDirectory = installDirectory,
            sizeBytes = sizeBytes,
            genre = genreOf(title),
            inspected = false,
        )

        /** Librería -> API gráfica que implica. */
        private fun apiOfFile(fileName: String): GraphicsApi? {
            val name = fileName.lowercase()
            return when {
                name == "d3d11.dll" || name.startsWith("d3dx11") || name == "dxgi.dll" ->
                    GraphicsApi.D3D11

                name == "d3d10.dll" || name.startsWith("d3dx10") ||
                    name.startsWith("d3d10_") -> GraphicsApi.D3D10

                name == "d3d9.dll" || name.startsWith("d3dx9") -> GraphicsApi.D3D9

                name == "vulkan-1.dll" || name == "vulkan.dll" -> GraphicsApi.VULKAN

                name == "opengl32.dll" || name == "libglesv2.dll" -> GraphicsApi.OPENGL

                else -> null
            }
        }

        /**
         * Género deducido del título.
         *
         * La lista cubre los géneros que cambian la decisión del optimizador
         * (una estrategia en tiempo real y un juego de conducción no piden lo
         * mismo); todo lo demás queda sin clasificar, que es un resultado
         * válido: simplemente no puntúa.
         */
        private fun genreOf(title: String): GameGenre {
            val haystack = title.lowercase()
            if (haystack.isBlank()) return GameGenre.UNKNOWN

            GENRE_KEYWORDS.forEach { (genre, keywords) ->
                if (keywords.any { haystack.contains(it) }) return genre
            }
            return GameGenre.UNKNOWN
        }

        private val GENRE_KEYWORDS: List<Pair<GameGenre, List<String>>> = listOf(
            GameGenre.RTS to listOf(
                "age of empires", "age of mythology", "command & conquer",
                "command and conquer", "red alert", "starcraft", "warcraft",
                "company of heroes", "total war", "stronghold", "cossacks",
                "empire earth", "rise of nations", "dune 2000", "homeworld",
                "dawn of war", "men of war", "blitzkrieg", "strateg", " rts",
            ),
            GameGenre.RACING to listOf(
                "need for speed", "forza", "gran turismo", "burnout", "dirt ",
                "ralli", "racing", "race driver", "grid ", "trackmania",
            ),
            GameGenre.FPS to listOf(
                "call of duty", "battlefield", "half-life", "counter-strike",
                "doom", "quake", "halo", "borderlands", "bioshock", "left 4 dead",
                "shooter", "metro 2033", "far cry", "crysis",
            ),
            GameGenre.RPG to listOf(
                "witcher", "skyrim", "fallout", "baldur", "divinity",
                "final fantasy", "dragon age", "mass effect", "diablo",
                "gothic", "vampire", " rpg", "path of exile",
            ),
            GameGenre.SIMULATION to listOf(
                "sims", "simulator", "farming", "euro truck", "american truck",
                "flight sim", "cities", "planet coaster", "railway",
            ),
            GameGenre.PLATFORMER to listOf(
                "hollow knight", "celeste", "ori and", "rayman", "limbo",
                "inside", "braid", "shovel knight", "cuphead",
            ),
        )
    }
}

/** API gráfica detectada junto al ejecutable. */
enum class GraphicsApi(
    val label: String,
    /** Orden de modernidad: manda la más alta de las detectadas. */
    val modernity: Int,
) {
    D3D9("DirectX 9", 1),
    D3D10("DirectX 10", 2),
    D3D11("DirectX 11", 3),
    VULKAN("Vulkan", 4),
    OPENGL("OpenGL", 0),
}

/** Género deducido del juego. */
enum class GameGenre(
    val label: String,
) {
    RTS("Estrategia en tiempo real"),
    FPS("Disparos en primera persona"),
    RPG("Rol"),
    RACING("Conducción"),
    SIMULATION("Simulación"),
    PLATFORMER("Plataformas"),
    UNKNOWN("Sin clasificar"),
}
