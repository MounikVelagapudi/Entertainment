package com.example.reviews.data

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

class ReviewsRepoBuilder {

    val reviewsApi = mockk<ReviewsApi>()

    fun withReviews(movieId: Int, dto: ReviewsDTO) = apply {
        coEvery { reviewsApi.getMovieReviews(movieId = movieId) } returns Response.success(dto)
    }

    fun withHttpError(movieId: Int, code: Int, body: String) = apply {
        coEvery { reviewsApi.getMovieReviews(movieId = movieId) } returns Response.error(code, body.toResponseBody())
    }

    fun withRequestThrowing(movieId: Int, error: Throwable) = apply {
        coEvery { reviewsApi.getMovieReviews(movieId = movieId) } throws error
    }

    /* Request never completes until cancelled. */
    fun withRequestHanging(movieId: Int) = apply {
        coEvery { reviewsApi.getMovieReviews(movieId = movieId) } coAnswers { awaitCancellation() }
    }

    /* Pass runTest's scheduler so safeCall's retry delays run on virtual time. */
    fun build(scheduler: TestCoroutineScheduler) = ReviewsRepoImpl(
        reviewsApi = reviewsApi,
        reviewsDataMapper = ReviewsDataMapper(),
        ioDispatcher = StandardTestDispatcher(scheduler),
    )
}
