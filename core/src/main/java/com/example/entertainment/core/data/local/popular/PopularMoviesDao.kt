package com.example.entertainment.core.data.local.popular

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.entertainment.core.data.local.OfflineCacheDao
import kotlinx.coroutines.flow.Flow

@Dao
interface PopularMoviesDao : OfflineCacheDao<PopularMovieEntity> {

    /* Room keeps this alive -- UI observes the DB, not the network call. */
    @Query("SELECT * FROM popular_movies ORDER BY sortOrder ASC")
    override fun observeAll(): Flow<List<PopularMovieEntity>>

    /* Detail screen looks up straight from Room, no separate in-memory cache. */
    @Query("SELECT * FROM popular_movies WHERE id = :id")
    override suspend fun findById(id: Int): PopularMovieEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movies: List<PopularMovieEntity>)

    @Query("DELETE FROM popular_movies")
    suspend fun clearAll()

    @Query(value = "SELECT COUNT(*) FROM popular_movies")
    override suspend fun count(): Int

    @Transaction
    override suspend fun addDataToCache(entities: List<PopularMovieEntity>) {
        insertAll(entities)
    }
}
