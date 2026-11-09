package com.nexora.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexora.android.R
import com.nexora.android.runtime.preferences.AppTheme
import com.nexora.android.ui.components.EnumRow
import com.nexora.android.ui.components.MessageCard
import com.nexora.android.ui.components.NexoraCard
import com.nexora.android.ui.components.SectionHeader
import com.nexora.android.ui.settings.GlobalSettingsViewModel
import com.nexora.android.ui.theme.NexoraAccent
import com.nexora.android.ui.theme.NexoraBackground
import com.nexora.android.ui.theme.NexoraTextPrimary
import com.nexora.android.ui.theme.NexoraTextSecondary
import com.nexora.android.ui.theme.NexoraWarning

@Composable
fun GlobalSettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GlobalSettingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GlobalSettingsScreen(
        theme = state.preferences.theme,
        animationsEnabled = state.preferences.animationsEnabled,
        autoExportLogs = state.preferences.autoExportLogs,
        defaultBackend = state.preferences.defaultBackend,
        infoMessage = state.infoMessage,
        errorMessage = state.errorMessage,
        onBack = onBack,
        onThemeChange = viewModel::setTheme,
        onAnimationsChange = viewModel::setAnimationsEnabled,
        onAutoExportChange = viewModel::setAutoExportLogs,
        onBackendChange = viewModel::setDefaultBackend,
        onReset = viewModel::resetAll,
        onDismissMessages = viewModel::dismissMessages,
        modifier = modifier,
    )
}

@Composable
private fun GlobalSettingsScreen(
    theme: AppTheme,
    animationsEnabled: Boolean,
    autoExportLogs: Boolean,
    defaultBackend: String,
    infoMessage: String?,
    errorMessage: String?,
    onBack: () -> Unit,
    onThemeChange: (AppTheme) -> Unit,
    onAnimationsChange: (Boolean) -> Unit,
    onAutoExportChange: (Boolean) -> Unit,
    onBackendChange: (String) -> Unit,
    onReset: () -> Unit,
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
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = NexoraTextPrimary,
                )
            }

            if (infoMessage != null || errorMessage != null) {
                MessageCard(
                    message = infoMessage ?: errorMessage.orEmpty(),
                    isError = errorMessage != null,
                    onDismiss = onDismissMessages,
                )
            }

            SectionHeader(title = stringResource(R.string.settings_appearance))
            NexoraCard {
                EnumRow(
                    label = stringResource(R.string.settings_theme),
                    options = AppTheme.values().toList(),
                    selected = theme,
                    labelFor = { it.label },
                    onSelected = onThemeChange,
                )
                ToggleRow(
                    label = stringResource(R.string.settings_animations),
                    description = stringResource(R.string.settings_animations_body),
                    value = animationsEnabled,
                    onValueChange = onAnimationsChange,
                )
            }

            SectionHeader(title = stringResource(R.string.settings_runtime))
            NexoraCard {
                ToggleRow(
                    label = stringResource(R.string.settings_auto_export),
                    description = stringResource(R.string.settings_auto_export_body),
                    value = autoExportLogs,
                    onValueChange = onAutoExportChange,
                )
                EnumRow(
                    label = stringResource(R.string.settings_default_backend),
                    options = listOf("pulse", "aaudio", "opensles"),
                    selected = defaultBackend,
                    labelFor = { it },
                    onSelected = onBackendChange,
                )
            }

            SectionHeader(title = stringResource(R.string.settings_danger))
            NexoraCard {
                Text(
                    text = stringResource(R.string.settings_reset_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NexoraTextSecondary,
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(onClick = onReset) {
                    Icon(
                        Icons.Outlined.RestartAlt,
                        contentDescription = null,
                        tint = NexoraWarning,
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = stringResource(R.string.settings_reset),
                        color = NexoraWarning,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "NEXORA · v0.7",
                style = MaterialTheme.typography.labelSmall,
                color = NexoraAccent,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = NexoraTextPrimary,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = NexoraTextSecondary,
                )
            }
            Switch(checked = value, onCheckedChange = onValueChange)
        }
    }
}