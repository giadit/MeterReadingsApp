package com.example.meterreadingsapp

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a Post item fetched from the API and stored in the local database.
 * @param id The unique identifier of the post. This will be used as the primary key for Room.
 * @param userId The ID of the user who created the post.
 * @param title The title of the post.
 * @param body The main content/body of the post.
 */
// @Entity annotation marks this class as a table in the Room database.
// The tableName property specifies the name of the table.
@Entity(tableName = "posts")
data class Post(
    // @PrimaryKey annotation designates 'id' as the primary key for the table.
    // autoGenerate = false means we expect the API to provide a unique ID.
    @PrimaryKey(autoGenerate = false)
    val id: Int,
    val userId: Int,
    val title: String,
    val body: String
)
