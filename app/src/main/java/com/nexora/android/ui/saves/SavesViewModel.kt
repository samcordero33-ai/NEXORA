package com.nexora.android.ui.saves

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.android.game.data.local.NexoraDatabase
import com.nexora.android.game.model.Game
import com.nexora.android.game.repository.GameLibraryRepository
import com.nexora.android.runtime.diagnostics.DiagnosticsLevel
import com.nexora.android.runtime.diagnostics.DiagnosticsLogger
import com.nexora.android.runtime.env.WinePrefix
import com.nexora.android.runtime.saves.SaveBackup
import com.nexora.android.runtime.saves.SaveBackupStore
import com.nexora.android.runtime.saves.SaveFolder
import com.nexora.android.runtime.saves.SaveImporter
import com.nexora.android.runtime.saves.SaveLocationScanner
import com.nexora.android.runtime.saves.ShaderCacheCleaner
import com.nexora.android.runtime.saves.TempFileCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Estado del gestor de partidas guardadas (Fase 9).
 *
 * @property game Juego al que pertenecen las operaciones.
 * @property totalBytes Tamaño del prefijo completo.
 * @property folders Carpetas del prefijo que parecen contener partidas.
 * @property backups Respaldos existentes, del más reciente al más antiguo.
 * @property busy `true` mientras hay una operación de disco en curso.
 * @property exportFile Último ZIP generado, para compartirlo.
 */
data class SavesUiState(
    val game: Game? = null,
    val totalBytes: Long = 0L,
    val folders: List<SaveFolder> = emptyList(),
    val backups: List<SaveBackup> = emptyList(),
    val busy: Boolean = false,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
    val exportFile: File? = null,
) {
    val gameName: String get() = game?.name.orEmpty()
    val backupBytes: Long get() = backups.sumOf { it.bytes }
}

/**
 * ViewModel del gestor de partidas y caché.
 *
 * Escanea el prefijo del juego, crea respaldos ZIP, los restaura y los
 * exporta al almacenamiento del dispositivo mediante el Storage Access
 * Framework (el usuario elige dónde, sin permisos de almacenamiento).
 *
 * **Antes de restaurar se hace un respaldo automático** de lo que hay: si el
 * ZIP elegido es de otra partida, el usuario puede volver atrás. Restaurar sin
 * red de seguridad es la forma más rápida de perder 40 horas de juego.
 */
class SavesViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val database = NexoraDatabase.getInstance(application)
    private val gameRepository = GameLibraryRepository(database.gameDao())
    private val diagnostics = DiagnosticsLogger.shared

    private val store = SaveBackupStore(
        rootDir = File(application.filesDir, "save_backups"),
    )

    private val mutableState = MutableStateFlow(SavesUiState())
    val state: StateFlow<SavesUiState> = mutableState.asStateFlow()

    private var gameId: String = ""

    /** Carga el juego, mide el prefijo y escanea las carpetas de partidas. */
    fun load(gameId: String) {
        this.gameId = gameId
        viewModelScope.launch {
            val game = gameRepository.gameById(gameId)
            mutableState.update { it.copy(game = game) }
            rescan()
        }
    }

    /** Vuelve a medir el prefijo y a listar carpetas y respaldos. */
    fun rescan() {
        if (gameId.isEmpty()) return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true) }
            val snapshot = withContext(Dispatchers.IO) {
                val prefix = WinePrefix.underFilesDir(
                    filesDir = getApplication<Application>().filesDir,
                    gameId = gameId,
                )
                Snapshot(
                    totalBytes = if (prefix.exists()) dirSize(prefix.path) else 0L,
                    folders = SaveLocationScanner.scan(
                        driveC = prefix.driveC,
                        gameName = mutableState.value.game?.name,
                    ),
                    backups = store.list(gameId),
                )
            }
            mutableState.update {
                it.copy(
                    busy = false,
                    totalBytes = snapshot.totalBytes,
                    folders = snapshot.folders,
                    backups = snapshot.backups,
                )
            }
        }
    }

    /** Crea un respaldo de `drive_c` (partidas + perfil de usuario). */
    fun createBackup() {
        if (gameId.isEmpty()) return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, errorMessage = null) }
            try {
                val backup = withContext(Dispatchers.IO) {
                    val prefix = WinePrefix.underFilesDir(
                        filesDir = getApplication<Application>().filesDir,
                        gameId = gameId,
                    )
                    prefix.createIfMissing()
                    store.create(gameId = gameId, sourceRoot = prefix.driveC)
                }
                diagnostics.record(
                    DiagnosticsLevel.INFO,
                    "saves",
                    "Respaldo creado: ${backup.name} (${backup.bytes} B)",
                )
                mutableState.update {
                    it.copy(
                        busy = false,
                        infoMessage = "Respaldo creado (${formatBytes(backup.bytes)})",
                    )
                }
                rescan()
            } catch (error: Exception) {
                fail("No se pudo crear el respaldo", error)
            }
        }
    }

    /**
     * Restaura un respaldo sobre `drive_c`.
     *
     * Secuencia: respaldo de seguridad → extracción → reescaneo. El respaldo
     * de seguridad se conserva aunque la extracción falle, porque en ese caso
     * el prefijo puede haber quedado a medias.
     */
    fun restore(backup: SaveBackup) {
        if (gameId.isEmpty()) return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, errorMessage = null) }
            try {
                val summary = withContext(Dispatchers.IO) {
                    val prefix = WinePrefix.underFilesDir(
                        filesDir = getApplication<Application>().filesDir,
                        gameId = gameId,
                    )
                    prefix.createIfMissing()
                    // Red de seguridad: si el ZIP es de otra partida, esto es
                    // lo que permite volver atrás.
                    runCatching { store.create(gameId = gameId, sourceRoot = prefix.driveC) }
                    SaveImporter.extract(zipFile = backup.file, targetDir = prefix.driveC)
                }
                diagnostics.record(
                    DiagnosticsLevel.INFO,
                    "saves",
                    "Restaurado ${backup.name}: ${summary.entriesExtracted} entradas",
                )
                val rejected = if (summary.rejectedEntries.isNotEmpty()) {
                    " · ${summary.rejectedEntries.size} rechazadas"
                } else {
                    ""
                }
                mutableState.update {
                    it.copy(
                        busy = false,
                        infoMessage = "Restaurado ${backup.label}: " +
                            "${summary.entriesExtracted} archivos$rejected",
                    )
                }
                rescan()
            } catch (error: Exception) {
                fail("No se pudo restaurar el respaldo", error)
            }
        }
    }

    /** Borra un respaldo del almacén interno. */
    fun deleteBackup(backup: SaveBackup) {
        viewModelScope.launch {
            val removed = withContext(Dispatchers.IO) { store.delete(backup) }
            diagnostics.record(
                if (removed) DiagnosticsLevel.INFO else DiagnosticsLevel.WARN,
                "saves",
                "Borrado de respaldo ${backup.name}: $removed",
            )
            mutableState.update {
                it.copy(
                    infoMessage = if (removed) "Respaldo borrado" else null,
                    errorMessage = if (removed) null else "No se pudo borrar el respaldo",
                )
            }
            rescan()
        }
    }

    /**
     * Copia el respaldo más reciente al destino elegido por el usuario.
     *
     * Si aún no hay ninguno, se crea uno al vuelo: exportar "nada" no tiene
     * sentido, y obligar a dos pulsaciones para lo obvio es peor.
     */
    fun exportTo(uri: Uri) {
        if (gameId.isEmpty()) return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, errorMessage = null) }
            try {
                val backup = withContext(Dispatchers.IO) {
                    val existing = store.list(gameId).firstOrNull()
                    val target = existing ?: run {
                        val prefix = WinePrefix.underFilesDir(
                            filesDir = getApplication<Application>().filesDir,
                            gameId = gameId,
                        )
                        prefix.createIfMissing()
                        store.create(gameId = gameId, sourceRoot = prefix.driveC)
                    }
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openOutputStream(uri)?.use { output ->
                        target.file.inputStream().use { input -> input.copyTo(output) }
                    } ?: throw IllegalStateException("El destino no admite escritura")
                    target
                }
                diagnostics.record(
                    DiagnosticsLevel.INFO,
                    "saves",
                    "Exportado ${backup.name} a $uri",
                )
                mutableState.update {
                    it.copy(
                        busy = false,
                        infoMessage = "Exportado a la ubicación elegida",
                        exportFile = backup.file,
                    )
                }
            } catch (error: Exception) {
                fail("No se pudo exportar", error)
            }
        }
    }

    /** Importa un ZIP elegido con el selector de documentos. */
    fun importFrom(uri: Uri) {
        if (gameId.isEmpty()) return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, errorMessage = null) }
            try {
                val summary = withContext(Dispatchers.IO) {
                    val app = getApplication<Application>()
                    val incoming = File(app.cacheDir, "import-${System.currentTimeMillis()}.zip")
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        incoming.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IllegalStateException("No se pudo leer el archivo elegido")

                    val prefix = WinePrefix.underFilesDir(
                        filesDir = app.filesDir,
                        gameId = gameId,
                    )
                    prefix.createIfMissing()
                    runCatching { store.create(gameId = gameId, sourceRoot = prefix.driveC) }
                    val result = SaveImporter.extract(zipFile = incoming, targetDir = prefix.driveC)
                    incoming.delete()
                    result
                }
                diagnostics.record(
                    DiagnosticsLevel.INFO,
                    "saves",
                    "Importadas ${summary.entriesExtracted} entradas (${summary.totalBytes} B)",
                )
                val rejected = if (summary.rejectedEntries.isNotEmpty()) {
                    " (${summary.rejectedEntries.size} rechazadas por seguridad)"
                } else {
                    ""
                }
                mutableState.update {
                    it.copy(
                        busy = false,
                        infoMessage = "Importadas ${summary.entriesExtracted} entradas$rejected",
                    )
                }
                rescan()
            } catch (error: Exception) {
                fail("No se pudo importar el ZIP", error)
            }
        }
    }

    /** Intent para compartir un respaldo con otra app. */
    fun shareIntent(backup: SaveBackup): Intent? = runCatching {
        val app = getApplication<Application>()
        val uri = FileProvider.getUriForFile(
            app,
            app.packageName + ".fileprovider",
            backup.file,
        )
        Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, backup.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }.getOrNull()

    /** Limpia la caché de shaders DXVK del juego. */
    fun cleanShaderCache() {
        if (gameId.isEmpty()) return
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val prefix = WinePrefix.underFilesDir(
                        filesDir = getApplication<Application>().filesDir,
                        gameId = gameId,
                    ).path
                    ShaderCacheCleaner.clean(prefix)
                }
                diagnostics.record(
                    DiagnosticsLevel.INFO,
                    "saves",
                    "Caché DXVK: ${result.deletedFiles} archivos, ${result.freedBytes} B liberados",
                )
                mutableState.update {
                    it.copy(
                        infoMessage = "Caché DXVK: ${result.deletedFiles} archivos borrados",
                        errorMessage = null,
                    )
                }
                rescan()
            } catch (error: Exception) {
                fail("No se pudo limpiar la caché", error)
            }
        }
    }

    /** Limpia temporales de los prefijos. */
    fun cleanTemporary(deep: Boolean = false) {
        viewModelScope.launch {
            try {
                val filesDir = getApplication<Application>().filesDir
                val targets = listOf(
                    File(filesDir, "cache/nexora-tmp"),
                    File(filesDir, "prefixes"),
                )
                val result = withContext(Dispatchers.IO) {
                    TempFileCleaner.clean(targets, deep)
                }
                diagnostics.record(
                    DiagnosticsLevel.INFO,
                    "saves",
                    "Temporales: ${result.deletedFiles} archivos, ${result.freedBytes} B",
                )
                mutableState.update {
                    it.copy(
                        infoMessage = "Temporales: ${result.deletedFiles} archivos borrados",
                        errorMessage = null,
                    )
                }
                rescan()
            } catch (error: Exception) {
                fail("No se pudieron limpiar los temporales", error)
            }
        }
    }

    /** Descarta mensajes. */
    fun dismissMessages() {
        mutableState.update { it.copy(infoMessage = null, errorMessage = null) }
    }

    private fun fail(prefix: String, error: Exception) {
        diagnostics.record(DiagnosticsLevel.ERROR, "saves", "$prefix: ${error.message}")
        mutableState.update {
            it.copy(
                busy = false,
                errorMessage = "$prefix: ${error.message ?: error.javaClass.simpleName}",
            )
        }
    }

    private fun dirSize(path: File): Long {
        if (!path.exists()) return 0L
        var total = 0L
        for (file in path.walkTopDown()) if (file.isFile) total += file.length()
        return total
    }

    private data class Snapshot(
        val totalBytes: Long,
        val folders: List<SaveFolder>,
        val backups: List<SaveBackup>,
    )

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index += 1
        }
        return "%.2f %s".format(value, units[index])
    }
}
