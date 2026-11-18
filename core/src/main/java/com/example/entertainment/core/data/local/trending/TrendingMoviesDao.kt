package com.example.entertainment.core.data.local.trending

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.entertainment.core.data.local.OfflineCacheDao
import com.example.entertainment.core.data.local.toprated.TopRatedMovieEntity
import kotlinx.coroutines.flow.Flow
import androidx.room.Dao

@Dao
interface TrendingMoviesDao : OfflineCacheDao<TrendingMovieEntity> {


    @Query("SELECT * FROM trending_movies ORDER BY sortOrder ASC")
    override fun observeAll(): Flow<List<TrendingMovieEntity>>

    /* Detail screen looks up straight from Room, no separate in-memory cache. */
    @Query("SELECT * FROM trending_movies WHERE id = :id")
    override suspend fun findById(id: Int): TrendingMovieEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movies: List<TrendingMovieEntity>)

    @Query("DELETE FROM trending_movies")
    suspend fun clearAll()

    @Query("SELECT count(*) FROM trending_movies")
    override suspend fun count(): Int

    @Transaction
    override suspend fun addDataToCache(movies: List<TrendingMovieEntity>) {
        insertAll(movies)
    }
}