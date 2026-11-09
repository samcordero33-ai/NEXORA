package com.nexora.android.runtime.saves

import java.io.File

/**
 * Un respaldo de partidas ya escrito en disco.
 *
 * @property file Archivo ZIP.
 * @property stamp Juego y fecha, deducidos del nombre.
 * @property bytes Tamaño del ZIP.
 */
data class SaveBackup(
    val file: File,
    val stamp: BackupStamp,
    val bytes: Long,
) {
    val name: String get() = file.name
    val label: String get() = BackupNaming.label(stamp)
}

/**
 * Almacén de respaldos de partidas, uno por juego.
 *
 * Estructura en disco:
 *
 * ```
 * filesDir/save_backups/<gameId>/nexora-saves_<gameId>_20250312-180412.zip
 * ```
 *
 * No hay base de datos: el listado se reconstruye leyendo el directorio. Un
 * ZIP que no siga la convención de nombres **no** se lista (no se puede saber
 * de qué juego es ni cuándo se hizo), pero tampoco se borra.
 *
 * El contenido del ZIP es **relativo a `drive_c`**, no al prefijo entero. Es
 * la decisión que hace que exportar e importar sean simétricos: lo que se
 * empaqueta desde `drive_c` se restaura sobre `drive_c`, sin prefijos
 * intermedios que acaben creando `drive_c/nexora/<id>/...` al restaurar.
 */
class SaveBackupStore(
    private val rootDir: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Carpeta de respaldos de un juego. */
    fun dirFor(gameId: String): File = File(rootDir, BackupNaming.sanitize(gameId))

    /** Respaldos existentes, del más reciente al más antiguo. */
    fun list(gameId: String): List<SaveBackup> {
        val dir = dirFor(gameId)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { file -> file.isFile && file.name.endsWith(".zip") }
            .orEmpty()
            .mapNotNull { file ->
                val stamp = BackupNaming.parse(file.name) ?: return@mapNotNull null
                SaveBackup(file = file, stamp = stamp, bytes = file.length())
            }
            .sortedByDescending { it.stamp.epochMs }
    }

    /**
     * Empaqueta [sourceRoot] en un ZIP nuevo.
     *
     * @throws IllegalArgumentException si el origen no existe o no es carpeta.
     */
    fun create(gameId: String, sourceRoot: File): SaveBackup {
        require(sourceRoot.isDirectory) {
            "No hay carpeta que respaldar: ${sourceRoot.absolutePath}"
        }
        val dir = dirFor(gameId).apply { mkdirs() }
        val epoch = clock()
        // Dos respaldos en el mismo segundo se pisarían: se avanza hasta que
        // el nombre esté libre. Es más honesto que sobreescribir en silencio.
        var target = File(dir, BackupNaming.fileName(gameId, epoch))
        var offset = 1_000L
        while (target.exists()) {
            target = File(dir, BackupNaming.fileName(gameId, epoch + offset))
            offset += 1_000L
        }

        SaveBackupArchive.pack(root = sourceRoot, outputZip = target, entryPrefix = "")
        val stamp = BackupNaming.parse(target.name)
            ?: BackupStamp(gameId = gameId, epochMs = epoch)
        return SaveBackup(file = target, stamp = stamp, bytes = target.length())
    }

    /** Borra un respaldo. Devuelve `true` si el archivo ya no está. */
    fun delete(backup: SaveBackup): Boolean {
        val deleted = runCatching { backup.file.delete() }.getOrDefault(false)
        return deleted || !backup.file.exists()
    }

    /**
     * Conserva solo los [keep] respaldos más recientes de un juego.
     *
     * @return cuántos se borraron.
     */
    fun prune(gameId: String, keep: Int): Int {
        require(keep >= 0) { "keep no puede ser negativo" }
        val all = list(gameId)
        if (all.size <= keep) return 0
        return all.drop(keep).count { delete(it) }
    }

    /** Tamaño total ocupado por los respaldos de un juego. */
    fun totalBytes(gameId: String): Long = list(gameId).sumOf { it.bytes }
}
