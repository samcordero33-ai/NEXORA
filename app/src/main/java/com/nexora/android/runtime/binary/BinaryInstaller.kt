package com.nexora.android.runtime.binary

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Extrae los binarios del origen al directorio de instalación y les da permiso
 * de ejecución.
 *
 * Reglas que no se negocian:
 *
 * 1. **Nada se escribe fuera de la raíz.** El destino se resuelve con
 *    `canonicalFile` y se comprueba que siga dentro: un manifiesto manipulado
 *    con `../../` no puede sobrescribir datos de la app.
 * 2. **Se escribe a un `.part` y se renombra.** Si el proceso muere a mitad de
 *    copia, no queda un `wine64` truncado que luego falle de forma críptica.
 * 3. **La huella se verifica antes de renombrar.** Un binario alterado no
 *    llega nunca a su ruta definitiva.
 * 4. **Los permisos se aplican desde Kotlin**, con `setExecutable(true, false)`:
 *    solo el propietario (la app), nunca "todos".
 *
 * Es Kotlin puro (solo `java.io`): se prueba en JVM con un origen en memoria.
 */
class BinaryInstaller(
    private val source: BinarySource,
    private val root: File,
) {

    /**
     * Instala las entradas indicadas.
     *
     * @return informe con lo instalado, lo que ya estaba al día, lo que no
     *   venía en el origen y lo que falló. Nunca lanza por una entrada: una
     *   pieza rota no debe impedir instalar las demás.
     */
    fun install(entries: List<BinaryEntry>): InstallReport {
        val installed = mutableListOf<InstalledBinary>()
        val upToDate = mutableListOf<InstalledBinary>()
        val missing = mutableListOf<String>()
        val failures = mutableListOf<InstallFailure>()

        for (entry in entries) {
            if (!source.exists(entry.assetPath)) {
                missing += "${entry.component.id}:${entry.assetPath}"
                continue
            }
            val target = resolveTarget(entry.targetPath)
            if (target == null) {
                failures += InstallFailure(
                    entry = entry,
                    reason = "La ruta destino se sale del directorio de instalación",
                )
                continue
            }
            try {
                if (isUpToDate(entry, target)) {
                    applyPermissions(entry, target)
                    upToDate += InstalledBinary(
                        component = entry.component,
                        path = target.absolutePath,
                        sizeBytes = target.length(),
                        sha256 = if (entry.hasChecksum) entry.sha256 else "",
                        executable = entry.executable,
                    )
                    continue
                }
                installed += copyEntry(entry, target)
            } catch (error: Exception) {
                failures += InstallFailure(
                    entry = entry,
                    reason = error.message ?: error.javaClass.simpleName,
                )
            }
        }

        return InstallReport(
            installed = installed,
            upToDate = upToDate,
            missing = missing,
            failures = failures,
        )
    }

    /**
     * `true` si el archivo destino ya existe, tiene el tamaño esperado y su
     * huella coincide (cuando el manifiesto la declara).
     */
    fun isUpToDate(entry: BinaryEntry, target: File): Boolean {
        if (!target.isFile) return false
        if (entry.sizeBytes >= 0 && target.length() != entry.sizeBytes) return false
        if (!entry.hasChecksum) return true
        return Sha256.matches(entry.sha256, Sha256.of(target))
    }

    private fun copyEntry(entry: BinaryEntry, target: File): InstalledBinary {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.part")
        if (temp.exists()) temp.delete()

        var written = 0L
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        source.open(entry.assetPath).use { input: InputStream ->
            FileOutputStream(temp).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    written += read
                }
                output.flush()
                output.fd.sync()
            }
        }

        val actual = digest.digest().joinToString("") {
            String.format(java.util.Locale.US, "%02x", it)
        }
        if (entry.hasChecksum && !Sha256.matches(entry.sha256, actual)) {
            temp.delete()
            throw IllegalStateException(
                "Huella distinta para ${entry.assetPath} (esperada ${entry.sha256.take(12)}…, " +
                    "obtenida ${actual.take(12)}…)"
            )
        }
        if (entry.sizeBytes >= 0 && written != entry.sizeBytes) {
            temp.delete()
            throw IllegalStateException(
                "Tamaño distinto para ${entry.assetPath}: $written de ${entry.sizeBytes} bytes"
            )
        }

        if (target.exists() && !target.delete()) {
            temp.delete()
            throw IllegalStateException("No se pudo reemplazar ${target.absolutePath}")
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IllegalStateException("No se pudo mover ${temp.name} a ${target.name}")
        }
        applyPermissions(entry, target)
        return InstalledBinary(
            component = entry.component,
            path = target.absolutePath,
            sizeBytes = written,
            sha256 = actual,
            executable = entry.executable,
        )
    }

    /**
     * Aplica permisos: lectura para el dueño y, si toca, ejecución.
     *
     * `ownerOnly = true` en el segundo parámetro evita el clásico `chmod 777`:
     * otro proceso del mismo dispositivo no necesita ejecutar los binarios de
     * NEXORA.
     */
    private fun applyPermissions(entry: BinaryEntry, target: File) {
        target.setReadable(true, true)
        target.setWritable(false, false)
        if (entry.executable) {
            target.setExecutable(true, true)
        }
    }

    /** Resuelve el destino y comprueba que no se escape de [root]. */
    private fun resolveTarget(targetPath: String): File? {
        val base = root.canonicalFile
        val candidate = File(base, targetPath.trimStart('/')).canonicalFile
        return if (candidate.path.startsWith(base.path + File.separator)) candidate else null
    }
}

/** Un archivo que quedó instalado en su ruta definitiva. */
data class InstalledBinary(
    val component: BinaryComponent,
    val path: String,
    val sizeBytes: Long,
    val sha256: String,
    val executable: Boolean,
)

/** Una entrada que no se pudo instalar, con el motivo en claro. */
data class InstallFailure(
    val entry: BinaryEntry,
    val reason: String,
)

/**
 * Resultado de una instalación.
 *
 * `missing` no es un error: significa que ese componente no viaja en el APK
 * (o el usuario no lo copió). La app sigue funcionando y lo dice en la UI.
 */
data class InstallReport(
    val installed: List<InstalledBinary>,
    val upToDate: List<InstalledBinary>,
    val missing: List<String>,
    val failures: List<InstallFailure>,
) {

    /** `true` si no hubo ningún fallo (que no es lo mismo que "hay motor"). */
    val ok: Boolean get() = failures.isEmpty()

    /** Bytes realmente escritos en esta pasada. */
    val writtenBytes: Long get() = installed.sumOf { it.sizeBytes }

    /** Componentes con al menos un archivo instalado o ya presente. */
    val availableComponents: Set<BinaryComponent>
        get() = (installed + upToDate).map { it.component }.toSet()

    /** Resumen de una línea para el visor de diagnóstico. */
    fun summary(): String {
        val parts = mutableListOf<String>()
        if (installed.isNotEmpty()) parts += "${installed.size} instalados"
        if (upToDate.isNotEmpty()) parts += "${upToDate.size} al día"
        if (missing.isNotEmpty()) parts += "${missing.size} sin empaquetar"
        if (failures.isNotEmpty()) parts += "${failures.size} con error"
        return if (parts.isEmpty()) "Sin componentes en el manifiesto" else parts.joinToString(", ")
    }
}
