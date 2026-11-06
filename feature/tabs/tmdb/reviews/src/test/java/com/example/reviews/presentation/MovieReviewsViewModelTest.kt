package com.example.reviews.presentation

import app.cash.turbine.test
import com.example.entertainment.core.domain.APIError
import com.example.entertainment.core.domain.APIFailure
import com.example.entertainment.core.domain.NetworkError
import com.example.entertainment.core.domain.NetworkFailure
import com.example.reviews.MainDispatcherRule
import com.example.reviews.testdata.ReviewsResult
import com.example.reviews.testdata.aReview
import com.example.reviews.testdata.reviewsError
import com.example.reviews.testdata.reviewsFailure
import com.example.reviews.testdata.reviewsSuccess
import io.mockk.coVerify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class MovieReviewsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val given = MovieReviewsViewModelBuilder()

    @Test
    fun `starts in Loading`() {
        val viewModel = given.build()

        assertEquals(MovieReviewsUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `load emits Loading then Success with the reviews`() = runTest {
        val reviews = listOf(aReview(id = "a"), aReview(id = "b"))
        val viewModel = given.withResult(movieId = 550, result = reviewsSuccess(*reviews.toTypedArray())).build()

        viewModel.uiState.test {
            assertEquals(MovieReviewsUiState.Loading, awaitItem())
            viewModel.load(550)
            assertEquals(MovieReviewsUiState.Success(reviews), awaitItem())
        }
    }

    @Test
    fun `empty list maps to Empty`() = runTest {
        val viewModel = given.withResult(movieId = 1, result = reviewsSuccess()).build()

        viewModel.load(1)
        advanceUntilIdle()

        assertEquals(MovieReviewsUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `APIError shows only the HTTP code, never the raw server message`() = runTest {
        val rawServerMessage = "Error parsing error response: {\"status_code\":7} Exception: boom"
        val viewModel = given
            .withResult(movieId = 1, result = reviewsError(APIError.UnAuthenticatedError(code = 401, message = rawServerMessage)))
            .build()

        viewModel.load(1)
        advanceUntilIdle()

        val state = viewModel.uiState.value as MovieReviewsUiState.Error
        assertEquals("Couldn't load reviews (401)", state.message)
        assertFalse(state.message.contains("status_code"))
    }

    @Test
    fun `APIFailure shows only the HTTP code`() = runTest {
        val viewModel = given
            .withResult(
                movieId = 1,
                result = reviewsFailure(APIFailure.NotFoundFailure(code = 404, message = "{\"status_message\":\"nope\"}")),
            )
            .build()

        viewModel.load(1)
        advanceUntilIdle()

        assertEquals(MovieReviewsUiState.Error("Couldn't load reviews (404)"), viewModel.uiState.value)
    }

    @Test
    fun `no internet and connection errors show a short offline message`() = runTest {
        val viewModel = given
            .withResult(movieId = 1, result = reviewsError(NetworkError.NoInternetError))
            .withResult(movieId = 2, result = reviewsError(NetworkError.ConnectionError))
            .build()

        viewModel.load(1)
        advanceUntilIdle()
        assertEquals(MovieReviewsUiState.Error("No internet connection"), viewModel.uiState.value)

        viewModel.load(2)
        advanceUntilIdle()
        assertEquals(MovieReviewsUiState.Error("No internet connection"), viewModel.uiState.value)
    }

    @Test
    fun `timeout shows a timed-out message`() = runTest {
        val viewModel = given.withResult(movieId = 1, result = reviewsError(NetworkError.TimeoutError)).build()

        viewModel.load(1)
        advanceUntilIdle()

        assertEquals(MovieReviewsUiState.Error("Request timed out"), viewModel.uiState.value)
    }

    @Test
    fun `IO failure falls back to the generic message`() = runTest {
        val viewModel = given.withResult(movieId = 1, result = reviewsFailure(NetworkFailure.IOFailure)).build()

        viewModel.load(1)
        advanceUntilIdle()

        assertEquals(MovieReviewsUiState.Error("Couldn't load reviews"), viewModel.uiState.value)
    }

    @Test
    fun `repeated load with the same id fetches exactly once`() = runTest {
        val viewModel = given.withResult(movieId = 1, result = reviewsSuccess(aReview())).build()

        viewModel.load(1)
        advanceUntilIdle()
        viewModel.load(1)
        advanceUntilIdle()

        coVerify(exactly = 1) { given.getMovieReviews(1) }
        assertEquals(MovieReviewsUiState.Success(listOf(aReview())), viewModel.uiState.value)
    }

    @Test
    fun `load with a different id fetches again`() = runTest {
        val viewModel = given
            .withResult(movieId = 1, result = reviewsSuccess(aReview(id = "one")))
            .withResult(movieId = 2, result = reviewsSuccess(aReview(id = "two")))
            .build()

        viewModel.load(1)
        advanceUntilIdle()
        viewModel.load(2)
        advanceUntilIdle()

        coVerify(exactly = 1) { given.getMovieReviews(1) }
        coVerify(exactly = 1) { given.getMovieReviews(2) }
        assertEquals(MovieReviewsUiState.Success(listOf(aReview(id = "two"))), viewModel.uiState.value)
    }

    @Test
    fun `retry before any load is a no-op`() = runTest {
        val viewModel = given.build()

        viewModel.retry()
        advanceUntilIdle()

        coVerify(exactly = 0) { given.getMovieReviews(any()) }
        assertEquals(MovieReviewsUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `retry after an error refetches and goes Loading then Success`() = runTest {
        val viewModel = given
            .withResultsInOrder(movieId = 1, reviewsError(NetworkError.NoInternetError), reviewsSuccess(aReview()))
            .build()
        viewModel.load(1)
        advanceUntilIdle()

        viewModel.uiState.test {
            assertEquals(MovieReviewsUiState.Error("No internet connection"), awaitItem())
            viewModel.retry()
            assertEquals(MovieReviewsUiState.Loading, awaitItem())
            assertEquals(MovieReviewsUiState.Success(listOf(aReview())), awaitItem())
        }
        coVerify(exactly = 2) { given.getMovieReviews(1) }
    }

    @Test
    fun `rapid retry cancels the in-flight request so only the last one sets state`() = runTest {
        val first = CompletableDeferred<ReviewsResult>()
        val second = CompletableDeferred<ReviewsResult>()
        val viewModel = given.withPendingResponses(movieId = 1, first, second).build()

        viewModel.load(1)
        runCurrent()
        viewModel.retry()
        runCurrent()

        second.complete(reviewsSuccess(aReview(id = "latest")))
        runCurrent()
        /* Stale first response lands after; its coroutine is already cancelled. */
        first.complete(reviewsSuccess(aReview(id = "stale")))
        advanceUntilIdle()

        assertEquals(MovieReviewsUiState.Success(listOf(aReview(id = "latest"))), viewModel.uiState.value)
        coVerify(exactly = 2) { given.getMovieReviews(1) }
    }

    @Test
    fun `stale response arriving before the retried one does not overwrite Loading`() = runTest {
        val first = CompletableDeferred<ReviewsResult>()
        val second = CompletableDeferred<ReviewsResult>()
        val viewModel = given.withPendingResponses(movieId = 1, first, second).build()

        viewModel.load(1)
        runCurrent()
        viewModel.retry()
        runCurrent()

        first.complete(reviewsError(NetworkError.TimeoutError))
        runCurrent()
        assertEquals(MovieReviewsUiState.Loading, viewModel.uiState.value)

        second.complete(reviewsSuccess())
        advanceUntilIdle()
        assertEquals(MovieReviewsUiState.Empty, viewModel.uiState.value)
    }
}
