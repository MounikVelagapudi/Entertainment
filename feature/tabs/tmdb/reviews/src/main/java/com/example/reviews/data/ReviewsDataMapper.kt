package com.example.reviews.data

import com.example.entertainment.core.domain.Mapper
import com.example.reviews.domain.MovieReview
import javax.inject.Inject

class ReviewsDataMapper @Inject constructor() : Mapper<ReviewsDTO, List<MovieReview>> {

    override suspend fun map(input: ReviewsDTO): List<MovieReview> =
        input.results?.mapNotNull { mapReviewToDomain(it) } ?: emptyList()

    /* No id means no stable LazyColumn key, so the row is dropped. */
    private fun mapReviewToDomain(result: ReviewResultDTO?): MovieReview? {
        if (result == null) return null
        val reviewId = result.id ?: return null

        return MovieReview(
            id = reviewId,
            author = result.author ?: result.authorDetails?.username ?: "",
            rating = result.authorDetails?.rating,
            content = result.content ?: "",
            createdAt = result.createdAt ?: "",
        )
    }
}
