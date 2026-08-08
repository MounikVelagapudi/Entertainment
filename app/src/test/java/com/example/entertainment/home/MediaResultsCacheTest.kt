package com.example.entertainment.home

import com.example.presentation.presentation.MediaResults
import com.example.presentation.presentation.MediaResultsCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// BUILDER PATTERN UNIT TEST
class MediaResultsCacheTest {

    private fun media(id: Int, title: String = "Inception$id") = MediaResults(
        title = title,
        releaseDate = "2010-07-06",
        imageUrl = "https://example.com/inception.jpg",
        id = id,
        ratings = 8.8
    )

    @Test
    fun `get returns the item after putAll stores it`() {
        // Arrange
        val cache = MediaResultsCache()
        val movie = media(id = 1, title = "Inception")

        // Act
        cache.putAll(listOf(movie))
        val result = cache.get(1)

        // Assert
        assertEquals(movie, result)
    }

    @Test
    fun `get returns null when putAll is called with an empty list`() {
        // Arrange
        val cache = MediaResultsCache()

        // Act
        cache.putAll(emptyList())
        val cachedData = cache.get(1)

        // Assert
        assertNull(cachedData)
    }

    @Test
    fun `get returns correct item for each id when putAll stores multiple items`() {
        // Arrange
        val cache = MediaResultsCache()
        val mockMediaResults = listOf(media(1), media(2), media(3))

        // Act
        cache.putAll(mockMediaResults)

        // Assert
        assertEquals("Inception1", cache.get(1)?.title)
        assertEquals("Inception2", cache.get(2)?.title)
        assertEquals("Inception3", cache.get(3)?.title)
    }

    @Test
    fun `get returns items from both calls when putAll is called twice with different ids`() {
        // Arrange
        val cache = MediaResultsCache()
        val mockMediaResults1 = listOf(media(1))
        val mockMediaResults2 = listOf(media(2))

        // Act
        cache.putAll(mockMediaResults1)
        cache.putAll(mockMediaResults2)

        // Assert
        assertEquals("Inception1", cache.get(1)?.title)
        assertEquals("Inception2", cache.get(2)?.title)
    }

    @Test
    fun `putAll overwrites existing entry when id already present`() {
        // Arrange
        val cache = MediaResultsCache()
        val mockMediaResults1 = listOf(media(id = 1, title = "Inception1"))
        val mockMediaResults2 = listOf(media(id = 1, title = "Inception2"))

        // Act
        cache.putAll(mockMediaResults1)
        cache.putAll(mockMediaResults2)

        // Assert
        assertEquals("Inception2", cache.get(1)?.title)
    }

    @Test
    fun `get returns null on a cache that was never populated`() {
        // Arrange
        val cache = MediaResultsCache()

        // Act
        val result = cache.get(1)

        // Assert
        assertNull(result)
    }
}
