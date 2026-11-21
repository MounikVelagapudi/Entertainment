package com.example.reviews.domain

import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.domain.TRPError

interface ReviewsRepo {

    suspend fun getMovieReviews(movieId: Int): Result<List<MovieReview>, TRPError>
}
