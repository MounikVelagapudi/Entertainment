package com.example.entertainment.core.presentation.reusable_components.events

import com.example.entertainment.core.presentation.reusable_components.MediaResults

enum class Section {
    TRENDING, POPULAR, TOP_RATED
}

sealed interface MediaUiEvents {

    data class OnTapOfMovieCard(val media: MediaResults) : MediaUiEvents

    data class OnTapOfRating(val id: Double) : MediaUiEvents

    data class OnTapOfDisclosure(val media: MediaResults) : MediaUiEvents

    data class OnScrolledToEnd(val section: Section) : MediaUiEvents
}