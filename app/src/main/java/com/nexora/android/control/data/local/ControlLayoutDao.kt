package com.nexora.android.control.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Acceso a los perfiles de control táctil.
 *
 * Las dos lecturas son `Flow`: al guardar un botón o cambiar la transparencia,
 * Room re-emite y la UI se refresca sola.
 */
@Dao
interface ControlLayoutDao {

    /** Ajustes del HUD de un juego; `null` si todavía no se ha guardado nada. */
    @Query("SELECT * FROM control_profiles WHERE gameId = :gameId")
    fun observeProfile(gameId: String): Flow<ControlProfileEntity?>

    /** Botones guardados de un juego; vacío si usa la disposición por defecto. */
    @Query("SELECT * FROM control_buttons WHERE gameId = :gameId")
    fun observeButtons(gameId: String): Flow<List<ControlButtonEntity>>

    @Upsert
    suspend fun upsertProfile(profile: ControlProfileEntity)

    @Upsert
    suspend fun upsertButtons(buttons: List<ControlButtonEntity>)

    @Query("DELETE FROM control_buttons WHERE gameId = :gameId")
    suspend fun deleteButtons(gameId: String)

    @Query("DELETE FROM control_profiles WHERE gameId = :gameId")
    suspend fun deleteProfile(gameId: String)
}
