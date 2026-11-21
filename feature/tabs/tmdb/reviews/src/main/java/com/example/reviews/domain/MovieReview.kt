package com.example.reviews.domain

data class MovieReview(
    val id: String,
    val author: String,
    val rating: Double?,
    val content: String,
    val createdAt: String,
)
