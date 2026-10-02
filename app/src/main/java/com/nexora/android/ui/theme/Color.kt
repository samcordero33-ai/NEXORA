package com.nexora.android.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * PALETA GAMING OSCURA DE NEXORA
 * ---------------------------------------------------------------------------
 * Un solo acento saturado sobre un fondo casi negro: es la convención visual de
 * los launchers y clientes de juegos, y además es la que mejor aprovecha las
 * pantallas OLED de los móviles donde se va a ejecutar el runtime.
 *
 * Fuente de verdad del diseño: ningún composable declara colores literales.
 */

/** Fondo base, casi negro (no negro puro: evita el efecto "hueco" en OLED). */
val NexoraBackground = Color(0xFF080A0E)

/** Superficie de tarjetas y paneles. */
val NexoraSurface = Color(0xFF12161F)

/** Superficie elevada (chips, botones secundarios). */
val NexoraSurfaceElevated = Color(0xFF1B2130)

/** Acento principal: verde neón. Acciones y estado activo. */
val NexoraAccent = Color(0xFF00E676)

/** Acento secundario: violeta. Acentos complementarios y gráficos. */
val NexoraAccentAlt = Color(0xFF7C5CFF)

/** Texto principal. */
val NexoraTextPrimary = Color(0xFFE8ECF4)

/** Texto secundario / etiquetas. */
val NexoraTextSecondary = Color(0xFF8A93A6)

/** Texto deshabilitado. */
val NexoraTextDisabled = Color(0xFF5A6376)

/** Borde de tarjetas y separadores. */
val NexoraBorder = Color(0xFF232A38)

/** Fondo del botón principal deshabilitado. */
val NexoraAccentMuted = Color(0xFF2C3A38)

/** Estado de advertencia (p. ej. runtime no disponible). */
val NexoraWarning = Color(0xFFFFB74D)
