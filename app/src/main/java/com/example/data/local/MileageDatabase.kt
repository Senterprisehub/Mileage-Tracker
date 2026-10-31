package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.fuel.LiveFuelPriceManager
import com.example.data.model.AppSettingsEntity
import com.example.data.model.SavedPlaceEntity
import com.example.data.model.TripEntity
import com.example.data.model.TripPointEntity
import com.example.data.model.VehicleEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        VehicleEntity::class,
        TripEntity::class,
        TripPointEntity::class,
        SavedPlaceEntity::class,
        AppSettingsEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class MileageDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun tripDao(): TripDao
    abstract fun tripPointDao(): TripPointDao
    abstract fun savedPlaceDao(): SavedPlaceDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: MileageDatabase? = null

        fun getInstance(context: Context): MileageDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MileageDatabase::class.java,
                    "mileage_tracker.db"
                ).fallbackToDestructiveMigration()
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // No pre-fill data for vehicles, trips, or places as requested by user.
                        // ONLY initialize baseline live fuel price settings for the default country.
                        CoroutineScope(Dispatchers.IO).launch {
                            val database = getInstance(context)
                            val todayRate = LiveFuelPriceManager.getTodayFuelRate("IN", "New Delhi", "PETROL")
                            database.settingsDao().insertOrUpdate(
                                AppSettingsEntity(
                                    currentFuelPrice = todayRate.price,
                                    currentCity = todayRate.cityName,
                                    selectedFuelType = todayRate.fuelType,
                                    fuelRateUnit = todayRate.unit,
                                    lastFuelRateFetchTime = System.currentTimeMillis(),
                                    lastFuelRateSource = todayRate.source
                                )
                            )
                        }
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Strict zero pre-fill guarantee: Ensure no legacy mock entries exist
                        try {
                            db.execSQL("DELETE FROM vehicles WHERE name LIKE '%Hero / RE 350%' OR name LIKE '%Swift / City%' OR name LIKE '%Demo%'")
                            db.execSQL("DELETE FROM saved_places WHERE (name = 'Home' AND latitude BETWEEN 19.07 AND 19.08) OR (name = 'Office HQ' AND latitude BETWEEN 19.08 AND 19.09)")
                            db.execSQL("DELETE FROM trips WHERE notes LIKE '%mock%' OR notes LIKE '%demo%' OR startAddress LIKE '%Sample%'")
                        } catch (_: Exception) {
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
