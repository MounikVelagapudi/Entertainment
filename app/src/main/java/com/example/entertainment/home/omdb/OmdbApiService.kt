package com.example.entertainment.home.omdb

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query


interface OmdbApiService {

  //  @GET("https://www.omdbapi.com/")
    @GET("https://www.omdbapi.com/?i=tt3896198&apikey=2094b96")
    suspend fun getOmdbData(/*
        @Query("i") imdbId: String = "tt3896198",
        @Query("apikey") apikey: String = "2094b96"*/
    ): Response<OmDbDTO>
}
