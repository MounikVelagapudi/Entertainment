package com.example.entertainment.home.omdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OmDbDTO(
    @SerialName("Actors")
    val actors: String? = null,
    @SerialName("Awards")
    val awards: String? = null,
    @SerialName("BoxOffice")
    val boxOffice: String? = null,
    @SerialName("Country")
    val country: String? = null,
    @SerialName("DVD")
    val dVD: String? = null,
    @SerialName("Director")
    val director: String? = null,
    @SerialName("Genre")
    val genre: String? = null,
    @SerialName("imdbID")
    val imdbID: String? = null,
    @SerialName("imdbRating")
    val imdbRating: String? = null,
    @SerialName("imdbVotes")
    val imdbVotes: String? = null,
    @SerialName("Language")
    val language: String? = null,
    @SerialName("Metascore")
    val metascore: String? = null,
    @SerialName("Plot")
    val plot: String? = null,
    @SerialName("Poster")
    val poster: String? = null,
    @SerialName("Production")
    val production: String? = null,
    @SerialName("Rated")
    val rated: String? = null,
    @SerialName("Ratings")
    val ratings: List<RatingX>? = null,
    @SerialName("Released")
    val released: String? = null,
    @SerialName("Response")
    val response: String? = null,
    @SerialName("Runtime")
    val runtime: String? = null,
    @SerialName("Title")
    val title: String? = null,
    @SerialName("Type")
    val type: String? = null,
    @SerialName("Website")
    val website: String? = null,
    @SerialName("Writer")
    val writer: String? = null,
    @SerialName("Year")
    val year: String? = null
)
