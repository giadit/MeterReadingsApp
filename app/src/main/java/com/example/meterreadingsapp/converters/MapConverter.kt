package com.example.meterreadingsapp.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Room TypeConverters for serializing and deserializing Map<String, Any?> objects.
 * This allows Room to store complex map structures as JSON strings in the database.
 */
class MapConverter {

    private val gson = Gson()

    /**
     * Converts a Map<String, Any?> object to its JSON string representation.
     * This method is used by Room when saving data to the database.
     * @param map The map to convert.
     * @return The JSON string representation of the map, or null if the input map is null.
     */
    @TypeConverter
    fun fromMap(map: Map<String, Any?>?): String? {
        return gson.toJson(map)
    }

    /**
     * Converts a JSON string back to a Map<String, Any?> object.
     * This method is used by Room when reading data from the database.
     * @param jsonString The JSON string to convert.
     * @return The Map<String, Any?> object, or null if the input string is null.
     */
    @TypeConverter
    fun toMap(jsonString: String?): Map<String, Any?>? {
        if (jsonString == null) {
            return null
        }
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        return gson.fromJson(jsonString, type)
    }
}
