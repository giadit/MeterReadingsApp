package com.example.meterreadingsapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.meterreadingsapp.converters.ListConverter
import com.example.meterreadingsapp.converters.MapConverter

/**
 * The Room database class for the application.
 * Defines the database configuration and provides access to the DAOs.
 */
@Database(
    entities = [
        Project::class,
        Building::class, // Location::class has been replaced by Building::class
        Meter::class,
        Reading::class,
        QueuedRequest::class,
        // ADDED: New entities for OBIS structure
        ObisCode::class,
        MeterObis::class
    ],
    version = 20, // INCREMENTED VERSION (from 19) to trigger migration
    exportSchema = false
)
@TypeConverters(MapConverter::class, ListConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
    abstract fun buildingDao(): BuildingDao
    abstract fun meterDao(): MeterDao
    abstract fun readingDao(): ReadingDao
    abstract fun queuedRequestDao(): QueuedRequestDao
    // ADDED: New DAOs for OBIS structure
    abstract fun obisCodeDao(): ObisCodeDao
    abstract fun meterObisDao(): MeterObisDao
    // The locationDao() function has been removed.

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "meter_readings_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
