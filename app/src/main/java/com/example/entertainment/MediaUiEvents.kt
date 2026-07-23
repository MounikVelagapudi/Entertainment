package com.example.entertainment

import com.example.entertainment.home.MediaResults

sealed interface MediaUiEvents {

    data class OnTapOfMovieCard(val media: MediaResults): MediaUiEvents

    data class OnTapOfRating(val id: Double): MediaUiEvents

    data class OnTapOfDisclosure(val media: MediaResults) : MediaUiEvents
}