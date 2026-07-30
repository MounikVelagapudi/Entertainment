package com.example.entertainment.home

import com.example.core.BuildConfig
import com.example.entertainment.core.data.networking.dto.toprated.TopRatedDTO
import com.example.entertainment.home.popularMovies.data.PopularMovieDTO
import com.example.entertainment.home.trendingMovies.data.TrendingMovieDTO
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface HomeScreenApi {


    // curl "https://api.themoviedb.org/3/trending/movie/week?api_key=YOUR_KEY"
    @GET("3/trending/movie/week")
    suspend fun getTrendingMovies(@Query(value = "api_key") api_key: String = BuildConfig.TMDB_API_KEY): Response<TrendingMovieDTO>

   // curl "https://api.themoviedb.org/3/movie/popular?api_key=ffd54cd8a5b8167771a06dbae51d5bb5&language=en-US&page=1"
    @GET("3/movie/popular")
    suspend fun getPopularMovies(
        @Query(value = "api_key") api_key: String = BuildConfig.TMDB_API_KEY,
        @Query(value = "language") language: String = "en-US",
        @Query(value = "page") page: Int = 1,
    ): Response<PopularMovieDTO>


    // curl "https://api.themoviedb.org/3/movie/top_rated?api_key=ffd54cd8a5b8167771a06dbae51d5bb5&language=en-US&page=1"
    @GET("3/movie/top_rated")
    suspend fun getTopRatedMovie(
        @Query(value = "api_key") api_key: String = BuildConfig.TMDB_API_KEY,
        @Query(value = "language") language: String = "en-US",
        @Query(value = "page") page: Int = 1,
    ): Response<TopRatedDTO>

}