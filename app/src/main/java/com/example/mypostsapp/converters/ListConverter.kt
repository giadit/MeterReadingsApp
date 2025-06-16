package com.example.mypostsapp.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Room TypeConverters for serializing and deserializing List<String> objects.
 * This allows Room to store lists of strings as JSON strings in the database.
 */
class ListConverter {

    private val gson = Gson()

    /**
     * Converts a List<String>? object to its JSON string representation.
     * This method is used by Room when saving data to the database.
     * @param list The list to convert.
     * @return The JSON string representation of the list, or null if the input list is null.
     */
    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        return gson.toJson(list)
    }

    /**
     * Converts a JSON string back to a List<String>? object.
     * This method is used by Room when reading data from the database.
     * @param jsonString The JSON string to convert.
     * @return The List<String>? object, or null if the input string is null.
     */
    @TypeConverter
    fun toStringList(jsonString: String?): List<String>? {
        if (jsonString == null) {
            return null
        }
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(jsonString, type)
    }
}
