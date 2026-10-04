package com.nexora.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import com.nexora.android.ui.theme.NexoraAccent
import com.nexora.android.ui.theme.NexoraBackground
import com.nexora.android.ui.theme.NexoraBorder
import com.nexora.android.ui.theme.NexoraSurfaceElevated
import com.nexora.android.ui.theme.NexoraTextPrimary
import com.nexora.android.ui.theme.NexoraTextSecondary
import com.nexora.android.ui.theme.NexoraTheme

private val CHIP_SHAPE = RoundedCornerShape(8.dp)

/**
 * Tarjeta del perfil de rendimiento de un juego (AutoOptimizer).
 *
 * Muestra el perfil **efectivo** —el que ha elegido el usuario, o el que
 * recomienda el AutoOptimizer si está en automático—, los parámetros que aplica
 * y los motivos de la recomendación. Desde aquí se cambia el perfil a mano.
 *
 * @param recommendation Recomendación calculada para este juego; `null` mientras
 *   se está calculando.
 * @param selectedProfile Perfil fijado por el usuario; `null` es automático.
 * @param customParameters Parámetros derivados para el perfil
 *   [PerformanceProfile.AUTO_CUSTOM] en este dispositivo.
 * @param onSelectProfile Fija un perfil, o `null` para volver al automático.
 */
@Composable
fun PerformanceProfileCard(
    recommendation: ProfileRecommendation?,
    selectedProfile: PerformanceProfile?,
    customParameters: PerformanceParameters?,
    onSelectProfile: (PerformanceProfile?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSelector by rememberSaveable { mutableStateOf(false) }

    NexoraCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = NexoraAccent,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.profile_recommended_title),
                style = MaterialTheme.typography.titleSmall,
                color = NexoraTextPrimary,
                modifier = Modifier.weight(1f),
            )
        }

        if (recommendation == null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.profile_calculating),
                style = MaterialTheme.typography.bodyMedium,
                color = NexoraTextSecondary,
            )
            return@NexoraCard
        }

        val effectiveProfile = selectedProfile ?: recommendation.profile
        val parameters = when (selectedProfile) {
            null -> recommendation.parameters
            PerformanceProfile.AUTO_CUSTOM -> customParameters ?: recommendation.parameters
            else -> selectedProfile.parameters
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = effectiveProfile.technicalName,
                style = MaterialTheme.typography.titleMedium,
                color = NexoraTextPrimary,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.size(8.dp))
            ProfileModeChip(isAutomatic = selectedProfile == null)
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (selectedProfile == null) recommendation.profile.summary else effectiveProfile.summary,
            style = MaterialTheme.typography.bodySmall,
            color = NexoraTextSecondary,
        )

        if (selectedProfile == null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.profile_confidence, recommendation.confidence),
                style = MaterialTheme.typography.bodySmall,
                color = NexoraAccent,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        ParameterGrid(parameters = parameters)

        if (recommendation.reasons.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Column {
                recommendation.reasons.forEach { reason ->
                    Text(
                        text = "· $reason",
                        style = MaterialTheme.typography.bodySmall,
                        color = NexoraTextSecondary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = { showSelector = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = stringResource(R.string.profile_change),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }

    if (showSelector) {
        ProfileSelectorDialog(
            selectedProfile = selectedProfile,
            // El diálogo solo se abre desde la rama con recomendación calculada;
            // el acceso seguro evita perder el smart cast al salir de la lambda.
            recommendedProfile = recommendation?.profile,
            onSelectProfile = { profile ->
                showSelector = false
                onSelectProfile(profile)
            },
            onDismiss = { showSelector = false },
        )
    }
}

/** Etiqueta que distingue una elección manual del automático. */
@Composable
private fun ProfileModeChip(
    isAutomatic: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(
            if (isAutomatic) R.string.profile_mode_auto else R.string.profile_mode_manual,
        ),
        style = MaterialTheme.typography.labelSmall,
        color = if (isAutomatic) NexoraAccent else NexoraTextSecondary,
        modifier = modifier
            .background(color = NexoraSurfaceElevated, shape = CHIP_SHAPE)
            .border(width = 1.dp, color = NexoraBorder, shape = CHIP_SHAPE)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** Rejilla de dos columnas con los parámetros del perfil. */
@Composable
private fun ParameterGrid(
    parameters: PerformanceParameters,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ParameterRow(
            label = stringResource(R.string.profile_label_resolution),
            value = parameters.resolution.label,
        )
        ParameterRow(
            label = stringResource(R.string.profile_label_fps),
            value = if (parameters.fpsCap == 0) {
                stringResource(R.string.profile_fps_unlimited)
            } else {
                stringResource(R.string.profile_fps_value, parameters.fpsCap)
            },
        )
        ParameterRow(
            label = stringResource(R.string.profile_label_backend),
            value = parameters.backend.label,
        )
        ParameterRow(
            label = stringResource(R.string.profile_label_cpu),
            value = parameters.cpuPriority.label,
        )
        ParameterRow(
            label = stringResource(R.string.profile_label_effects),
            value = parameters.effectsQuality.label,
        )
        ParameterRow(
            label = stringResource(R.string.profile_label_textures),
            value = parameters.textureQuality.label,
        )
        ParameterRow(
            label = stringResource(R.string.profile_label_threads),
            value = stringResource(R.string.profile_threads_value, parameters.workerThreads),
        )
        ParameterRow(
            label = stringResource(R.string.profile_label_vram),
            value = stringResource(R.string.profile_mb_value, parameters.videoMemoryBudgetMb),
        )
        ParameterRow(
            label = stringResource(R.string.profile_label_audio),
            value = stringResource(R.string.profile_ms_value, parameters.audioLatencyMs),
        )
        ParameterRow(
            label = stringResource(R.string.profile_label_vsync),
            value = stringResource(
                if (parameters.vsync) R.string.profile_value_yes else R.string.profile_value_no,
            ),
        )
    }
}

@Composable
private fun ParameterRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = NexoraTextSecondary,
        )
        Spacer(modifier = Modifier.size(12.dp))
        Box(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = NexoraTextPrimary,
            )
        }
    }
}

/**
 * Selector de perfil.
 *
 * La primera opción devuelve el control al AutoOptimizer; el resto son los diez
 * perfiles cerrados. La opción recomendada se marca para que el usuario sepa qué
 * cambia si la pisa.
 */
@Composable
private fun ProfileSelectorDialog(
    selectedProfile: PerformanceProfile?,
    recommendedProfile: PerformanceProfile?,
    onSelectProfile: (PerformanceProfile?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.profile_dialog_title),
                style = MaterialTheme.typography.titleMedium,
                color = NexoraTextPrimary,
            )
        },
        text = {
            LazyColumn {
                item {
                    ProfileOptionRow(
                        name = stringResource(R.string.profile_option_auto),
                        summary = stringResource(R.string.profile_option_auto_summary),
                        isSelected = selectedProfile == null,
                        isRecommended = false,
                        onClick = { onSelectProfile(null) },
                    )
                }
                items(items = PerformanceProfile.ALL, key = { it.id }) { profile ->
                    ProfileOptionRow(
                        name = profile.technicalName,
                        summary = profile.summary,
                        isSelected = selectedProfile == profile,
                        isRecommended = profile == recommendedProfile,
                        onClick = { onSelectProfile(profile) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.profile_cancel))
            }
        },
    )
}

@Composable
private fun ProfileOptionRow(
    name: String,
    summary: String,
    isSelected: Boolean,
    isRecommended: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = NexoraAccent),
        )
        Spacer(modifier = Modifier.size(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = NexoraTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (isRecommended) {
                    "${stringResource(R.string.profile_option_recommended)} · $summary"
                } else {
                    summary
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isRecommended) NexoraAccent else NexoraTextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun PerformanceProfileCardPreview() {
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
        title = "Age of Empires II",
        installDirectory = "/storage/emulated/0/Games/AoE2",
        detectedApis = setOf(GraphicsApi.D3D9),
        genre = GameGenre.RTS,
        inspected = true,
    )
    val optimizer = AutoOptimizer()
    val recommendation = optimizer.recommend(device, hints)

    NexoraTheme {
        Box(
            modifier = Modifier
                .background(NexoraBackground)
                .padding(12.dp),
        ) {
            PerformanceProfileCard(
                recommendation = recommendation,
                selectedProfile = null,
                customParameters = optimizer.parameters(
                    profile = PerformanceProfile.AUTO_CUSTOM,
                    device = device,
                    hints = hints,
                ),
                onSelectProfile = {},
            )
        }
    }
}
