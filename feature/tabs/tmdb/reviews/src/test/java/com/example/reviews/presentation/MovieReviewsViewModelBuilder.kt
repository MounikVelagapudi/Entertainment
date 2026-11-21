package com.example.reviews.presentation

import com.example.reviews.domain.GetMovieReviewsUseCase
import com.example.reviews.testdata.ReviewsResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred

class MovieReviewsViewModelBuilder {

    val getMovieReviews = mockk<GetMovieReviewsUseCase>()

    fun withResult(movieId: Int, result: ReviewsResult) = apply {
        coEvery { getMovieReviews(movieId) } returns result
    }

    /* First call gets the first result, the next call the second, and so on. */
    fun withResultsInOrder(movieId: Int, vararg results: ReviewsResult) = apply {
        coEvery { getMovieReviews(movieId) } returnsMany results.toList()
    }

    /* Each call waits on the next deferred, so the test decides when and in what order responses land. */
    fun withPendingResponses(movieId: Int, vararg pending: CompletableDeferred<ReviewsResult>) = apply {
        val queue = ArrayDeque(pending.toList())
        coEvery { getMovieReviews(movieId) } coAnswers { queue.removeFirst().await() }
    }

    fun build() = MovieReviewsViewModel(getMovieReviews)
}
