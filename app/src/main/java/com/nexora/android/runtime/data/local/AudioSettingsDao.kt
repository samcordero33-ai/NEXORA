package com.nexora.android.runtime.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioSettingsDao {

    @Query("SELECT * FROM audio_settings WHERE gameId = :gameId LIMIT 1")
    fun observe(gameId: String): Flow<AudioSettingsEntity?>

    @Query("SELECT * FROM audio_settings WHERE gameId = :gameId LIMIT 1")
    suspend fun get(gameId: String): AudioSettingsEntity?

    @Upsert
    suspend fun upsert(entity: AudioSettingsEntity)

    @Query("DELETE FROM audio_settings WHERE gameId = :gameId")
    suspend fun deleteForGame(gameId: String)
}