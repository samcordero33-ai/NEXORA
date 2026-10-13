package com.nexora.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nexora.android.ui.theme.NexoraAccent
import com.nexora.android.ui.theme.NexoraTextPrimary
import com.nexora.android.ui.theme.NexoraTextSecondary
import com.nexora.android.ui.theme.NexoraWarning

/**
 * Tarjeta genérica con un mensaje (info o error) y botón OK para cerrarlo.
 *
 * Usada en GameRunnerScreen, SavesManagerScreen, GlobalSettingsScreen.
 */
@Composable
fun MessageCard(
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
            Spacer(modifier = Modifier.size(8.dp))
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                Text("OK", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * Fila para elegir entre un conjunto de opciones tipadas.
 *
 * @param label Etiqueta de la fila.
 * @param options Lista de valores (de cualquier tipo `T`).
 * @param selected Valor actualmente elegido (se compara por `equals`).
 * @param labelFor Cómo mostrar cada opción.
 * @param onSelected Callback al pulsar una opción; recibe el valor, no el índice.
 */
@Composable
fun <T> EnumRow(
    label: String,
    options: List<T>,
    selected: T,
    labelFor: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = NexoraTextSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = labelFor(selected),
                style = MaterialTheme.typography.bodySmall,
                color = NexoraAccent,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                TextButton(
                    onClick = { onSelected(option) },
                    modifier = Modifier,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (isSelected) Color.White else NexoraTextSecondary,
                        containerColor = if (isSelected) NexoraAccent else Color.Transparent,
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = labelFor(option),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}