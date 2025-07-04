package com.example.meterreadingsapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.meterreadingsapp.converters.ListConverter
import com.example.meterreadingsapp.converters.MapConverter

/**
 * Room Database class for the Meter Readings App.
 * Defines the database configuration, including entities, DAOs, and type converters.
 *
 * Version 5: Added FileMetadata entity and FileMetadataDao.
 */
@Database(
    entities = [
        Project::class,
        Location::class,
        Meter::class,
        Reading::class,
        QueuedRequest::class,
        FileMetadata::class // NEW: Added FileMetadata entity
    ],
    version = 5, // INCREMENTED VERSION: From 4 to 5 due to adding FileMetadata entity
    exportSchema = false // Set to false for simplicity, but consider true for real apps with schema export
)
@TypeConverters(ListConverter::class, MapConverter::class) // Apply type converters for lists and maps
abstract class AppDatabase : RoomDatabase() {

    // Abstract methods to provide DAOs
    abstract fun projectDao(): ProjectDao
    abstract fun locationDao(): LocationDao
    abstract fun meterDao(): MeterDao
    abstract fun readingDao(): ReadingDao
    abstract fun queuedRequestDao(): QueuedRequestDao
    abstract fun fileMetadataDao(): FileMetadataDao // NEW: Abstract method for FileMetadataDao

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
                    // For simplicity, we are allowing destructive migrations.
                    // In a production app, you would implement proper migration strategies.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
