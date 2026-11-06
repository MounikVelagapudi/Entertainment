package com.example.entertainment.core.data.local.toprated

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.entertainment.core.data.local.OfflineCacheDao
import kotlinx.coroutines.flow.Flow

@Dao
interface TopRatedDao : OfflineCacheDao<TopRatedMovieEntity> {

    /* Room keeps this alive -- UI observes the DB, not the network call. */
    @Query("SELECT * FROM top_rated_movies ORDER BY sortOrder ASC")
    override fun observeAll(): Flow<List<TopRatedMovieEntity>>

    /* Detail screen looks up straight from Room, no separate in-memory cache. */
    @Query("SELECT * FROM top_rated_movies WHERE id = :id")
    override suspend fun findById(id: Int): TopRatedMovieEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movies: List<TopRatedMovieEntity>)

    @Query("DELETE FROM top_rated_movies")
    suspend fun clearAll()

    @Query("SELECT count(*) FROM top_rated_movies")
    override suspend fun count(): Int

    @Transaction
    override suspend fun addDataToCache(movies: List<TopRatedMovieEntity>) {
        insertAll(movies)
    }
}
