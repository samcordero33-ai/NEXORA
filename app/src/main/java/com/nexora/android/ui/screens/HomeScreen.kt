package com.nexora.android.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexora.android.R
import com.nexora.android.engine.DeviceInfo
import com.nexora.android.engine.DeviceInfoProvider
import com.nexora.android.ui.components.GameListItem
import com.nexora.android.ui.components.NexoraCard
import com.nexora.android.ui.components.ScanProgressCard
import com.nexora.android.ui.components.SectionHeader
import com.nexora.android.ui.components.StorageAccessCard
import com.nexora.android.ui.scanner.GameScannerViewModel
import com.nexora.android.ui.scanner.ScannerUiState
import com.nexora.android.ui.theme.NexoraAccent
import com.nexora.android.ui.theme.NexoraAccentMuted
import com.nexora.android.ui.theme.NexoraBackground
import com.nexora.android.ui.theme.NexoraSubtitleTextStyle
import com.nexora.android.ui.theme.NexoraTextPrimary
import com.nexora.android.ui.theme.NexoraTextSecondary
import com.nexora.android.ui.theme.NexoraTheme
import com.nexora.android.ui.theme.NexoraWarning

private const val BYTES_PER_GIGABYTE = 1024.0 * 1024.0 * 1024.0

/**
 * Ruta de la pantalla principal: une el ViewModel con la pantalla pura.
 *
 * Aquí viven los efectos que sí necesitan un `Context` o un ciclo de vida: el
 * selector de carpetas del sistema, la petición del permiso clásico de lectura y
 * la consulta de los datos del dispositivo. [NexoraHomeScreen] se queda sin
 * dependencias de Android, así que se puede previsualizar.
 */
@Composable
fun NexoraHomeRoute(
    modifier: Modifier = Modifier,
    viewModel: GameScannerViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val deviceInfo by produceState<DeviceInfo?>(initialValue = null) {
        value = DeviceInfoProvider(context).getDeviceInfo()
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri != null) viewModel.onFolderSelected(treeUri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refreshStorageAccess() }

    LaunchedEffect(uiState.storageAccess.needsLegacyReadPermission) {
        if (uiState.storageAccess.needsLegacyReadPermission) {
            val pending = viewModel.legacyReadPermissions()
            if (pending.isNotEmpty()) permissionLauncher.launch(pending)
        }
    }

    // El acceso amplio se concede desde Ajustes: al volver hay que releerlo,
    // porque el sistema no notifica el cambio a la aplicación.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshStorageAccess()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    NexoraHomeScreen(
        uiState = uiState,
        deviceInfo = deviceInfo,
        onScanFolder = { folderPicker.launch(null) },
        onFindGames = viewModel::scanCommonDirectories,
        onRemoveGame = viewModel::removeGame,
        onClearLibrary = viewModel::clearLibrary,
        onGrantAllFilesAccess = {
            viewModel.allFilesAccessIntent()?.let { context.startActivity(it) }
        },
        onDismissMessages = viewModel::dismissMessages,
        modifier = modifier,
    )
}

/**
 * Pantalla principal de NEXORA.
 *
 * Compuesta por: encabezado de marca, biblioteca de juegos, acciones de
 * escaneo, estado del almacenamiento e información del dispositivo.
 */
@Composable
fun NexoraHomeScreen(
    uiState: ScannerUiState,
    deviceInfo: DeviceInfo?,
    onScanFolder: () -> Unit,
    onFindGames: () -> Unit,
    onRemoveGame: (String) -> Unit,
    onClearLibrary: () -> Unit,
    onGrantAllFilesAccess: () -> Unit,
    onDismissMessages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = NexoraBackground,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { NexoraHeader() }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(title = stringResource(R.string.section_my_games))
            }

            if (uiState.games.isEmpty()) {
                item { EmptyGameLibrary() }
            } else {
                item {
                    Text(
                        text = stringResource(R.string.library_count, uiState.games.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = NexoraTextSecondary,
                    )
                }
                items(items = uiState.games, key = { it.id }) { game ->
                    GameListItem(game = game, onRemove = { onRemoveGame(game.id) })
                }
                item {
                    OutlinedButton(
                        onClick = onClearLibrary,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.library_clear),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                ScanActions(
                    isScanning = uiState.isScanning,
                    onScanFolder = onScanFolder,
                    onFindGames = onFindGames,
                )
            }

            if (uiState.isScanning && uiState.progress != null) {
                item { ScanProgressCard(progress = uiState.progress) }
            }

            if (uiState.infoMessage != null || uiState.errorMessage != null) {
                item {
                    ScanMessageCard(
                        message = uiState.infoMessage ?: uiState.errorMessage.orEmpty(),
                        isError = uiState.errorMessage != null,
                        onDismiss = onDismissMessages,
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                SectionHeader(title = stringResource(R.string.section_storage_access))
                StorageAccessCard(
                    state = uiState.storageAccess,
                    onGrantAllFilesAccess = onGrantAllFilesAccess,
                )
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
                SectionHeader(title = stringResource(R.string.section_device_info))
            }

            item {
                if (deviceInfo == null) {
                    DeviceInfoLoading()
                } else {
                    DeviceInfoCard(deviceInfo = deviceInfo)
                }
            }
        }
    }
}

@Composable
private fun NexoraHeader(
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "NEXORA",
            style = MaterialTheme.typography.displayLarge,
            color = NexoraTextPrimary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.app_subtitle),
            style = NexoraSubtitleTextStyle,
            color = NexoraTextSecondary,
        )
    }
}

/**
 * Acciones de descubrimiento de juegos.
 *
 * Son dos caminos deliberadamente distintos:
 * - **Escanear carpeta** abre el selector del sistema y no necesita ningún
 *   permiso: el usuario concede acceso solo a esa carpeta.
 * - **Buscar juegos** recorre las carpetas habituales del dispositivo y de la
 *   microSD, y por eso sí necesita acceso amplio al almacenamiento.
 */
@Composable
private fun ScanActions(
    isScanning: Boolean,
    onScanFolder: () -> Unit,
    onFindGames: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Button(
            onClick = onScanFolder,
            enabled = !isScanning,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NexoraAccent,
                contentColor = NexoraBackground,
                disabledContainerColor = NexoraAccentMuted,
                disabledContentColor = NexoraTextSecondary,
            ),
            contentPadding = PaddingValues(horizontal = 20.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = stringResource(R.string.action_scan_folder),
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onFindGames,
            enabled = !isScanning,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = stringResource(R.string.action_find_games),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * Mensaje de resultado o de error del último escaneo.
 *
 * El resumen dice cuántos ejecutables se descartaron a propósito: sin ese dato,
 * el usuario no sabe si el escáner no los vio o si los filtró.
 */
@Composable
private fun ScanMessageCard(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NexoraCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) NexoraWarning else NexoraTextPrimary,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.size(10.dp))
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                Text(text = "OK", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun EmptyGameLibrary(
    modifier: Modifier = Modifier,
) {
    NexoraCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.SportsEsports,
                contentDescription = null,
                tint = NexoraAccent,
                modifier = Modifier.size(44.dp),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.library_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = NexoraTextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.library_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = NexoraTextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DeviceInfoLoading(
    modifier: Modifier = Modifier,
) {
    NexoraCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.device_info_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = NexoraTextSecondary,
        )
    }
}

@Composable
private fun DeviceInfoCard(
    deviceInfo: DeviceInfo,
    modifier: Modifier = Modifier,
) {
    NexoraCard(modifier = modifier.fillMaxWidth()) {
        DeviceInfoRow(
            label = stringResource(R.string.device_info_device),
            value = "${deviceInfo.manufacturer} ${deviceInfo.model}",
        )
        DeviceInfoRow(
            label = stringResource(R.string.device_info_android),
            value = "${deviceInfo.androidVersion} (API ${deviceInfo.apiLevel})",
        )
        DeviceInfoRow(
            label = stringResource(R.string.device_info_architecture),
            value = deviceInfo.primaryAbi,
        )
        DeviceInfoRow(
            label = stringResource(R.string.device_info_cpu_cores),
            value = deviceInfo.cpuCoreCount.toString(),
        )

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(10.dp))

        DeviceInfoRow(
            label = stringResource(R.string.device_info_total_memory),
            value = formatGigabytes(deviceInfo.totalMemoryBytes),
        )
        DeviceInfoRow(
            label = stringResource(R.string.device_info_available_memory),
            value = formatGigabytes(deviceInfo.availableMemoryBytes),
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Memory,
                contentDescription = null,
                tint = NexoraWarning,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.runtime_not_integrated),
                style = MaterialTheme.typography.bodySmall,
                color = NexoraTextSecondary,
            )
        }
    }
}

@Composable
private fun DeviceInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = NexoraTextSecondary,
        )
        Spacer(modifier = Modifier.size(12.dp))
        Box(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = NexoraTextPrimary,
                textAlign = TextAlign.End,
            )
        }
    }
}

private fun formatGigabytes(bytes: Long): String {
    if (bytes <= 0L) return "-"
    return String.format("%.2f GB", bytes / BYTES_PER_GIGABYTE)
}

@Preview(showBackground = true, widthDp = 420, heightDp = 1100)
@Composable
private fun NexoraHomeScreenPreview() {
    NexoraTheme {
        NexoraHomeScreen(
            uiState = ScannerUiState(),
            deviceInfo = null,
            onScanFolder = {},
            onFindGames = {},
            onRemoveGame = {},
            onClearLibrary = {},
            onGrantAllFilesAccess = {},
            onDismissMessages = {},
        )
    }
}
