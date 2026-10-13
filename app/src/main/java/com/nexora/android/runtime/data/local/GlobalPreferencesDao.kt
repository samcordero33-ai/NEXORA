package com.nexora.android.runtime.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GlobalPreferencesDao {

    @Query("SELECT * FROM global_preferences WHERE id = :id LIMIT 1")
    fun observe(id: Int = GlobalPreferencesEntity.SINGLETON_ID): Flow<GlobalPreferencesEntity?>

    @Query("SELECT * FROM global_preferences WHERE id = :id LIMIT 1")
    suspend fun get(id: Int = GlobalPreferencesEntity.SINGLETON_ID): GlobalPreferencesEntity?

    @Upsert
    suspend fun upsert(entity: GlobalPreferencesEntity)

    @Query("DELETE FROM global_preferences")
    suspend fun deleteAll()
}