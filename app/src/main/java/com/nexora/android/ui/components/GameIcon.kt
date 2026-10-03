package com.nexora.android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.VideogameAsset
import androidx.compose.ui.graphics.vector.ImageVector
import com.nexora.android.game.model.GameIconPreset

/**
 * Icono material asociado a cada [GameIconPreset].
 *
 * La traducción vive en la capa de UI a propósito: el modelo de dominio guarda
 * el preset (un ordinal persistible), no un `ImageVector`, que solo existe en
 * Compose y no se puede guardar en SQLite.
 */
val GameIconPreset.vector: ImageVector
    get() = when (this) {
        GameIconPreset.JOYPAD -> Icons.Outlined.SportsEsports
        GameIconPreset.ARCADE -> Icons.Outlined.VideogameAsset
        GameIconPreset.DICE -> Icons.Outlined.Casino
    }
