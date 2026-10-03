package com.nexora.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexora.android.R
import com.nexora.android.game.model.Game
import com.nexora.android.game.model.GameIconPreset
import com.nexora.android.game.model.GameSource
import com.nexora.android.ui.theme.NexoraAccent
import com.nexora.android.ui.theme.NexoraAccentAlt
import com.nexora.android.ui.theme.NexoraBackground
import com.nexora.android.ui.theme.NexoraBorder
import com.nexora.android.ui.theme.NexoraSurface
import com.nexora.android.ui.theme.NexoraSurfaceElevated
import com.nexora.android.ui.theme.NexoraTextPrimary
import com.nexora.android.ui.theme.NexoraTextSecondary
import com.nexora.android.ui.theme.NexoraTheme
import com.nexora.android.ui.util.formatBytes

private val CARD_SHAPE = RoundedCornerShape(16.dp)
private val BADGE_SHAPE = RoundedCornerShape(12.dp)

/**
 * Tarjeta de juego de la biblioteca.
 *
 * Toda la tarjeta abre el detalle; el botón "Jugar" y el icono de ajustes
 * consumen su propia pulsación, así que no abren el detalle por accidente.
 *
 * @param onPlay Acción del botón "Jugar". En la Fase 3 informa de que el
 *   runtime aún no está integrado: el botón ya está cableado para que la tarjeta
 *   no cambie cuando llegue el motor de ejecución.
 * @param onSettings Abre los ajustes del juego (pantalla de detalle).
 */
@Composable
fun GameCard(
    game: Game,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(color = NexoraSurface, shape = CARD_SHAPE)
            .border(width = 1.dp, color = NexoraBorder, shape = CARD_SHAPE)
            .clip(CARD_SHAPE)
            .clickable(onClick = onOpen)
            .padding(12.dp),
    ) {
        GameIconBadge(
            preset = game.iconPreset,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = game.name,
            style = MaterialTheme.typography.titleSmall,
            color = NexoraTextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = buildSubtitle(game),
            style = MaterialTheme.typography.bodySmall,
            color = NexoraTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Button(
                onClick = onPlay,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NexoraAccent,
                    contentColor = NexoraBackground,
                ),
                contentPadding = PaddingValues(horizontal = 10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = stringResource(R.string.action_play),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }

            Spacer(modifier = Modifier.size(8.dp))

            IconButton(onClick = onSettings, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.action_game_settings),
                    tint = NexoraTextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Marco del icono de la tarjeta.
 *
 * Es un degradado, no una imagen: un juego detectado no trae carátula y NEXORA
 * no lee los recursos del `.exe`, así que el icono predeterminado se dibuja.
 */
@Composable
fun GameIconBadge(
    preset: GameIconPreset,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(16f / 10f)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        NexoraAccentAlt.copy(alpha = 0.28f),
                        NexoraSurfaceElevated,
                    ),
                ),
                shape = BADGE_SHAPE,
            )
            .border(width = 1.dp, color = NexoraBorder, shape = BADGE_SHAPE),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = preset.vector,
            contentDescription = null,
            tint = NexoraAccent,
            modifier = Modifier.size(34.dp),
        )
    }
}

@Composable
private fun buildSubtitle(game: Game): String {
    val size = if (game.sizeBytes > 0L) {
        formatBytes(game.sizeBytes)
    } else {
        stringResource(R.string.library_size_unknown)
    }
    return if (game.source == GameSource.SCAN) {
        "$size · ${stringResource(R.string.library_detected_by_scan)}"
    } else {
        size
    }
}

@Preview(showBackground = true, widthDp = 200, heightDp = 260)
@Composable
private fun GameCardPreview() {
    NexoraTheme {
        Box(
            modifier = Modifier
                .background(Color.Unspecified)
                .padding(12.dp),
        ) {
            GameCard(
                game = Game(
                    id = "preview",
                    name = "Hollow Knight",
                    executablePath = "/storage/emulated/0/Games/Hollow Knight/hollow_knight.exe",
                    sizeBytes = 50_500_000L,
                    source = GameSource.SCAN,
                ),
                onOpen = {},
                onPlay = {},
                onSettings = {},
            )
        }
    }
}
