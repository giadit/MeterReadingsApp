package com.example.mypostsapp.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mypostsapp.Post
import kotlinx.coroutines.flow.Flow // For observing changes in the database

/**
 * Data Access Object (DAO) for the Post entity.
 * This interface defines the methods for interacting with the 'posts' table in the Room database.
 */
@Dao
interface PostDao {

    /**
     * Inserts a list of posts into the database.
     * If a post with the same primary key (id) already exists, it will be replaced.
     * This is useful for syncing data from an API where you want to update existing entries.
     * @param posts The list of Post objects to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(posts: List<Post>)

    /**
     * Retrieves all posts from the database.
     * Returns a Flow, which is a stream of data that emits new values whenever the data in the database changes.
     * This allows your UI to automatically update when new data is inserted or existing data is modified.
     * @return A Flow emitting a list of all Post objects from the 'posts' table.
     */
    @Query("SELECT * FROM posts")
    fun getAllPosts(): Flow<List<Post>>

    /**
     * Deletes all posts from the database.
     * Useful for clearing old data before syncing new data from an API.
     */
    @Query("DELETE FROM posts")
    suspend fun deleteAllPosts()
}