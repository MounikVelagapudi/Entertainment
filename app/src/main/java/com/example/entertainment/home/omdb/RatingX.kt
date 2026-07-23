package com.example.entertainment.home.omdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RatingX(
    @SerialName("Source")
    val source: String? = null,
    @SerialName("Value")
    val value: String? = null
)
