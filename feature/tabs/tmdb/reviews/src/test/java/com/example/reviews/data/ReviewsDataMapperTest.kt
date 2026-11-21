package com.example.reviews.data

import com.example.reviews.testdata.aReview
import com.example.reviews.testdata.aReviewDto
import com.example.reviews.testdata.aReviewsDto
import com.example.reviews.testdata.aReviewsDtoWithoutResults
import com.example.reviews.testdata.anAuthorDetailsDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewsDataMapperTest {

    private val mapper = ReviewsDataMapper()

    @Test
    fun `maps a full review row to the domain model`() = runTest {
        val dto = aReviewsDto(
            aReviewDto(
                id = "5b1c13b9c3a36848f2026384",
                author = "Goddard",
                authorDetails = anAuthorDetailsDto(username = "goddard", rating = 8.0),
                content = "Great film.",
                createdAt = "2018-06-09T17:51:53.359Z",
            ),
            movieId = 550,
        )

        val reviews = mapper.map(dto)

        assertEquals(
            listOf(
                aReview(
                    id = "5b1c13b9c3a36848f2026384",
                    author = "Goddard",
                    rating = 8.0,
                    content = "Great film.",
                    createdAt = "2018-06-09T17:51:53.359Z",
                )
            ),
            reviews,
        )
    }

    @Test
    fun `null results maps to an empty list`() = runTest {
        assertTrue(mapper.map(aReviewsDtoWithoutResults()).isEmpty())
    }

    @Test
    fun `empty results maps to an empty list`() = runTest {
        assertTrue(mapper.map(aReviewsDto()).isEmpty())
    }

    @Test
    fun `null rows and rows without an id are dropped`() = runTest {
        val dto = aReviewsDto(
            null,
            aReviewDto(id = null, author = "no id"),
            aReviewDto(id = "kept", author = "a"),
        )

        val reviews = mapper.map(dto)

        assertEquals(listOf("kept"), reviews.map { it.id })
    }

    @Test
    fun `author falls back to author_details username`() = runTest {
        val dto = aReviewsDto(
            aReviewDto(id = "1", author = null, authorDetails = anAuthorDetailsDto(username = "fallback"))
        )

        assertEquals("fallback", mapper.map(dto).single().author)
    }

    @Test
    fun `missing optional fields default to empty strings and null rating`() = runTest {
        val review = mapper.map(aReviewsDto(aReviewDto(id = "1"))).single()

        assertEquals(aReview(id = "1", author = "", rating = null, content = "", createdAt = ""), review)
    }
}
