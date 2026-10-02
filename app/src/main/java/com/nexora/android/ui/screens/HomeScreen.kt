package com.nexora.android.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexora.android.engine.DeviceInfo
import com.nexora.android.engine.DeviceInfoProvider
import com.nexora.android.ui.components.NexoraCard
import com.nexora.android.ui.components.SectionHeader
import com.nexora.android.ui.theme.NexoraAccent
import com.nexora.android.ui.theme.NexoraBackground
import com.nexora.android.ui.theme.NexoraSubtitleTextStyle
import com.nexora.android.ui.theme.NexoraTextPrimary
import com.nexora.android.ui.theme.NexoraTextSecondary
import com.nexora.android.ui.theme.NexoraTheme
import com.nexora.android.ui.theme.NexoraWarning

private const val BYTES_PER_GIGABYTE = 1024.0 * 1024.0 * 1024.0

/**
 * Pantalla principal de NEXORA.
 *
 * Compuesta por cuatro bloques: encabezado de marca, biblioteca de juegos
 * (vacía en esta fase), botón principal "Añadir juego" e información del
 * dispositivo.
 */
@Composable
fun NexoraHomeScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val deviceInfo by produceState<DeviceInfo?>(initialValue = null) {
        value = DeviceInfoProvider(context).getDeviceInfo()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = NexoraBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            NexoraHeader()

            Spacer(modifier = Modifier.height(28.dp))

            SectionHeader(title = "Mis juegos")
            Spacer(modifier = Modifier.height(12.dp))
            EmptyGameLibrary()

            Spacer(modifier = Modifier.height(20.dp))

            AddGameButton(onClick = {
                // Fase 2: abrir el selector de ejecutable y dar de alta el juego.
            })

            Spacer(modifier = Modifier.height(32.dp))

            SectionHeader(title = "Información del dispositivo")
            Spacer(modifier = Modifier.height(12.dp))
            if (deviceInfo == null) {
                DeviceInfoLoading()
            } else {
                DeviceInfoCard(deviceInfo = deviceInfo!!)
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
            text = "Windows Gaming Runtime for Android",
            style = NexoraSubtitleTextStyle,
            color = NexoraTextSecondary,
        )
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
                text = "No hay juegos agregados",
                style = MaterialTheme.typography.titleMedium,
                color = NexoraTextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Añade tu primer juego para verlo aquí.",
                style = MaterialTheme.typography.bodyMedium,
                color = NexoraTextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Botón principal.
 *
 * En la Fase 1 no tiene destino: el alta de juegos requiere el explorador de
 * archivos y el modelo de biblioteca persistente, que llegan en la Fase 2.
 */
@Composable
private fun AddGameButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = NexoraAccent,
            contentColor = Color(0xFF04140A),
        ),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.size(10.dp))
        Text(
            text = "Añadir juego",
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun DeviceInfoLoading(
    modifier: Modifier = Modifier,
) {
    NexoraCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Leyendo datos del dispositivo...",
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
        DeviceInfoRow(label = "Dispositivo", value = "${deviceInfo.manufacturer} ${deviceInfo.model}")
        DeviceInfoRow(
            label = "Android",
            value = "${deviceInfo.androidVersion} (API ${deviceInfo.apiLevel})",
        )
        DeviceInfoRow(label = "Arquitectura", value = deviceInfo.primaryAbi)
        DeviceInfoRow(label = "Núcleos de CPU", value = deviceInfo.cpuCoreCount.toString())

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(10.dp))

        DeviceInfoRow(
            label = "Memoria total",
            value = deviceInfo.totalMemoryBytes.toGigabytes(),
        )
        DeviceInfoRow(
            label = "Memoria disponible",
            value = deviceInfo.availableMemoryBytes.toGigabytes(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Memory,
                contentDescription = null,
                tint = NexoraWarning,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "Runtime de ejecución no integrado todavía (Fase 2)",
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

private fun Long.toGigabytes(): String {
    val gigabytes = this / BYTES_PER_GIGABYTE
    return if (this <= 0L) "Desconocido" else String.format("%.2f GB", gigabytes)
}

@Preview(showBackground = true, widthDp = 420, heightDp = 900)
@Composable
private fun NexoraHomeScreenPreview() {
    NexoraTheme {
        NexoraHomeScreen()
    }
}
