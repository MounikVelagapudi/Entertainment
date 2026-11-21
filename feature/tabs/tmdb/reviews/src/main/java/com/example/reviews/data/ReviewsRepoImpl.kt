package com.example.reviews.data

import com.example.entertainment.core.data.networking.mappedApiCall
import com.example.entertainment.core.di.IoDispatcher
import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.domain.TRPError
import com.example.reviews.domain.MovieReview
import com.example.reviews.domain.ReviewsRepo
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher

class ReviewsRepoImpl @Inject constructor(
    private val reviewsApi: ReviewsApi,
    private val reviewsDataMapper: ReviewsDataMapper,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ReviewsRepo {

    override suspend fun getMovieReviews(movieId: Int): Result<List<MovieReview>, TRPError> =
        mappedApiCall(
            networkDispatcher = ioDispatcher,
            mapper = reviewsDataMapper,
            execute = { reviewsApi.getMovieReviews(movieId = movieId) }
        )
}
