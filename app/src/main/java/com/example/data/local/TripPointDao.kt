package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.TripPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripPointDao {
    @Query("SELECT * FROM trip_points WHERE tripId = :tripId ORDER BY timestamp ASC")
    fun getPointsForTrip(tripId: String): Flow<List<TripPointEntity>>

    @Query("SELECT * FROM trip_points WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun getPointsForTripDirect(tripId: String): List<TripPointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: TripPointEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<TripPointEntity>)

    @Query("DELETE FROM trip_points WHERE tripId = :tripId")
    suspend fun deletePointsForTrip(tripId: String)
}
