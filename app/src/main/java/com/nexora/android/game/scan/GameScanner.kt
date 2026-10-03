package com.nexora.android.game.scan

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.nexora.android.game.model.Game
import com.nexora.android.game.model.GameSource
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlin.coroutines.coroutineContext

/**
 * Escáner de almacenamiento de NEXORA.
 *
 * Recorre directorios en busca de ejecutables de Windows (`.exe`) y devuelve los
 * que parecen el ejecutable principal de un juego. **No ejecuta, no abre ni
 * analiza ningún binario de Windows**: el único criterio es el nombre, la ruta y
 * el tamaño del archivo.
 *
 * Dos formas de acceso, deliberadamente separadas:
 *
 * 1. [scanTree] sobre un `Uri` de árbol del Storage Access Framework. No
 *    requiere ningún permiso y es la vía recomendada: el usuario elige la
 *    carpeta y concede acceso persistente solo a esa carpeta.
 * 2. [scanTargets] sobre rutas del sistema de archivos. Solo es útil si el
 *    dispositivo concede acceso amplio al almacenamiento
 *    (`MANAGE_EXTERNAL_STORAGE` en Android 11+), porque desde el almacenamiento
 *    con ámbito (Scoped Storage) el resto de rutas no son legibles.
 *
 * El escaneo es una función suspendida cancellable: se comprueba la cancelación
 * en cada entrada visitada, así que abandonar la pantalla lo detiene.
 */
class GameScanner(
    private val context: Context,
    private val config: ScanConfig = ScanConfig.DEFAULT,
) {

    /**
     * Escanea el árbol de carpetas concedido mediante el Storage Access
     * Framework.
     *
     * @param treeUri `Uri` devuelto por `ACTION_OPEN_DOCUMENT_TREE`.
     * @param onProgress Se invoca periódicamente; es suspendida para poder
     *   emitir progreso sin bloquear la corrutina de escaneo.
     * @throws IllegalArgumentException si el sistema no resuelve el árbol.
     */
    suspend fun scanTree(
        treeUri: Uri,
        onProgress: suspend (ScanProgress) -> Unit,
    ): ScanOutcome {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalArgumentException(
                "El sistema no devolvió un árbol de directorios válido para $treeUri",
            )

        val rootName = root.name ?: treeUri.lastPathSegment ?: treeUri.toString()
        val session = ScanSession(config, onProgress)
        session.report(rootName)
        walkDocumentTree(document = root, depth = 0, path = rootName, session = session)
        return session.buildOutcome(targets = listOf(rootName))
    }

    /**
     * Escanea directorios del sistema de archivos.
     *
     * Los directorios que no se puedan leer se registran en
     * [ScanOutcome.errors] en vez de abortar el escaneo: es lo esperado cuando
     * falta el permiso de acceso amplio.
     */
    suspend fun scanTargets(
        targets: List<ScanTarget>,
        onProgress: suspend (ScanProgress) -> Unit,
    ): ScanOutcome {
        val session = ScanSession(config, onProgress)
        for (target in targets) {
            session.report(target.directory.absolutePath)
            walkDirectory(
                directory = target.directory,
                depth = 0,
                path = target.directory.absolutePath,
                session = session,
            )
            if (session.isExhausted) break
        }
        return session.buildOutcome(targets = targets.map { it.directory.absolutePath })
    }

    private suspend fun walkDocumentTree(
        document: DocumentFile,
        depth: Int,
        path: String,
        session: ScanSession,
    ) {
        if (session.isExhausted || depth > config.maxDepth) return

        val children = try {
            document.listFiles()
        } catch (error: Exception) {
            session.noteError("Sin acceso de lectura en $path")
            return
        }

        for (child in children) {
            if (session.isExhausted) break
            coroutineContext.ensureActive()

            val name = child.name ?: continue
            val childPath = if (path.isEmpty()) name else "$path/$name"

            when {
                child.isDirectory -> {
                    if (ExecutableFilter.isIgnoredDirectory(name)) continue
                    session.noteDirectory()
                    session.report(childPath)
                    walkDocumentTree(child, depth + 1, childPath, session)
                }

                child.isFile && !child.isVirtual -> {
                    session.noteFile()
                    if (!ExecutableFilter.isExecutable(name)) continue
                    if (config.filterNoise && ExecutableFilter.isIgnoredExecutable(name)) {
                        session.noteDismissed()
                        continue
                    }
                    session.add(
                        Candidate(
                            name = name,
                            parentPath = path,
                            sizeBytes = runCatching { child.length() }.getOrDefault(0L),
                            path = childPath,
                            documentUri = child.uri.toString(),
                        ),
                    )
                }
            }
        }
    }

    private suspend fun walkDirectory(
        directory: File,
        depth: Int,
        path: String,
        session: ScanSession,
    ) {
        if (session.isExhausted || depth > config.maxDepth) return
        if (isSymbolicLink(directory)) return

        val children = try {
            directory.listFiles()
        } catch (error: SecurityException) {
            session.noteError("Sin acceso de lectura en $path")
            null
        }

        if (children == null) {
            session.noteError("No se pudo leer $path")
            return
        }

        for (child in children) {
            if (session.isExhausted) break
            coroutineContext.ensureActive()

            val name = child.name
            val childPath = "$path/$name"

            when {
                child.isDirectory -> {
                    if (ExecutableFilter.isIgnoredDirectory(name)) continue
                    session.noteDirectory()
                    session.report(childPath)
                    walkDirectory(child, depth + 1, childPath, session)
                }

                child.isFile -> {
                    session.noteFile()
                    if (!ExecutableFilter.isExecutable(name)) continue
                    if (config.filterNoise && ExecutableFilter.isIgnoredExecutable(name)) {
                        session.noteDismissed()
                        continue
                    }
                    session.add(
                        Candidate(
                            name = name,
                            parentPath = path,
                            sizeBytes = child.length(),
                            path = childPath,
                            documentUri = null,
                        ),
                    )
                }
            }
        }
    }

    /**
     * `true` si la ruta es un enlace simbólico.
     *
     * Se descartan para que un enlace que apunte a un directorio padre no
     * provoque un recorrido infinito.
     */
    private fun isSymbolicLink(file: File): Boolean = runCatching {
        Files.isSymbolicLink(file.toPath())
    }.getOrDefault(false)

    /** Ejecutable candidato recogido durante el recorrido. */
    private data class Candidate(
        val name: String,
        val parentPath: String,
        val sizeBytes: Long,
        val path: String,
        val documentUri: String?,
    )

    /**
     * Estado mutable de un escaneo: contadores, candidatos y presupuesto.
     *
     * Existe como clase aparte para que los dos recorridos (SAF y sistema de
     * archivos) compartan exactamente la misma contabilidad y el mismo criterio
     * de selección final.
     */
    private class ScanSession(
        private val config: ScanConfig,
        private val onProgress: suspend (ScanProgress) -> Unit,
    ) {
        private val candidates = mutableListOf<Candidate>()
        private val errors = mutableListOf<String>()

        private var directoriesScanned = 0L
        private var filesScanned = 0L
        private var executablesDismissed = 0L
        private var visits = 0
        private var visitsSinceReport = 0
        private var truncated = false
        private var currentPath = ""

        /** `true` cuando se agotó el presupuesto de entradas visitadas. */
        val isExhausted: Boolean get() = truncated

        suspend fun report(path: String) {
            currentPath = path
            visits++
            if (visits >= config.entryBudget) truncated = true
            visitsSinceReport++
            if (truncated || visitsSinceReport >= config.progressInterval) {
                visitsSinceReport = 0
                onProgress(
                    ScanProgress(
                        directoriesScanned = directoriesScanned,
                        filesScanned = filesScanned,
                        executablesFound = candidates.size.toLong(),
                        currentPath = currentPath,
                        budget = config.entryBudget,
                        exhausted = truncated,
                    ),
                )
            }
        }

        fun noteDirectory() {
            directoriesScanned++
        }

        fun noteFile() {
            filesScanned++
        }

        fun noteDismissed() {
            executablesDismissed++
        }

        fun noteError(message: String) {
            if (errors.size < MAX_REPORTED_ERRORS) errors.add(message)
        }

        fun add(candidate: Candidate) {
            candidates += candidate
        }

        /**
         * Selecciona y ordena el resultado final.
         *
         * Los candidatos se agrupan por carpeta de instalación y de cada grupo
         * se conserva el mejor puntuado: así un juego acompañado de su launcher
         * aparece una sola vez en la biblioteca.
         */
        fun buildOutcome(targets: List<String>): ScanOutcome {
            val games = candidates
                .groupBy { it.parentPath }
                .values
                .flatMap { group ->
                    group
                        .sortedByDescending { score(it) }
                        .take(config.maxGamesPerFolder)
                }
                .sortedByDescending { score(it) }
                .take(config.maxResults)
                .map { it.toGame() }

            return ScanOutcome(
                games = games,
                directoriesScanned = directoriesScanned,
                filesScanned = filesScanned,
                executablesDismissed = executablesDismissed,
                truncated = truncated,
                targets = targets,
                errors = errors,
            )
        }

        private fun score(candidate: Candidate): Int = ExecutableFilter.score(
            fileName = candidate.name,
            parentDirectoryName = candidate.parentPath.substringAfterLast('/'),
            sizeBytes = candidate.sizeBytes,
        )

        private fun Candidate.toGame(): Game = Game(
            id = stableId(path),
            name = ExecutableFilter.deriveTitle(name),
            executablePath = path,
            installDirectory = parentPath,
            addedAtMillis = System.currentTimeMillis(),
            source = GameSource.SCAN,
            sizeBytes = sizeBytes,
            documentUri = documentUri,
        )

        /**
         * Identificador estable derivado de la ruta.
         *
         * Derivarlo de la ruta (y no de un contador) hace que volver a escanear
         * la misma carpeta no duplique entradas en la biblioteca.
         */
        private fun stableId(path: String): String =
            "scan-${UUID.nameUUIDFromBytes(path.toByteArray(Charsets.UTF_8))}"

        private companion object {
            const val MAX_REPORTED_ERRORS = 8
        }
    }
}
