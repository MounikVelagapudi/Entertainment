package com.example.reviews.domain

import com.example.entertainment.core.domain.NetworkError
import com.example.reviews.testdata.aReview
import com.example.reviews.testdata.reviewsError
import com.example.reviews.testdata.reviewsSuccess
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetMovieReviewsUseCaseTest {

    private val getMovieReviews = GetMovieReviewsUseCaseBuilder()

    @Test
    fun `returns the repo's success as-is`() = runTest {
        val success = reviewsSuccess(aReview(id = "1", author = "a", rating = null, content = "c", createdAt = "d"))
        val useCase = getMovieReviews.withRepoResult(movieId = 7, result = success).build()

        assertEquals(success, useCase(7))
    }

    @Test
    fun `returns the repo's error as-is`() = runTest {
        val error = reviewsError(NetworkError.NoInternetError)
        val useCase = getMovieReviews.withRepoResult(movieId = 7, result = error).build()

        assertEquals(error, useCase(7))
    }
}
