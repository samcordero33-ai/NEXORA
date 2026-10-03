package com.nexora.android.game.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nexora.android.game.model.Game
import com.nexora.android.game.model.GameIconPreset
import com.nexora.android.game.model.GameSource

/**
 * Entidad Room de la biblioteca de juegos.
 *
 * Es la **única** fuente de verdad persistente de la Fase 3: lo que se guarda
 * aquí es lo que se ve al volver a abrir la aplicación.
 *
 * Campos que exige el modelo de biblioteca:
 * - `id`: identificador estable, clave primaria.
 * - `title`: título mostrado en la tarjeta.
 * - `exePath`: ruta del ejecutable.
 * - `iconPreset`: icono predeterminado del juego; `0` es el predeterminado de
 *   la biblioteca. `iconUri` queda reservado para un icono personalizado.
 * - `addedAt`: fecha de agregado, en milisegundos de época.
 *
 * El resto de columnas (`installDirectory`, `sizeBytes`, `source`,
 * `documentUri`) son los metadatos que ya producía el escáner de la Fase 2 y
 * que la pantalla de detalle necesita mostrar.
 *
 * `exePath` tiene índice **único**: es la identidad real de un juego dentro de
 * un volumen, así que volver a escanear la misma carpeta no puede duplicarlo
 * (la inserción usa `OnConflictStrategy.IGNORE`).
 */
@Entity(
    tableName = "games",
    indices = [Index(value = ["exePath"], unique = true)],
)
data class GameEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "exePath")
    val exePath: String,

    @ColumnInfo(name = "installDirectory")
    val installDirectory: String?,

    @ColumnInfo(name = "iconPreset", defaultValue = "0")
    val iconPreset: Int,

    @ColumnInfo(name = "iconUri")
    val iconUri: String?,

    @ColumnInfo(name = "addedAt")
    val addedAt: Long,

    @ColumnInfo(name = "source")
    val source: String,

    @ColumnInfo(name = "sizeBytes", defaultValue = "0")
    val sizeBytes: Long,

    @ColumnInfo(name = "documentUri")
    val documentUri: String?,
)

/** Convierte la fila al modelo de dominio usado por la UI y el escáner. */
fun GameEntity.toDomain(): Game = Game(
    id = id,
    name = title,
    executablePath = exePath,
    installDirectory = installDirectory,
    iconPreset = GameIconPreset.fromOrdinal(iconPreset),
    iconUri = iconUri,
    addedAtMillis = addedAt,
    source = GameSource.fromName(source),
    sizeBytes = sizeBytes,
    documentUri = documentUri,
)

/** Convierte el modelo de dominio a fila de base de datos. */
fun Game.toEntity(): GameEntity = GameEntity(
    id = id,
    title = name,
    exePath = executablePath,
    installDirectory = installDirectory,
    iconPreset = iconPreset.ordinal,
    iconUri = iconUri,
    addedAt = addedAtMillis,
    source = source.name,
    sizeBytes = sizeBytes,
    documentUri = documentUri,
)
