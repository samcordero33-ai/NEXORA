package com.nexora.android.control.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nexora.android.control.model.ControlAction
import com.nexora.android.control.model.ControlLayout
import com.nexora.android.control.model.HudControl

/**
 * Ajustes del HUD táctil de un juego (Fase 5).
 *
 * Es una fila por juego: guarda la transparencia de la capa y si el arrastre
 * con un dedo dibuja el recuadro de selección. La posición de los botones vive
 * en `control_buttons`; separar las dos cosas evita reescribir ocho filas cada
 * vez que el usuario solo cambia la transparencia.
 */
@Entity(tableName = "control_profiles")
data class ControlProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "gameId")
    val gameId: String,

    @ColumnInfo(name = "overlayOpacity")
    val overlayOpacity: Float,

    @ColumnInfo(name = "dragBoxEnabled")
    val dragBoxEnabled: Boolean,

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long,
)

/**
 * Reconstruye la disposición completa de un juego.
 *
 * Las acciones que no tengan fila (un juego recién escaneado, o una acción
 * añadida en una versión posterior) caen a su valor predeterminado, y las filas
 * de acciones desconocidas se descartan: leer la disposición nunca puede fallar.
 */
fun layoutFrom(
    gameId: String,
    profile: ControlProfileEntity?,
    buttons: List<ControlButtonEntity>,
): ControlLayout {
    val byAction = buttons.mapNotNull { entity ->
        ControlAction.fromId(entity.actionId)?.let { action ->
            action to HudControl(
                action = action,
                centerX = entity.centerX,
                centerY = entity.centerY,
                size = entity.size,
                visible = entity.visible,
            )
        }
    }.toMap()

    return ControlLayout(
        gameId = gameId,
        overlayOpacity = profile?.overlayOpacity ?: ControlLayout.DEFAULT_OPACITY,
        dragBoxEnabled = profile?.dragBoxEnabled ?: true,
        controls = ControlAction.ALL.map { action ->
            byAction[action] ?: HudControl.defaultOf(action)
        },
        updatedAtMillis = profile?.updatedAt ?: 0L,
    )
}

/** Par de filas (perfil y botones) que hay que escribir para guardar un HUD. */
fun ControlLayout.toEntities(updatedAt: Long): Pair<ControlProfileEntity, List<ControlButtonEntity>> {
    val clamped = clamped()
    val profile = ControlProfileEntity(
        gameId = clamped.gameId,
        overlayOpacity = clamped.overlayOpacity,
        dragBoxEnabled = clamped.dragBoxEnabled,
        updatedAt = updatedAt,
    )
    val buttons = clamped.controls.map { control ->
        ControlButtonEntity(
            gameId = clamped.gameId,
            actionId = control.action.id,
            centerX = control.centerX,
            centerY = control.centerY,
            size = control.size,
            visible = control.visible,
        )
    }
    return profile to buttons
}
