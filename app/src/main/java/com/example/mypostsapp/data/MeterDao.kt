package com.example.mypostsapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for the Meter entity.
 * Defines methods for interacting with the 'meters' table in the Room database.
 */
@Dao
interface MeterDao {
    /**
     * Inserts a list of meters into the database.
     * On conflict, existing meters with the same primary key will be replaced.
     * @param meters The list of Meter objects to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(meters: List<Meter>)

    /**
     * Retrieves all meters from the database.
     * @return A Flow emitting a list of all Meter objects from the 'meters' table.
     */
    @Query("SELECT * FROM meters")
    fun getAllMeters(): Flow<List<Meter>>

    /**
     * Retrieves meters associated with a specific address.
     * @param address The street address to filter meters by.
     * @param postalCode The postal code to filter meters by.
     * @param city The city to filter meters by.
     * @return A Flow emitting a list of Meter objects matching the given address details.
     */
    @Query("SELECT * FROM meters WHERE address = :address AND postal_code = :postalCode AND city = :city")
    fun getMetersByAddress(address: String, postalCode: String, city: String): Flow<List<Meter>>

    /**
     * Deletes all meters from the database.
     */
    @Query("DELETE FROM meters")
    suspend fun deleteAllMeters()

    /**
     * Deletes meters associated with a specific address from the database.
     * @param address The street address of meters to delete.
     * @param postalCode The postal code of meters to delete.
     * @param city The city of meters to delete.
     */
    @Query("DELETE FROM meters WHERE address = :address AND postal_code = :postalCode AND city = :city")
    suspend fun deleteMetersByAddress(address: String, postalCode: String, city: String)
}
    