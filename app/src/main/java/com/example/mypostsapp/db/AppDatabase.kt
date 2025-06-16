package com.example.mypostsapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters // Import TypeConverters annotation
import com.example.mypostsapp.converters.MapConverter // Import your MapConverter
import com.example.mypostsapp.converters.ListConverter // Import your ListConverter

/**
 * The Room database for the application.
 * Defines the entities (tables) and the DAOs (data access objects).
 *
 * Version 5: Added Location entity and LocationDao, and registered TypeConverters.
 */
@Database(
    entities = [Location::class, Meter::class, Reading::class], // FIX: Added Location::class
    version = 5, // IMPORTANT: Increment the version number due to schema changes
    exportSchema = false // Set to false for simplicity in development, true for production migrations
)
@TypeConverters(MapConverter::class, ListConverter::class) // FIX: Registered both TypeConverters
abstract class AppDatabase : RoomDatabase() {

    // Abstract methods to get instances of the DAOs
    abstract fun locationDao(): LocationDao // Existing DAO for locations
    abstract fun meterDao(): MeterDao     // Existing DAO for meters
    abstract fun readingDao(): ReadingDao   // Existing DAO for readings

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Returns a singleton instance of the AppDatabase.
         * If the instance does not exist, it creates one.
         * @param context The application context.
         * @return The singleton AppDatabase instance.
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database" // Database name
                )
                    // FallbackToDestructiveMigration will wipe and recreate the database
                    // if the schema version changes. In a real app, you'd use proper migrations.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
