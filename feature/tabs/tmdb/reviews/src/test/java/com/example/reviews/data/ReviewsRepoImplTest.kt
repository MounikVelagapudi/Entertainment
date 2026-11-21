package com.example.reviews.data

import com.example.entertainment.core.domain.APIError
import com.example.entertainment.core.domain.APIFailure
import com.example.entertainment.core.domain.NetworkError
import com.example.entertainment.core.domain.NetworkFailure
import com.example.entertainment.core.domain.Result
import com.example.reviews.testdata.TMDB_NOT_FOUND_BODY
import com.example.reviews.testdata.aReviewDto
import com.example.reviews.testdata.aReviewsDto
import io.mockk.coVerify
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewsRepoImplTest {

    private val given = ReviewsRepoBuilder()

    @Test
    fun `success maps the body through the mapper and requests the given movie id`() = runTest {
        val repo = given
            .withReviews(movieId = 550, dto = aReviewsDto(aReviewDto(id = "r1", author = "a"), null))
            .build(testScheduler)

        val result = repo.getMovieReviews(550)

        assertTrue(result is Result.Success)
        assertEquals(listOf("r1"), (result as Result.Success).data.map { it.id })
        coVerify(exactly = 1) { given.reviewsApi.getMovieReviews(movieId = 550) }
    }

    @Test
    fun `success with no results is an empty list`() = runTest {
        val repo = given.withReviews(movieId = 1, dto = aReviewsDto()).build(testScheduler)

        val result = repo.getMovieReviews(1)

        assertEquals(Result.Success<List<Any>, Nothing>(emptyList()), result)
    }

    @Test
    fun `404 with TMDB's error body maps to NotFoundFailure carrying the HTTP code`() = runTest {
        val repo = given.withHttpError(movieId = 1, code = 404, body = TMDB_NOT_FOUND_BODY).build(testScheduler)

        val result = repo.getMovieReviews(1)

        assertTrue(result is Result.Failure)
        val failure = (result as Result.Failure).failure
        assertTrue(failure is APIFailure.NotFoundFailure)
        assertEquals(404, (failure as APIFailure).code)
    }

    @Test
    fun `401 maps to UnAuthenticatedError`() = runTest {
        val repo = given.withHttpError(movieId = 1, code = 401, body = TMDB_NOT_FOUND_BODY).build(testScheduler)

        val result = repo.getMovieReviews(1)

        assertTrue(result is Result.Error)
        val error = (result as Result.Error).error
        assertTrue(error is APIError.UnAuthenticatedError)
        assertEquals(401, (error as APIError).code)
    }

    @Test
    fun `unknown host is retried then maps to NoInternetError`() = runTest {
        val repo = given.withRequestThrowing(movieId = 1, error = UnknownHostException()).build(testScheduler)

        val result = repo.getMovieReviews(1)

        assertEquals(Result.Error<Any, NetworkError>(NetworkError.NoInternetError), result)
        coVerify(exactly = 3) { given.reviewsApi.getMovieReviews(movieId = 1) }
    }

    @Test
    fun `socket timeout maps to TimeoutError`() = runTest {
        val repo = given.withRequestThrowing(movieId = 1, error = SocketTimeoutException()).build(testScheduler)

        val result = repo.getMovieReviews(1)

        assertEquals(Result.Error<Any, NetworkError>(NetworkError.TimeoutError), result)
    }

    @Test
    fun `generic IO failure maps to IOFailure without retrying`() = runTest {
        val repo = given.withRequestThrowing(movieId = 1, error = IOException("boom")).build(testScheduler)

        val result = repo.getMovieReviews(1)

        assertEquals(Result.Failure<Any, NetworkFailure>(NetworkFailure.IOFailure), result)
        coVerify(exactly = 1) { given.reviewsApi.getMovieReviews(movieId = 1) }
    }

    @Test
    fun `cancelling mid-request propagates instead of mapping to a Result`() = runTest {
        val repo = given.withRequestHanging(movieId = 1).build(testScheduler)
        var result: Any? = null

        val job = launch { result = repo.getMovieReviews(1) }
        runCurrent()
        job.cancel()
        runCurrent()

        assertTrue(job.isCancelled)
        assertNull(result)
    }
}
