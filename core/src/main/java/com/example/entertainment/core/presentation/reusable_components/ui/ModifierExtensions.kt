package com.example.entertainment.core.presentation.reusable_components.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
/* Custom conditional modifier, e.g. a border shown only when a card is tappable. */
fun Modifier.conditional(
    condition: Boolean,
    modifier: @Composable Modifier.() -> Modifier
): Modifier {
    return if (condition) {
        then(modifier(Modifier))
    } else {
        this
    }
}
