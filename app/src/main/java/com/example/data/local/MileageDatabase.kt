package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
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

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Schema preserved
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Schema preserved
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Schema preserved
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE app_settings ADD COLUMN stopTimeoutMinutes INTEGER NOT NULL DEFAULT 8")
                } catch (_: Exception) {
                    // Column already present
                }
            }
        }

        fun getInstance(context: Context): MileageDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MileageDatabase::class.java,
                    "mileage_tracker.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Initialize baseline live fuel price settings ONLY if absent.
                            CoroutineScope(Dispatchers.IO).launch {
                                val database = INSTANCE ?: getInstance(context)
                                if (database.settingsDao().getSettings() == null) {
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
