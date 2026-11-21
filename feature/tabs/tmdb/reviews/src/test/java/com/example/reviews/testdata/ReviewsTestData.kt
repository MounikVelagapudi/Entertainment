package com.example.reviews.testdata

import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.domain.TRPError
import com.example.entertainment.core.domain.TRPFailure
import com.example.reviews.data.AuthorDetailsDTO
import com.example.reviews.data.ReviewResultDTO
import com.example.reviews.data.ReviewsDTO
import com.example.reviews.domain.MovieReview

/* Test data builders for reviews: network DTOs, domain model, and the use case's Result type. */

typealias ReviewsResult = Result<List<MovieReview>, TRPError>

fun aReview(
    id: String = "r1",
    author: String = "author",
    rating: Double? = 7.5,
    content: String = "content",
    createdAt: String = "2024-01-01",
) = MovieReview(id = id, author = author, rating = rating, content = content, createdAt = createdAt)

fun aReviewDto(
    id: String? = "r1",
    author: String? = null,
    authorDetails: AuthorDetailsDTO? = null,
    content: String? = null,
    createdAt: String? = null,
) = ReviewResultDTO(
    id = id,
    author = author,
    authorDetails = authorDetails,
    content = content,
    createdAt = createdAt,
)

fun anAuthorDetailsDto(username: String? = null, rating: Double? = null) =
    AuthorDetailsDTO(username = username, rating = rating)

fun aReviewsDto(vararg rows: ReviewResultDTO?, movieId: Int? = null) =
    ReviewsDTO(id = movieId, results = rows.toList())

/* TMDB can omit "results" entirely. */
fun aReviewsDtoWithoutResults() = ReviewsDTO(results = null)

fun reviewsSuccess(vararg reviews: MovieReview): ReviewsResult = Result.Success(reviews.toList())

fun reviewsError(error: TRPError): ReviewsResult = Result.Error(error)

fun reviewsFailure(failure: TRPFailure): ReviewsResult = Result.Failure(failure)

/* TMDB's real 404 body; its shape doesn't match core's ErrorResponse. */
const val TMDB_NOT_FOUND_BODY =
    """{"success":false,"status_code":34,"status_message":"The resource you requested could not be found."}"""
