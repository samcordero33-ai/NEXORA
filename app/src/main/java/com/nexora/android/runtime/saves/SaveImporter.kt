package com.nexora.android.runtime.saves

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Desempaqueta un ZIP de partidas guardadas dentro de [targetDir].
 *
 * Seguridad: cada entrada se valida para que su ruta resuelta **caiga dentro**
 * de [targetDir]. Esto evita el ataque clásico "Zip Slip" donde una entrada
 * `../../etc/passwd` se sale del directorio de destino. Si una entrada es
 * sospechosa, se ignora y se contabiliza.
 *
 * Las entradas que son directorios no se materializan: `ZipInputStream` los
 * entrega vacíos y los saltamos.
 */
object SaveImporter {

    data class ExtractSummary(
        val entriesExtracted: Int,
        val totalBytes: Long,
        val rejectedEntries: List<String>,
    )

    fun extract(zipFile: File, targetDir: File): ExtractSummary {
        require(zipFile.exists() && zipFile.isFile) {
            "El ZIP no existe: ${zipFile.absolutePath}"
        }
        targetDir.mkdirs()

        var count = 0
        var bytes = 0L
        val rejected = mutableListOf<String>()

        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zip ->
            var entry = zip.nextEntry
            val canonicalTarget = targetDir.canonicalPath
            while (entry != null) {
                if (!entry.isDirectory) {
                    val out = File(targetDir, entry.name)
                    val canonicalOut = out.canonicalPath
                    if (!canonicalOut.startsWith(canonicalTarget + File.separator) &&
                        canonicalOut != canonicalTarget
                    ) {
                        rejected += entry.name
                    } else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { fos ->
                            val copied = zip.copyTo(fos, bufferSize = 8 * 1024)
                            bytes += copied
                        }
                        count += 1
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return ExtractSummary(count, bytes, rejected)
    }
}