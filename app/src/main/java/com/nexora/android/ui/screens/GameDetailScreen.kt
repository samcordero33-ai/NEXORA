package com.nexora.android.ui.screens

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
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexora.android.R
import com.nexora.android.engine.DeviceInfo
import com.nexora.android.engine.GpuVendor
import com.nexora.android.engine.profile.AutoOptimizer
import com.nexora.android.engine.profile.GameGenre
import com.nexora.android.engine.profile.GameProfileHints
import com.nexora.android.engine.profile.GraphicsApi
import com.nexora.android.engine.profile.PerformanceParameters
import com.nexora.android.engine.profile.PerformanceProfile
import com.nexora.android.engine.profile.ProfileRecommendation
import com.nexora.android.game.model.Game
import com.nexora.android.game.model.GameSource
import com.nexora.android.ui.components.GameIconBadge
import com.nexora.android.ui.components.NexoraCard
import com.nexora.android.ui.components.PerformanceProfileCard
import com.nexora.android.ui.components.SectionHeader
import com.nexora.android.ui.theme.NexoraAccent
import com.nexora.android.ui.theme.NexoraBackground
import com.nexora.android.ui.theme.NexoraTextPrimary
import com.nexora.android.ui.theme.NexoraTextSecondary
import com.nexora.android.ui.theme.NexoraTheme
import com.nexora.android.ui.theme.NexoraWarning
import com.nexora.android.ui.util.formatBytes
import com.nexora.android.ui.util.formatDateTime

/**
 * Pantalla de detalle de un juego.
 *
 * Muestra la información del ejecutable tal como la guardó el escáner (ruta,
 * carpeta de instalación, tamaño, origen y fecha de alta), el **perfil de
 * rendimiento** que propone el AutoOptimizer (con su selección manual) y las
 * **opciones iniciales** del juego: jugar, cambiar el icono, copiar la ruta y
 * quitarlo de la biblioteca.
 *
 * @param profileRecommendation Recomendación del AutoOptimizer para este juego;
 *   `null` mientras se calcula.
 * @param customParameters Parámetros derivados para el perfil "Auto
 *   Personalizado" en este dispositivo.
 * @param onSelectProfile Fija el perfil del juego (o `null` para el automático);
 *   la elección se persiste en Room.
 * @param onPlay "Jugar". La fase del runtime aún no está integrada: la acción
 *   avisa al usuario sin lanzar nada.
 * @param onChangeIcon Cambia el icono predeterminado; se persiste en Room.
 * @param onCopyPath Copia la ruta del ejecutable al portapapeles.
 * @param onRemove Quita el juego de la biblioteca. El archivo original no se
 *   toca: NEXORA nunca modifica ni ejecuta binarios de Windows.
 */
@Composable
fun GameDetailScreen(
    game: Game,
    onBack: () -> Unit,
    profileRecommendation: ProfileRecommendation?,
    customParameters: PerformanceParameters?,
    onSelectProfile: (PerformanceProfile?) -> Unit,
    onPlay: () -> Unit,
    onChangeIcon: () -> Unit,
    onCopyPath: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmRemoval by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NexoraBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        DetailTopBar(title = game.name, onBack = onBack)

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GameIconBadge(
                preset = game.iconPreset,
                modifier = Modifier.width(112.dp),
            )
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = NexoraTextPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = executableFileName(game.executablePath),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NexoraTextSecondary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatBytes(game.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = NexoraTextSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionHeader(title = stringResource(R.string.detail_section_executable))
        Spacer(modifier = Modifier.height(10.dp))
        NexoraCard(modifier = Modifier.fillMaxWidth()) {
            DetailRow(
                label = stringResource(R.string.detail_label_file),
                value = executableFileName(game.executablePath),
            )
            DetailRow(
                label = stringResource(R.string.detail_label_folder),
                value = game.installDirectory ?: stringResource(R.string.device_info_unknown),
            )
            DetailRow(
                label = stringResource(R.string.detail_label_size),
                value = stringResource(R.string.detail_size_value, formatBytes(game.sizeBytes)),
            )
            DetailRow(
                label = stringResource(R.string.detail_label_source),
                value = stringResource(sourceLabel(game.source)),
            )
            DetailRow(
                label = stringResource(R.string.detail_label_added),
                value = formatDateTime(game.addedAtMillis),
            )
            DetailRow(
                label = stringResource(R.string.detail_label_access),
                value = stringResource(accessLabel(game.documentUri != null)),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.detail_label_path),
                style = MaterialTheme.typography.bodySmall,
                color = NexoraTextSecondary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = game.executablePath,
                style = MaterialTheme.typography.bodyMedium,
                color = NexoraTextPrimary,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionHeader(title = stringResource(R.string.section_performance))
        Spacer(modifier = Modifier.height(10.dp))
        PerformanceProfileCard(
            recommendation = profileRecommendation,
            selectedProfile = game.profile,
            customParameters = customParameters,
            onSelectProfile = onSelectProfile,
        )

        Spacer(modifier = Modifier.height(20.dp))
        SectionHeader(title = stringResource(R.string.detail_section_options))
        Spacer(modifier = Modifier.height(10.dp))
        NexoraCard(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onPlay,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NexoraAccent,
                    contentColor = NexoraBackground,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = stringResource(R.string.action_play),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onChangeIcon,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Palette,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = stringResource(R.string.detail_action_change_icon),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(game.executablePath))
                    onCopyPath()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = stringResource(R.string.detail_action_copy_path),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            TextButton(
                onClick = { confirmRemoval = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = NexoraWarning,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = stringResource(R.string.detail_action_remove),
                    style = MaterialTheme.typography.labelLarge,
                    color = NexoraWarning,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (confirmRemoval) {
        AlertDialog(
            onDismissRequest = { confirmRemoval = false },
            title = {
                Text(
                    text = stringResource(R.string.detail_remove_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = NexoraTextPrimary,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.detail_remove_body, game.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NexoraTextSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemoval = false
                        onRemove()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.detail_remove_confirm),
                        color = NexoraWarning,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoval = false }) {
                    Text(text = stringResource(R.string.detail_remove_cancel))
                }
            },
        )
    }
}

/**
 * Barra superior del detalle.
 *
 * La supresión es deliberada: la versión de `material-icons-extended` que fija
 * el BOM marca `Icons.Outlined.ArrowBack` como obsoleto apuntando a
 * `Icons.AutoMirrored.Outlined.ArrowBack`, pero **ese objeto no existe** en la
 * versión resuelta. Se queda la variante explícita para compilar sin avisos hasta
 * que se suba el BOM.
 */
@Suppress("DEPRECATION")
@Composable
private fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.detail_action_back),
                tint = NexoraTextPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = stringResource(R.string.detail_title),
            style = MaterialTheme.typography.titleMedium,
            color = NexoraTextPrimary,
        )
        Spacer(modifier = Modifier.size(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = NexoraTextSecondary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
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

private fun executableFileName(path: String): String = path.substringAfterLast('/')

private fun sourceLabel(source: GameSource): Int = when (source) {
    GameSource.SCAN -> R.string.detail_source_scan
    GameSource.MANUAL -> R.string.detail_source_manual
}

private fun accessLabel(usesDocumentUri: Boolean): Int = if (usesDocumentUri) {
    R.string.detail_access_saf
} else {
    R.string.detail_access_path
}

@Preview(showBackground = true, widthDp = 420, heightDp = 1200)
@Composable
private fun GameDetailScreenPreview() {
    val device = DeviceInfo(
        manufacturer = "NEXORA",
        model = "Preview",
        androidVersion = "13",
        apiLevel = 33,
        primaryAbi = "arm64-v8a",
        cpuCoreCount = 8,
        totalMemoryBytes = 6_442_450_944L,
        availableMemoryBytes = 3_221_225_472L,
        gpuVendor = GpuVendor.MALI,
        gpuRenderer = "Mali-G57 MC2",
        glEsVersion = 0x0003_0002,
        isLowRamDevice = false,
    )
    val hints = GameProfileHints(
        title = "Hollow Knight",
        installDirectory = "/storage/emulated/0/Games/Hollow Knight",
        detectedApis = setOf(GraphicsApi.D3D9),
        genre = GameGenre.PLATFORMER,
        inspected = true,
    )
    val optimizer = AutoOptimizer()

    NexoraTheme {
        GameDetailScreen(
            game = Game(
                id = "preview",
                name = "Hollow Knight",
                executablePath = "/storage/emulated/0/Games/Hollow Knight/hollow_knight.exe",
                installDirectory = "/storage/emulated/0/Games/Hollow Knight",
                sizeBytes = 50_500_000L,
                addedAtMillis = 1_706_000_000_000L,
                source = GameSource.SCAN,
            ),
            onBack = {},
            profileRecommendation = optimizer.recommend(device, hints),
            customParameters = optimizer.parameters(
                profile = PerformanceProfile.AUTO_CUSTOM,
                device = device,
                hints = hints,
            ),
            onSelectProfile = {},
            onPlay = {},
            onChangeIcon = {},
            onCopyPath = {},
            onRemove = {},
        )
    }
}
