package com.example.meterreadingsapp.data

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
     * Retrieves meters associated with a specific address, including house number and addition.
     * Parameters are nullable to allow flexible filtering (e.g., get all meters on a street regardless of house number).
     * The query now explicitly handles both NULL and empty string values for nullable parameters.
     * @param address The street address to filter meters by.
     * @param postalCode The postal code to filter meters by (can be null).
     * @param city The city to filter meters by (can be null).
     * @param houseNumber The house number to filter meters by (NEW, can be null).
     * @param houseNumberAddition The house number addition to filter meters by (NEW, can be null).
     * @return A Flow emitting a list of Meter objects matching the given address details.
     */
    @Query("""
        SELECT * FROM meters
        WHERE address = :address
        AND (postal_code = :postalCode OR :postalCode IS NULL OR :postalCode = '')
        AND (city = :city OR :city IS NULL OR :city = '')
        AND (house_number = :houseNumber OR :houseNumber IS NULL OR :houseNumber = '')
        AND (house_number_addition = :houseNumberAddition OR :houseNumberAddition IS NULL OR :houseNumberAddition = '')
    """)
    fun getMetersByAddress(
        address: String,
        postalCode: String?,
        city: String?,
        houseNumber: String?, // FIX: New parameter for filtering
        houseNumberAddition: String? // FIX: New parameter for filtering
    ): Flow<List<Meter>>

    /**
     * Deletes all meters from the database.
     */
    @Query("DELETE FROM meters")
    suspend fun deleteAllMeters()

    /**
     * Deletes meters associated with a specific address, including house number and addition, from the database.
     * Parameters are nullable to allow flexible deletion based on available detail.
     * The query now explicitly handles both NULL and empty string values for nullable parameters.
     * @param address The street address of meters to delete.
     * @param postalCode The postal code of meters to delete (can be null).
     * @param city The city of meters to delete (can be null).
     * @param houseNumber The house number of meters to delete (NEW, can be null).
     * @param houseNumberAddition The house number addition of meters to delete (NEW, can be null).
     */
    @Query("""
        DELETE FROM meters
        WHERE address = :address
        AND (postal_code = :postalCode OR :postalCode IS NULL OR :postalCode = '')
        AND (city = :city OR :city IS NULL OR :city = '')
        AND (house_number = :houseNumber OR :houseNumber IS NULL OR :houseNumber = '')
        AND (house_number_addition = :houseNumberAddition OR :houseNumberAddition IS NULL OR :houseNumberAddition = '')
    """)
    suspend fun deleteMetersByAddress(
        address: String,
        postalCode: String?,
        city: String?,
        houseNumber: String?, // FIX: New parameter for deletion
        houseNumberAddition: String? // FIX: New parameter for deletion
    )
}
