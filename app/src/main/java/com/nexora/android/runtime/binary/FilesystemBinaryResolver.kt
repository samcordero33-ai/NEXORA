package com.nexora.android.runtime.binary

import com.nexora.android.runtime.engine.BinaryResolver
import java.io.File

/**
 * Resuelve un nombre de binario a una ruta **real y ejecutable**.
 *
 * Orden de búsqueda:
 * 1. Si el nombre ya es una ruta absoluta y es ejecutable, se devuelve tal cual.
 * 2. Los binarios del runtime instalado (`filesDir/runtime/bin/<nombre>`), que
 *    es el caso normal tras [RuntimeBinaryLoader.ensureInstalled].
 * 3. `PATH` del sistema (`/system/bin`, `/vendor/bin`…), por si el dispositivo
 *    ya trae `sh` o un `wine` de una ROM concreta.
 *
 * Devuelve `null` si no hay nada ejecutable. No "adivina": un archivo que
 * existe pero no tiene permiso de ejecución no sirve, y devolverlo solo
 * cambiaría un error claro (`BinaryNotInstalledException`) por un
 * `Permission denied` del sistema.
 */
class FilesystemBinaryResolver(
    private val layoutProvider: () -> RuntimeLayout?,
    private val systemPaths: List<String> = DEFAULT_SYSTEM_PATHS,
) : BinaryResolver {

    override fun resolve(name: String): String? {
        if (name.isBlank()) return null

        if (name.startsWith("/")) {
            return name.takeIf { File(it).canExecute() }
        }

        val layout = layoutProvider()
        if (layout != null) {
            val installed = layout.binaries.values.firstOrNull { File(it).name == name }
            if (installed != null && File(installed).canExecute()) return installed

            val direct = File(layout.binDir, name)
            if (direct.canExecute()) return direct.absolutePath
        }

        for (dir in systemPaths) {
            val candidate = File(dir, name)
            if (candidate.canExecute()) return candidate.absolutePath
        }
        return null
    }

    companion object {
        val DEFAULT_SYSTEM_PATHS = listOf("/system/bin", "/system/xbin", "/vendor/bin", "/product/bin")
    }
}
