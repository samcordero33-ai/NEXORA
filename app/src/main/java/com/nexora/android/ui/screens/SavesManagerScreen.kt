package com.nexora.android.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexora.android.R
import com.nexora.android.runtime.saves.BackupNaming
import com.nexora.android.runtime.saves.SaveBackup
import com.nexora.android.runtime.saves.SaveFolder
import com.nexora.android.ui.components.MessageCard
import com.nexora.android.ui.components.NexoraCard
import com.nexora.android.ui.components.SectionHeader
import com.nexora.android.ui.saves.SavesViewModel
import com.nexora.android.ui.theme.NexoraAccent
import com.nexora.android.ui.theme.NexoraBackground
import com.nexora.android.ui.theme.NexoraBorder
import com.nexora.android.ui.theme.NexoraDanger
import com.nexora.android.ui.theme.NexoraSurfaceElevated
import com.nexora.android.ui.theme.NexoraTextPrimary
import com.nexora.android.ui.theme.NexoraTextSecondary
import com.nexora.android.ui.theme.NexoraWarning

/**
 * Ruta del gestor de partidas (Fase 9).
 *
 * Aquí viven los dos selectores del sistema: crear el ZIP de exportación
 * (`CreateDocument`) e importar uno existente (`OpenDocument`). La pantalla en
 * sí es pura y recibe callbacks, así que se puede previsualizar sin Android.
 */
@Composable
fun SavesManagerRoute(
    gameId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SavesViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(gameId) { viewModel.load(gameId) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(viewModel::exportTo) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importFrom) }

    var pendingRestore by remember { mutableStateOf<SaveBackup?>(null) }
    var pendingDelete by remember { mutableStateOf<SaveBackup?>(null) }

    SavesManagerScreen(
        gameName = state.gameName,
        totalBytes = state.totalBytes,
        folders = state.folders,
        backups = state.backups,
        backupBytes = state.backupBytes,
        busy = state.busy,
        infoMessage = state.infoMessage,
        errorMessage = state.errorMessage,
        onBack = onBack,
        onCreateBackup = viewModel::createBackup,
        onRestoreRequest = { pendingRestore = it },
        onDeleteRequest = { pendingDelete = it },
        onShare = { backup -> viewModel.shareIntent(backup)?.let(context::startActivity) },
        onExport = { exportLauncher.launch(defaultExportName(state.gameName)) },
        onImport = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
        onCleanShaders = viewModel::cleanShaderCache,
        onCleanTemp = viewModel::cleanTemporary,
        onDismissMessages = viewModel::dismissMessages,
        modifier = modifier,
    )

    pendingRestore?.let { backup ->
        ConfirmDialog(
            title = "Restaurar respaldo",
            body = "Se copiarán los archivos de «${backup.label}» sobre el prefijo del juego. " +
                "Antes se crea un respaldo automático de lo que hay ahora.",
            confirmLabel = "Restaurar",
            onConfirm = {
                viewModel.restore(backup)
                pendingRestore = null
            },
            onDismiss = { pendingRestore = null },
        )
    }

    pendingDelete?.let { backup ->
        ConfirmDialog(
            title = "Borrar respaldo",
            body = "Se eliminará «${backup.label}» del almacenamiento de NEXORA. " +
                "Las partidas del juego no se tocan.",
            confirmLabel = "Borrar",
            destructive = true,
            onConfirm = {
                viewModel.deleteBackup(backup)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun SavesManagerScreen(
    gameName: String,
    totalBytes: Long,
    folders: List<SaveFolder>,
    backups: List<SaveBackup>,
    backupBytes: Long,
    busy: Boolean,
    infoMessage: String?,
    errorMessage: String?,
    onBack: () -> Unit,
    onCreateBackup: () -> Unit,
    onRestoreRequest: (SaveBackup) -> Unit,
    onDeleteRequest: (SaveBackup) -> Unit,
    onShare: (SaveBackup) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onCleanShaders: () -> Unit,
    onCleanTemp: () -> Unit,
    onDismissMessages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = NexoraBackground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                }
                Spacer(modifier = Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.saves_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = NexoraTextPrimary,
                    )
                    Text(
                        text = gameName,
                        style = MaterialTheme.typography.bodySmall,
                        color = NexoraTextSecondary,
                    )
                }
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = NexoraAccent,
                        strokeWidth = 2.dp,
                    )
                }
            }

            if (infoMessage != null || errorMessage != null) {
                MessageCard(
                    message = infoMessage ?: errorMessage.orEmpty(),
                    isError = errorMessage != null,
                    onDismiss = onDismissMessages,
                )
            }

            SectionHeader(title = "Carpetas de partidas detectadas")
            NexoraCard {
                if (folders.isEmpty()) {
                    Text(
                        text = "No se ha encontrado ninguna carpeta con partidas dentro del " +
                            "prefijo. Aparecerán aquí en cuanto el juego guarde por primera vez.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NexoraTextSecondary,
                    )
                } else {
                    folders.forEachIndexed { index, folder ->
                        if (index > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(NexoraBorder),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        SaveFolderRow(folder)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onCreateBackup,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NexoraAccent,
                        contentColor = NexoraBackground,
                    ),
                ) {
                    Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Crear respaldo")
                }
                OutlinedButton(onClick = onImport, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Importar ZIP")
                }
            }

            SectionHeader(title = "Respaldos (${backups.size})")
            NexoraCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Prefijo: ${formatBytes(totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = NexoraTextSecondary,
                    )
                    Text(
                        text = "Respaldos: ${formatBytes(backupBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = NexoraTextSecondary,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (backups.isEmpty()) {
                    Text(
                        text = "Todavía no hay respaldos de este juego.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NexoraTextSecondary,
                    )
                } else {
                    backups.forEach { backup ->
                        BackupRow(
                            backup = backup,
                            enabled = !busy,
                            onRestore = { onRestoreRequest(backup) },
                            onShare = { onShare(backup) },
                            onDelete = { onDeleteRequest(backup) },
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onExport,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Guardar copia en el dispositivo")
            }

            SectionHeader(title = stringResource(R.string.saves_cache_title))
            NexoraCard {
                Text(
                    text = stringResource(R.string.saves_cache_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NexoraTextSecondary,
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(onClick = onCleanShaders, enabled = !busy) {
                    Icon(Icons.Outlined.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.saves_clean_shaders))
                }
            }

            SectionHeader(title = stringResource(R.string.saves_temp_title))
            NexoraCard {
                Text(
                    text = stringResource(R.string.saves_temp_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NexoraTextSecondary,
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(onClick = onCleanTemp, enabled = !busy) {
                    Icon(
                        Icons.Outlined.CleaningServices,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.saves_clean_temp))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SaveFolderRow(folder: SaveFolder) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Outlined.Folder,
            contentDescription = null,
            tint = if (folder.matchesGame) NexoraAccent else NexoraTextSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.label,
                style = MaterialTheme.typography.bodyMedium,
                color = NexoraTextPrimary,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "${folder.fileCount} archivos · ${formatBytes(folder.bytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = NexoraTextSecondary,
            )
        }
        if (folder.matchesGame) {
            Text(
                text = "COINCIDE",
                style = MaterialTheme.typography.labelSmall,
                color = NexoraAccent,
            )
        }
    }
}

@Composable
private fun BackupRow(
    backup: SaveBackup,
    enabled: Boolean,
    onRestore: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = BackupNaming.label(backup.stamp),
                style = MaterialTheme.typography.bodyMedium,
                color = NexoraTextPrimary,
            )
            Text(
                text = formatBytes(backup.bytes),
                style = MaterialTheme.typography.bodySmall,
                color = NexoraTextSecondary,
            )
        }
        IconButton(onClick = onRestore, enabled = enabled, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Outlined.Restore,
                contentDescription = "Restaurar",
                tint = NexoraAccent,
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onShare, enabled = enabled, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = "Compartir",
                tint = NexoraTextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onDelete, enabled = enabled, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Borrar",
                tint = NexoraWarning,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = NexoraTextPrimary) },
        text = { Text(body, color = NexoraTextSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) NexoraDanger else NexoraAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = NexoraTextSecondary)
            }
        },
        containerColor = NexoraSurfaceElevated,
    )
}

/** Nombre sugerido para el ZIP exportado. */
private fun defaultExportName(gameName: String): String {
    val safe = gameName.map { if (it.isLetterOrDigit()) it else '-' }
        .joinToString("")
        .trim('-')
        .ifEmpty { "nexora" }
    return "nexora-saves-$safe.zip"
}

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
