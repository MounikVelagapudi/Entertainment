package com.example.reviews.data

import com.example.core.BuildConfig
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ReviewsApi {

    /* curl "https://api.themoviedb.org/3/movie/{movie_id}/reviews?api_key=YOUR_KEY&language=en-US&page=1" */
    @GET("3/movie/{movie_id}/reviews")
    suspend fun getMovieReviews(
        @Path("movie_id") movieId: Int,
        @Query(value = "api_key") api_key: String = BuildConfig.TMDB_API_KEY,
        @Query(value = "language") language: String = "en-US",
        @Query(value = "page") page: Int = 1,
    ): Response<ReviewsDTO>
}
