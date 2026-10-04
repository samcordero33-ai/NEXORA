package com.nexora.android.control.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Posición, tamaño y visibilidad de **un** botón del HUD de un juego.
 *
 * Hay una fila por acción y juego, con clave primaria compuesta: así la
 * disposición se guarda aunque el usuario solo mueva un botón, y una acción que
 * la versión instalada no conozca se queda en la tabla sin estorbar (se ignora
 * al leer).
 *
 * Las coordenadas son fracciones 0..1 de la superficie: guardar píxeles
 * ataría la disposición a una pantalla concreta.
 */
@Entity(
    tableName = "control_buttons",
    primaryKeys = ["gameId", "actionId"],
)
data class ControlButtonEntity(
    @ColumnInfo(name = "gameId")
    val gameId: String,

    @ColumnInfo(name = "actionId")
    val actionId: String,

    @ColumnInfo(name = "centerX")
    val centerX: Float,

    @ColumnInfo(name = "centerY")
    val centerY: Float,

    @ColumnInfo(name = "size")
    val size: Float,

    @ColumnInfo(name = "visible")
    val visible: Boolean,
)
