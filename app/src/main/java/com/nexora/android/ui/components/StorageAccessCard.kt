package com.nexora.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexora.android.R
import com.nexora.android.permission.StorageAccessState
import com.nexora.android.ui.theme.NexoraAccent
import com.nexora.android.ui.theme.NexoraTextPrimary
import com.nexora.android.ui.theme.NexoraTextSecondary
import com.nexora.android.ui.theme.NexoraWarning

/**
 * Estado del acceso al almacenamiento.
 *
 * Se muestra como tarjeta propia porque es la causa número uno de que un
 * escaneo no encuentre nada: si el usuario no sabe qué permiso falta, la
 * biblioteca vacía parece un fallo de la aplicación.
 */
@Composable
fun StorageAccessCard(
    state: StorageAccessState,
    onGrantAllFilesAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NexoraCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (state.canReadSharedStorage) {
                    Icons.Outlined.LockOpen
                } else {
                    Icons.Outlined.Lock
                },
                contentDescription = null,
                tint = if (state.canReadSharedStorage) NexoraAccent else NexoraWarning,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (state.canReadSharedStorage) {
                            R.string.storage_all_files_granted
                        } else {
                            R.string.storage_all_files_missing
                        },
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = NexoraTextPrimary,
                )
                val detail = buildDetail(state)
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = NexoraTextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (!state.canReadSharedStorage && state.canRequestAllFilesAccess) {
            Spacer(modifier = Modifier.size(14.dp))
            Button(
                onClick = onGrantAllFilesAccess,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NexoraAccent,
                    contentColor = NexoraTextPrimary,
                ),
            ) {
                Text(
                    text = stringResource(R.string.storage_grant_all_files),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun buildDetail(state: StorageAccessState): String? {
    val storageHint = stringResource(R.string.storage_saf_hint)
    val folders = if (state.grantedTrees.isEmpty()) {
        null
    } else {
        stringResource(R.string.storage_granted_folders, state.grantedTrees.size)
    }

    return when {
        state.needsLegacyReadPermission ->
            stringResource(R.string.storage_legacy_missing) + " " + storageHint

        !state.canReadSharedStorage -> storageHint

        folders != null -> "$folders · $storageHint"

        else -> null
    }
}
