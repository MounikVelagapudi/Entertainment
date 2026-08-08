package com.example.presentation.presentation

import androidx.compose.runtime.Immutable

// @Immutable is a promise to the Compose compiler: "once built, nothing
// inside this reachable from these properties will ever change." Without
// it, MediaUIModel is inferred UNSTABLE -- not because of totalResults/
// totalPages (Int is always stable), but because of `results: List<MediaResults>`.
// `List` is an interface (could be a mutable ArrayList under the hood), and
// the compiler can't prove at compile time that whoever hands it a List
// won't mutate it later -- so it conservatively marks the whole type
// unstable. An unstable parameter means any composable receiving a
// MediaUIModel (e.g. MediaComposableUI) loses the ability to SKIP
// recomposition, even when you pass the exact same instance again.
//
// Verify this yourself: remove the @Immutable below, run
// `./gradlew :app:assembleDebug`, and check
// app/build/compose_metrics/*-classes.txt -- MediaUIModel will flip from
// "stable" back to "unstable: contains unstable field 'results'".
@Immutable
data class MediaUIModel(
    val totalResults: Int,
    val totalPages: Int,
    val results: List<MediaResults>
)

// All fields here are already compiler-stable primitives (Int, Double) and
// String, so MediaResults is inferred stable even without @Immutable. It's
// added anyway to make the invariant explicit for readers, and because a
// stable *leaf* type is what lets MediaUIModel's @Immutable claim actually
// hold -- @Immutable on the parent doesn't help if the items it holds are
// themselves mutable.
@Immutable
data class MediaResults(
    val title: String,
    val releaseDate: String,
    val imageUrl: String,
    val id: Int,
    val ratings: Double,
    val overview: String? = null
)
