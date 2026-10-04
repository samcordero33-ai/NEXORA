package com.nexora.android.runtime.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nexora.android.runtime.env.Box64DynarecSize
import com.nexora.android.runtime.env.Box64LogLevel
import com.nexora.android.runtime.env.DxvkHudPreset
import com.nexora.android.runtime.env.EnvironmentSettings
import com.nexora.android.runtime.env.GalliumDriver
import com.nexora.android.runtime.env.MesaVkWinsys

/**
 * Configuración del entorno de ejecución por juego (Fase 6).
 *
 * Se guarda **una fila por juego**: la clave primaria es `gameId`, así un
 * `upsert` reemplaza la fila anterior y nunca se duplica. Las columnas de
 * variables se guardan como `String` (no como ordinal) para que añadir un
 * preset en el futuro no obligue a migrar.
 *
 * `winetricksVerbs` se serializa como una cadena separada por comas: es
 * una lista pequeña (decenas de verbos como mucho) y SQLite no soporta
 * listas nativas, así que la unión manual es la opción más simple y barata.
 */
@Entity(tableName = "runtime_configs")
data class RuntimeConfigEntity(
    @PrimaryKey
    @ColumnInfo(name = "gameId")
    val gameId: String,

    @ColumnInfo(name = "dxvkHud")
    val dxvkHud: String,

    @ColumnInfo(name = "box64Log")
    val box64Log: String,

    @ColumnInfo(name = "box64Dynarec")
    val box64Dynarec: String,

    @ColumnInfo(name = "galliumDriver")
    val galliumDriver: String,

    @ColumnInfo(name = "mesaVkWinsys")
    val mesaVkWinsys: String,

    @ColumnInfo(name = "wineDebug")
    val wineDebug: String,

    @ColumnInfo(name = "winetricksVerbs")
    val winetricksVerbs: String,

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long,
)

/** Convierte la fila en ajustes de entorno normalizados. */
fun RuntimeConfigEntity.toSettings(): EnvironmentSettings = EnvironmentSettings.fromStorage(
    dxvkHud = dxvkHud,
    box64Log = box64Log,
    box64Dynarec = box64Dynarec,
    galliumDriver = galliumDriver,
    mesaVkWinsys = mesaVkWinsys,
    wineDebug = wineDebug,
    winetricksVerbs = if (winetricksVerbs.isBlank()) {
        emptyList()
    } else {
        winetricksVerbs.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    },
)

/** Serializa los ajustes de entorno a una fila nueva (sin `updatedAt`). */
fun EnvironmentSettings.toEntity(
    gameId: String,
    updatedAt: Long,
): RuntimeConfigEntity {
    val normalized = normalized()
    return RuntimeConfigEntity(
        gameId = gameId,
        dxvkHud = normalized.dxvkHud.wireValue,
        box64Log = normalized.box64Log.wireValue,
        box64Dynarec = normalized.box64Dynarec.wireValue,
        galliumDriver = normalized.galliumDriver.wireValue,
        mesaVkWinsys = normalized.mesaVkWinsys.wireValue,
        wineDebug = normalized.wineDebug,
        winetricksVerbs = normalized.winetricksVerbs.joinToString(","),
        updatedAt = updatedAt,
    )
}