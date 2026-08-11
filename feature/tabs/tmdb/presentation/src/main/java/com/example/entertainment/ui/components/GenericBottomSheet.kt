package com.example.entertainment.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.presentation.presentation.MediaDetailBottomSheetContent
import com.example.entertainment.ui.preview.PreviewConstants

/**
 * Shows [content] for [item] in a modal bottom sheet whenever [item] is non-null,
 * and calls [onDismiss] when the sheet is closed. Not tied to any specific model —
 * pair it with a `selectedX: StateFlow<X?>` + `dismissX()` in a ViewModel to reuse
 * this for any "tap a UI component to see its detail" flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> GenericBottomSheet(
    item: T?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    if (item != null) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            modifier = modifier
        ) {
            content(item)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GenericBottomSheetPreview() {
    GenericBottomSheet(
        item = PreviewConstants.previewMovie,
        onDismiss = {}
    ) { media ->
        MediaDetailBottomSheetContent(media = media)
    }
}
