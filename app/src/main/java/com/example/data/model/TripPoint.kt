package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trip_points",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tripId"]), Index(value = ["timestamp"])]
)
data class TripPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tripId: String,
    val latitude: Double,
    val longitude: Double,
    val speed: Float = 0f,
    val timestamp: Long
)
