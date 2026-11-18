package com.example.entertainment.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.entertainment.core.data.local.popular.PopularMovieEntity
import com.example.entertainment.core.data.local.popular.PopularMoviesDao
import com.example.entertainment.core.data.local.toprated.TopRatedDao
import com.example.entertainment.core.data.local.toprated.TopRatedMovieEntity
import com.example.entertainment.core.data.local.trending.TrendingMoviesDao
import com.example.entertainment.core.data.local.trending.TrendingMovieEntity

/**
 * App-wide Room database. As other sections (Popular, Trending, ...) get the same
 * offline-first treatment, add their @Entity to `entities` and their @Dao here --
 * one database, one abstract fun per Dao.
 */
@Database(
    entities = [TopRatedMovieEntity::class, PopularMovieEntity::class, TrendingMovieEntity::class],
    version = 7,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabaseTopRated : RoomDatabase() {
    abstract fun topRatedDao(): TopRatedDao
    abstract fun popularMoviesDao(): PopularMoviesDao
    abstract fun trendingMoviesDao(): TrendingMoviesDao
}