package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.TripEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips WHERE endTime IS NOT NULL ORDER BY startTime DESC")
    fun getAllCompletedTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE endTime IS NULL LIMIT 1")
    fun getActiveTripFlow(): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE endTime IS NULL LIMIT 1")
    suspend fun getActiveTrip(): TripEntity?

    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    suspend fun getTripById(id: String): TripEntity?

    @Query("SELECT * FROM trips WHERE category = :category AND endTime IS NOT NULL ORDER BY startTime DESC")
    fun getTripsByCategory(category: String): Flow<List<TripEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripEntity)

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Delete
    suspend fun deleteTrip(trip: TripEntity)

    @Query("SELECT * FROM trips WHERE vehicleId = :vehicleId")
    suspend fun getTripsByVehicleId(vehicleId: String): List<TripEntity>

    @Update
    suspend fun updateTrips(trips: List<TripEntity>)

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteTripById(id: String)
}
