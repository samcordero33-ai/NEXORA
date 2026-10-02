package com.nexora.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nexora.android.ui.theme.NexoraBorder
import com.nexora.android.ui.theme.NexoraSurface

/**
 * Tarjeta base de NEXORA: superficie oscura con borde de 1 px.
 *
 * El borde se aplica después del fondo para que quede dibujado encima.
 */
@Composable
fun NexoraCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = NexoraSurface,
    borderColor: Color = NexoraBorder,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .background(color = backgroundColor, shape = shape)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .padding(20.dp),
        content = content,
    )
}

/** Encabezado de sección: etiqueta en versalitas espaciadas con acento. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = accent,
        modifier = modifier,
    )
}
