package com.nexora.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexora.android.R
import com.nexora.android.game.scan.ScanProgress
import com.nexora.android.ui.theme.NexoraAccent
import com.nexora.android.ui.theme.NexoraTextPrimary
import com.nexora.android.ui.theme.NexoraTextSecondary
import com.nexora.android.ui.theme.NexoraWarning

/**
 * Progreso de un escaneo en curso.
 *
 * Además de la barra se muestran contadores y la ruta actual: en un escaneo
 * sobre microSD la espera se percibe mucho más corta si se ve que algo avanza.
 */
@Composable
fun ScanProgressCard(
    progress: ScanProgress,
    modifier: Modifier = Modifier,
) {
    NexoraCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.scan_progress_title),
            style = MaterialTheme.typography.titleSmall,
            color = NexoraTextPrimary,
        )

        Spacer(modifier = Modifier.size(12.dp))

        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier.fillMaxWidth(),
            color = NexoraAccent,
        )

        Spacer(modifier = Modifier.size(10.dp))

        Text(
            text = stringResource(
                R.string.scan_progress_counters,
                progress.filesScanned.toInt(),
                progress.directoriesScanned.toInt(),
                progress.executablesFound.toInt(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = NexoraTextSecondary,
        )

        if (progress.currentPath.isNotBlank()) {
            Text(
                text = progress.currentPath,
                style = MaterialTheme.typography.bodySmall,
                color = NexoraTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (progress.exhausted) {
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.scan_progress_truncated),
                style = MaterialTheme.typography.bodySmall,
                color = NexoraWarning,
            )
        }
    }
}
