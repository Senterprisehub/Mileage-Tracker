package com.example.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.data.model.AppSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): AppSettingsEntity?

    @Upsert
    suspend fun insertOrUpdate(settings: AppSettingsEntity)
}
