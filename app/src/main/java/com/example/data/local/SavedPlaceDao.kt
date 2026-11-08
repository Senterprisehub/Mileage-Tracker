package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.SavedPlaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedPlaceDao {
    @Query("SELECT * FROM saved_places ORDER BY name ASC")
    fun getAllPlaces(): Flow<List<SavedPlaceEntity>>

    @Query("SELECT * FROM saved_places")
    suspend fun getAllPlacesList(): List<SavedPlaceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlace(place: SavedPlaceEntity)

    @Update
    suspend fun updatePlace(place: SavedPlaceEntity)

    @Delete
    suspend fun deletePlace(place: SavedPlaceEntity)
}
