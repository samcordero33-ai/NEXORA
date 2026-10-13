package com.nexora.android.runtime.saves

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Crea un archivo ZIP a partir de un directorio, conservando la estructura
 * relativa de archivos. Pensado para exportar partidas guardadas de un
 * `WINEPREFIX` específico a una ruta accesible desde la app (la carpeta
 * `Documents/` de Android).
 *
 * Es código JDK puro (`java.util.zip`); **no usa** Swing ni AWT, así que
 * sigue siendo válido en Android.
 *
 * Política de paths: si [root] apunta a `/A/B/C` y contiene `/A/B/C/save.dat`,
 * el ZIP contiene `save.dat` en la raíz. Si [entryPrefix] es no vacío, ese
 * prefijo se antepone (útil para meter todos los exports bajo `nexora/`).
 */
object SaveBackupArchive {

    /**
     * Empaqueta [root] dentro de [outputZip], opcionalmente bajo [entryPrefix].
     *
     * @return número de archivos empaquetados (sin contar carpetas).
     */
    fun pack(root: File, outputZip: File, entryPrefix: String = ""): Int {
        require(root.exists() && root.isDirectory) {
            "El prefijo no existe o no es un directorio: ${root.absolutePath}"
        }
        outputZip.parentFile?.mkdirs()

        val prefix = entryPrefix.trim('/').let { if (it.isEmpty()) "" else "$it/" }
        return ZipOutputStream(BufferedOutputStream(outputZip.outputStream())).use { zip ->
            zip.setLevel(Deflater.DEFAULT_COMPRESSION)
            packInto(root, root, prefix, zip)
        }
    }

    /** Devuelve el número de archivos escritos en la rama [current]. */
    private fun packInto(
        root: File,
        current: File,
        prefix: String,
        zip: ZipOutputStream,
    ): Int {
        val children = current.listFiles() ?: return 0
        var written = 0
        for (child in children) {
            if (child.isDirectory) {
                written += packInto(root, child, prefix, zip)
            } else if (child.isFile) {
                val rel = relativePath(root, child)
                val entryName = "$prefix$rel"
                zip.putNextEntry(ZipEntry(entryName))
                FileInputStream(child).use { input ->
                    input.copyTo(zip, bufferSize = 8 * 1024)
                }
                zip.closeEntry()
                written += 1
            }
        }
        return written
    }

    private fun relativePath(root: File, file: File): String {
        val rootPath = root.absolutePath
        val filePath = file.absolutePath
        val prefix = if (rootPath.endsWith(File.separator)) rootPath else "$rootPath${File.separator}"
        return if (filePath.startsWith(prefix)) {
            filePath.removePrefix(prefix)
        } else {
            file.name
        }
    }
}