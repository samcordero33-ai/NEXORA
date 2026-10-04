package com.nexora.android.runtime.env

import java.io.File

/**
 * Fábrica abstracta de archivos.
 *
 * Se inyecta en [WinePrefix] para que el código sea **probable en JVM**: en
 * pruebas se pasa una fábrica en memoria; en Android, la implementación por
 * defecto ([FileSystemPrefixFactory]) usa el `filesDir` del proceso.
 */
fun interface PrefixFileFactory {

    /** Devuelve un `File` para una ruta absoluta o relativa al prefijo. */
    fun file(path: String): File

    companion object {
        /** Fábrica que monta todo bajo un directorio raíz. */
        fun rooted(root: File): PrefixFileFactory = PrefixFileFactory { path ->
            val cleaned = path.trimStart('/')
            if (cleaned.isEmpty()) root else File(root, cleaned)
        }
    }
}

/**
 * Gestor del directorio del prefijo de Wine (`WINEPREFIX`).
 *
 * Wine aísla cada instalación en un directorio propio que contiene una
 * jerarquía tipo Unix: `drive_c/`, `dosdevices/c:`, `system.reg`, etc. Aquí
 * modelamos **solo la estructura** y los archivos de marcadores que NEXORA
 * necesita; no tocamos el contenido del juego.
 *
 * El prefijo vive bajo `filesDir/prefixes/<gameId>` por defecto: cada juego
 * tiene su propio `WINEPREFIX`, así no comparten caché de DXVK ni DLLs
 * sobrescritas. La ruta exacta se calcula en [pathFor].
 */
class WinePrefix(
    private val factory: PrefixFileFactory,
    private val gameId: String,
) {

    /**
     * Ruta absoluta que se exportará como `WINEPREFIX` en el entorno.
     *
     * Existe aunque el prefijo no esté creado todavía: es la ruta que el
     * proceso verá cuando arranque.
     */
    val path: File get() = factory.file(rootRelative)

    private val rootRelative: String = "prefixes/$gameId"

    /** `drive_c` del prefijo, donde el juego cree estar ejecutándose. */
    val driveC: File get() = factory.file("$rootRelative/drive_c")

    /**
     * Crea la jerarquía mínima del prefijo en disco.
     *
     * Es seguro llamarlo muchas veces: solo crea lo que no existe. Si el
     * directorio ya contiene un prefijo de Wine válido, **no se sobrescribe**:
     * un segundo arranque del mismo juego reusa lo que ya estaba.
     *
     * @return Lista de archivos creados (para registro / diagnóstico).
     */
    fun createIfMissing(): List<File> {
        val created = mutableListOf<File>()
        ensureDirectory(path, created)
        ensureDirectory(driveC, created)
        ensureDirectory(File(driveC, "Program Files"), created)
        ensureDirectory(File(driveC, "Program Files (x86)"), created)
        ensureDirectory(File(driveC, "windows"), created)
        ensureDirectory(File(driveC, "users"), created)
        return created
    }

    /** `true` si la jerarquía mínima existe en disco. */
    fun exists(): Boolean = path.isDirectory && driveC.isDirectory

    /**
     * Borra el prefijo entero del juego. **No se llama desde la app**: la
     * posibilidad existe para pruebas y para una futura opción de "Restablecer
     * prefijo" en el editor.
     */
    fun delete(): Boolean = path.deleteRecursively()

    private fun ensureDirectory(directory: File, created: MutableList<File>) {
        if (!directory.exists() && directory.mkdirs()) {
            created += directory
        }
    }

    companion object {

        /**
         * Construye un [WinePrefix] bajo `filesDir`, uno por juego.
         *
         * @param filesDir Directorio privado del proceso (`Context.filesDir`).
         * @param gameId Identificador estable del juego.
         */
        fun underFilesDir(filesDir: File, gameId: String): WinePrefix {
            require(filesDir.isAbsolute) {
                "filesDir debe ser absoluto para construir WINEPREFIX"
            }
            require(gameId.isNotBlank()) { "gameId no puede estar vacío" }
            return WinePrefix(PrefixFileFactory.rooted(filesDir), gameId)
        }
    }
}