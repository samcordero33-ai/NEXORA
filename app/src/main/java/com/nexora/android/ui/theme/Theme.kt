package com.nexora.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/*
 * NEXORA es solo oscura y no reacciona al tema del sistema: es un launcher de
 * juegos, y un fondo claro arruinaría tanto la estética como el consumo en
 * pantallas OLED durante sesiones largas.
 */
private val NexoraColorScheme = darkColorScheme(
    primary = NexoraAccent,
    onPrimary = Color(0xFF04140A),
    primaryContainer = NexoraAccent.copy(alpha = 0.14f),
    onPrimaryContainer = NexoraAccent,

    secondary = NexoraAccentAlt,
    onSecondary = Color(0xFF0E0A18),
    secondaryContainer = NexoraAccentAlt.copy(alpha = 0.14f),
    onSecondaryContainer = NexoraAccentAlt,

    background = NexoraBackground,
    onBackground = NexoraTextPrimary,

    surface = NexoraSurface,
    onSurface = NexoraTextPrimary,
    surfaceVariant = NexoraSurfaceElevated,
    onSurfaceVariant = NexoraTextSecondary,
    surfaceTint = Color.Transparent,

    outline = NexoraBorder,
    outlineVariant = NexoraBorder,
)

/** Tokens semánticos de NEXORA. */
@Immutable
object NexoraTokens {
    val background: Color get() = NexoraBackground
    val card: Color get() = NexoraSurface
    val cardBorder: Color get() = NexoraBorder
    val textPrimary: Color get() = NexoraTextPrimary
    val textSecondary: Color get() = NexoraTextSecondary
    val accent: Color get() = NexoraAccent
    val accentAlt: Color get() = NexoraAccentAlt
    val warning: Color get() = NexoraWarning
}

@Composable
fun NexoraTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = NexoraColorScheme,
        typography = NexoraTypography,
        content = content,
    )
}
