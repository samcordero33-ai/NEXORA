package com.nexora.android.engine.profile

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Deduce las pistas de un juego mirando **solo los nombres** de los archivos de
 * su carpeta.
 *
 * No abre, no lee y no ejecuta nada: únicamente enumera el directorio, igual que
 * hace el escáner de la Fase 2, y busca las librerías que delatan la API gráfica
 * (`d3d9.dll`, `d3d11.dll`, `vulkan-1.dll`...). Es la única forma de saber algo
 * del juego sin interpretar un binario de Windows, cosa que NEXORA no hace.
 *
 * La enumeración se hace en `Dispatchers.IO` y está envuelta en `runCatching`:
 * si el almacenamiento no es accesible en ese momento, el resultado es
 * "no inspeccionado", que el AutoOptimizer trata como ausencia de dato, no como
 * un juego sin API gráfica.
 */
class GameProfileInspector(
    private val context: Context,
) {

    /**
     * Inspecciona la carpeta de un juego.
     *
     * @param title Título del juego, para deducir el género.
     * @param installDirectory Ruta del sistema de archivos de la carpeta.
     * @param sizeBytes Tamaño del ejecutable.
     * @param documentUri URI del ejecutable cuando se detectó por el Storage
     *   Access Framework; se usa como alternativa si no hay ruta accesible.
     */
    suspend fun inspect(
        title: String,
        installDirectory: String?,
        sizeBytes: Long,
        documentUri: String?,
    ): GameProfileHints = withContext(Dispatchers.IO) {
        val fileNames = runCatching {
            siblingNames(installDirectory = installDirectory, documentUri = documentUri)
        }.getOrDefault(emptyList())

        if (fileNames.isEmpty()) {
            GameProfileHints.fromTitleOnly(title, installDirectory, sizeBytes)
        } else {
            GameProfileHints.fromFiles(title, installDirectory, sizeBytes, fileNames)
        }
    }

    /** Nombres de archivo de la carpeta del juego, o lista vacía si no hay acceso. */
    private fun siblingNames(installDirectory: String?, documentUri: String?): List<String> {
        fromFileSystem(installDirectory)?.let { return it }
        return fromDocumentUri(documentUri)
    }

    private fun fromFileSystem(installDirectory: String?): List<String>? {
        if (installDirectory.isNullOrBlank()) return null
        val directory = File(installDirectory)
        if (!directory.isDirectory) return null

        val names = mutableListOf<String>()
        val children = directory.listFiles() ?: return null

        for (child in children) {
            if (names.size >= ENTRY_BUDGET) break
            if (child.isFile) {
                names += child.name
                continue
            }
            if (child.name.lowercase() !in BINARY_SUBFOLDERS) continue

            val grandChildren = child.listFiles() ?: continue
            for (grandChild in grandChildren) {
                if (names.size >= ENTRY_BUDGET) break
                if (grandChild.isFile) names += grandChild.name
            }
        }
        return names.takeIf { it.isNotEmpty() }
    }

    /**
     * Vía Storage Access Framework: el documento del ejecutable no siempre
     * expone su carpeta padre, así que un fallo aquí es un resultado válido.
     */
    private fun fromDocumentUri(documentUri: String?): List<String> {
        if (documentUri.isNullOrBlank()) return emptyList()
        val uri = Uri.parse(documentUri)
        val parent = DocumentFile.fromSingleUri(context, uri)?.parentFile ?: return emptyList()

        val names = mutableListOf<String>()
        for (child in parent.listFiles()) {
            if (names.size >= ENTRY_BUDGET) break
            if (child.isFile) names += child.name.orEmpty()
        }
        return names
    }

    private companion object {

        /** Tope de nombres recogidos: sobra para encontrar las librerías. */
        const val ENTRY_BUDGET = 400

        /** Subcarpetas donde los motores guardan los binarios del juego. */
        val BINARY_SUBFOLDERS = setOf("bin", "bin64", "bin32", "binaries", "system")
    }
}
