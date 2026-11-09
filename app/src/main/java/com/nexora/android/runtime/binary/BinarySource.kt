package com.nexora.android.runtime.binary

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/**
 * Origen de los archivos que se van a instalar.
 *
 * En producción es `AssetManager` (los binarios viajan en el APK o en un
 * paquete de expansión); en pruebas es un mapa en memoria. El instalador no
 * sabe de dónde vienen: solo pide `exists` y `open`.
 */
interface BinarySource {

    /** `true` si el origen contiene ese archivo. */
    fun exists(path: String): Boolean

    /** Abre el archivo para copiarlo. Lanza si no existe. */
    fun open(path: String): InputStream
}

/**
 * Origen en memoria: útil para pruebas y para previsualizar una instalación
 * sin tocar el disco.
 */
class ByteArrayBinarySource(
    private val files: Map<String, ByteArray>,
) : BinarySource {

    override fun exists(path: String): Boolean = files.containsKey(path.normalize())

    override fun open(path: String): InputStream =
        ByteArrayInputStream(files.getValue(path.normalize()))

    private fun String.normalize(): String = trimStart('/')
}

/**
 * Origen en disco: una carpeta que el usuario ha elegido (por ejemplo, un
 * runtime descomprimido a mano en `Download/nexora-runtime`).
 *
 * Rechaza rutas que se escapan del directorio base, igual que el instalador
 * rechaza destinos fuera de su raíz: si el origen no es de confianza, se
 * valida en los dos extremos.
 */
class DirectoryBinarySource(
    private val root: File,
) : BinarySource {

    override fun exists(path: String): Boolean {
        val file = resolve(path) ?: return false
        return file.isFile && file.canRead()
    }

    override fun open(path: String): InputStream {
        val file = resolve(path) ?: throw java.io.FileNotFoundException(path)
        return file.inputStream()
    }

    private fun resolve(path: String): File? {
        val base = root.canonicalFile
        val candidate = File(base, path.trimStart('/')).canonicalFile
        return if (candidate.path == base.path || candidate.path.startsWith(base.path + File.separator)) {
            candidate
        } else {
            null
        }
    }
}
