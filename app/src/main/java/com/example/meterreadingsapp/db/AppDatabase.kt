package com.example.meterreadingsapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.meterreadingsapp.converters.MapConverter
import com.example.meterreadingsapp.converters.ListConverter

/**
 * The Room database class for the application.
 * Defines the database configuration and provides access to the DAOs.
 * @param entities Specifies the entities (tables) included in this database.
 * @param version The version number of the database. **INCREMENT THIS TO 6** due to schema changes.
 * @param exportSchema Set to false to prevent exporting schema to a folder.
 */
@Database(
    entities = [Location::class, Meter::class, Reading::class, QueuedRequest::class], // FIX: Added Location::class and QueuedRequest::class
    version = 13, // FIX: Increment version to 6 for new table/entity changes
    exportSchema = false
)
@TypeConverters(MapConverter::class, ListConverter::class)
abstract class AppDatabase : RoomDatabase() {

    // Abstract function to get the DAO for Location entities.
    abstract fun locationDao(): LocationDao // FIX: Added LocationDao

    // Abstract function to get the DAO for Meter entities.
    abstract fun meterDao(): MeterDao

    // Abstract function to get the DAO for Reading entities.
    abstract fun readingDao(): ReadingDao

    // Abstract function to get the DAO for QueuedRequest entities.
    abstract fun queuedRequestDao(): QueuedRequestDao

    // Companion object to provide a singleton instance of the database.
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
                    .fallbackToDestructiveMigration() // Critical for schema changes without migrations
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
