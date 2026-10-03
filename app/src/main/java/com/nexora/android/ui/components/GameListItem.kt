package com.nexora.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexora.android.R
import com.nexora.android.game.model.Game
import com.nexora.android.game.model.GameSource
import com.nexora.android.ui.theme.NexoraAccent
import com.nexora.android.ui.theme.NexoraTextPrimary
import com.nexora.android.ui.theme.NexoraTextSecondary
import com.nexora.android.ui.util.formatBytes

/**
 * Entrada de la biblioteca.
 *
 * Muestra el título derivado del ejecutable, su ruta y su tamaño. La ruta se
 * muestra porque dos juegos distintos pueden llamarse igual y es la única forma
 * de distinguirlos mientras no haya carátulas.
 */
@Composable
fun GameListItem(
    game: Game,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NexoraCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.SportsEsports,
                contentDescription = null,
                tint = NexoraAccent,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = NexoraTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.size(2.dp))
                Text(
                    text = game.executablePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = NexoraTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.size(2.dp))
                Text(
                    text = buildSubtitle(game),
                    style = MaterialTheme.typography.bodySmall,
                    color = NexoraTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.size(8.dp))
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.library_remove_game),
                    tint = NexoraTextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
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
