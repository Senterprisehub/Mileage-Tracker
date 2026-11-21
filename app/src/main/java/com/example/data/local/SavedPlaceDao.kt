package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.example.data.model.SavedPlaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedPlaceDao {
    @Query("SELECT * FROM saved_places ORDER BY name ASC")
    fun getAllPlaces(): Flow<List<SavedPlaceEntity>>

    @Query("SELECT * FROM saved_places")
    suspend fun getAllPlacesList(): List<SavedPlaceEntity>

    @Upsert
    suspend fun insertPlace(place: SavedPlaceEntity)

    @Upsert
    suspend fun upsertPlaces(places: List<SavedPlaceEntity>)

    @Update
    suspend fun updatePlace(place: SavedPlaceEntity)

    @Delete
    suspend fun deletePlace(place: SavedPlaceEntity)
}
