package com.nexora.android.runtime.binary

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * Extrae un `.tar.gz` dentro de un directorio, sin dependencias externas.
 *
 * Se usa para los componentes que no son un archivo suelto sino un árbol: el
 * runtime de Wine tiene ~1.700 archivos y declararlos uno por uno en el
 * manifiesto sería inmanejable.
 *
 * Reglas, las mismas que en [BinaryInstaller]:
 *
 * 1. **Nada sale de [root].** Cada entrada se resuelve con `canonicalFile` y se
 *    comprueba que siga dentro. Una entrada `../../databases/app.db` se
 *    descarta.
 * 2. **Los enlaces simbólicos absolutos se ignoran.** Un `.tar` puede traer
 *    `libfoo.so -> /usr/lib/libfoo.so`; crearlo dentro de la app no aporta nada
 *    y abre una vía de escape si algo escribe a través de él.
 * 3. **El bit de ejecución sale del modo del tar**, no de una lista blanca: el
 *    empaquetador ya decidió qué es ejecutable.
 * 4. **Se lee exactamente `size` bytes.** Si el flujo se corta, se lanza: un
 *    árbol a medias es peor que un error.
 *
 * Solo usa `java.io` y `java.nio.file` (API 26+), así que se prueba en JVM.
 */
class TarGzExtractor(private val root: File) {

    /**
     * Resultado de la extracción.
     *
     * @property skipped Entradas descartadas por ruta insegura o enlace
     *   absoluto. No es un error, pero se reporta para que no pase inadvertido.
     */
    data class Stats(
        val files: Int,
        val directories: Int,
        val links: Int,
        val skipped: Int,
        val bytes: Long,
    ) {
        fun summary(): String =
            "$files archivos, $directories carpetas, $links enlaces" +
                if (skipped > 0) " ($skipped descartados)" else ""
    }

    fun extract(stream: InputStream): Stats {
        val base = root.canonicalFile
        if (!base.exists() && !base.mkdirs()) {
            throw IOException("No se pudo crear ${base.absolutePath}")
        }

        var files = 0
        var directories = 0
        var links = 0
        var skipped = 0
        var bytes = 0L

        var pendingName: String? = null
        var pendingLink: String? = null

        GZIPInputStream(stream, BUFFER).use { tar ->
            while (true) {
                val header = readBlock(tar) ?: break
                if (header.isEmptyBlock()) break

                val type = header[156].toInt().and(0xFF).toChar()
                val size = header.readNumber(124, 12)

                // Metadatos de GNU tar: el nombre real viene en el bloque
                // siguiente, no en la cabecera.
                when (type) {
                    'L' -> {
                        pendingName = readData(tar, size).toString(Charsets.UTF_8).trimEnd('\u0000')
                        continue
                    }
                    'K' -> {
                        pendingLink = readData(tar, size).toString(Charsets.UTF_8).trimEnd('\u0000')
                        continue
                    }
                    'x', 'g', 'V' -> {
                        skipData(tar, size)
                        continue
                    }
                }

                val name = pendingName ?: header.readName()
                val link = pendingLink ?: header.readString(157, 100)
                pendingName = null
                pendingLink = null

                val target = resolve(base, name)
                if (target == null) {
                    skipped++
                    skipData(tar, size)
                    continue
                }

                when (type) {
                    '5' -> {
                        if (!target.exists() && !target.mkdirs()) {
                            throw IOException("No se pudo crear ${target.absolutePath}")
                        }
                        directories++
                        skipData(tar, size)
                    }
                    '2' -> {
                        if (link.isEmpty() || Paths.get(link).isAbsolute) {
                            skipped++
                        } else {
                            target.parentFile?.mkdirs()
                            target.delete()
                            Files.createSymbolicLink(target.toPath(), Paths.get(link))
                            links++
                        }
                        skipData(tar, size)
                    }
                    '1' -> {
                        val source = resolve(base, link)
                        if (source == null || !source.isFile) {
                            skipped++
                        } else {
                            target.parentFile?.mkdirs()
                            source.copyTo(target, overwrite = true)
                            applyMode(target, header.readNumber(100, 8))
                            files++
                            bytes += target.length()
                        }
                        skipData(tar, size)
                    }
                    else -> {
                        target.parentFile?.mkdirs()
                        bytes += writeFile(tar, target, size)
                        applyMode(target, header.readNumber(100, 8))
                        files++
                    }
                }
            }
        }

        return Stats(files, directories, links, skipped, bytes)
    }

    /** Copia exactamente [size] bytes y consume el relleno hasta el bloque. */
    private fun writeFile(tar: InputStream, target: File, size: Long): Long {
        var remaining = size
        var written = 0L
        FileOutputStream(target).use { output ->
            val buffer = ByteArray(BUFFER)
            while (remaining > 0) {
                val chunk = minOf(remaining, buffer.size.toLong()).toInt()
                val read = tar.read(buffer, 0, chunk)
                if (read <= 0) {
                    throw IOException("Archivo truncado al extraer ${target.name}")
                }
                output.write(buffer, 0, read)
                written += read
                remaining -= read
            }
            output.flush()
            output.fd.sync()
        }
        skipPadding(tar, size)
        return written
    }

    private fun applyMode(target: File, mode: Long) {
        val executable = (mode and 0b001_000_000L) != 0L
        target.setReadable(true, false)
        target.setWritable(false, false)
        target.setExecutable(executable, false)
    }

    /** Resuelve y valida la ruta; `null` si se escapa de [base]. */
    private fun resolve(base: File, name: String): File? {
        var clean = name.replace('\\', '/')
        // Una ruta absoluta no se reinterpreta como relativa: se descarta.
        if (clean.startsWith("/")) return null
        while (clean.startsWith("./")) clean = clean.substring(2)
        if (clean.isEmpty() || clean == ".") return null
        if (clean.split('/').any { it == ".." }) return null
        return try {
            val candidate = File(base, clean).canonicalFile
            if (candidate.path == base.path || candidate.path.startsWith(base.path + File.separator)) {
                candidate
            } else {
                null
            }
        } catch (error: IOException) {
            null
        }
    }

    private fun readBlock(input: InputStream): ByteArray? {
        val block = ByteArray(BLOCK)
        var read = 0
        while (read < BLOCK) {
            val count = input.read(block, read, BLOCK - read)
            if (count < 0) {
                if (read == 0) return null
                throw IOException("Cabecera tar truncada")
            }
            read += count
        }
        return block
    }

    private fun readData(input: InputStream, size: Long): ByteArray {
        if (size > MAX_METADATA) throw IOException("Metadato tar demasiado grande: $size")
        val data = ByteArray(size.toInt())
        var read = 0
        while (read < data.size) {
            val count = input.read(data, read, data.size - read)
            if (count < 0) throw IOException("Metadato tar truncado")
            read += count
        }
        skipPadding(input, size)
        return data
    }

    private fun skipData(input: InputStream, size: Long) {
        var remaining = size
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() < 0) throw IOException("Contenido tar truncado")
                remaining -= 1
            } else {
                remaining -= skipped
            }
        }
        skipPadding(input, size)
    }

    private fun skipPadding(input: InputStream, size: Long) {
        val padding = (BLOCK - (size % BLOCK)) % BLOCK
        var remaining = padding
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() < 0) return
                remaining -= 1
            } else {
                remaining -= skipped
            }
        }
    }

    private fun ByteArray.isEmptyBlock(): Boolean = all { it == 0.toByte() }

    private fun ByteArray.readString(offset: Int, length: Int): String {
        val end = (offset until offset + length).firstOrNull { this[it] == 0.toByte() } ?: (offset + length)
        return String(this, offset, end - offset, Charsets.UTF_8)
    }

    /** Nombre completo: `prefix/name` cuando la cabecera es POSIX ustar. */
    private fun ByteArray.readName(): String {
        val name = readString(0, 100)
        val prefix = readString(345, 155)
        return if (prefix.isEmpty()) name else "$prefix/$name"
    }

    /**
     * Lee un campo numérico octal. GNU tar usa base-256 para tamaños que no
     * caben en 11 dígitos octales: el bit alto del primer byte lo delata.
     */
    private fun ByteArray.readNumber(offset: Int, length: Int): Long {
        if (this[offset].toInt() and 0x80 != 0) {
            var value = (this[offset].toInt() and 0x7F).toLong()
            for (i in 1 until length) {
                value = (value shl 8) or (this[offset + i].toInt() and 0xFF).toLong()
            }
            return value
        }
        val text = readString(offset, length).trim()
        if (text.isEmpty()) return 0L
        return text.toLongOrNull(8)
            ?: throw IOException("Campo numérico tar inválido: «${text.take(12)}»")
    }

    private companion object {
        const val BLOCK = 512
        const val BUFFER = 64 * 1024
        const val MAX_METADATA = 1024L * 1024L
    }
}
