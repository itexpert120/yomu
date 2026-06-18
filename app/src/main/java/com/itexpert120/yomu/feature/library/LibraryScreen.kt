package com.itexpert120.yomu.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuAppSurface
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuThemeMode

@Composable
fun YomuLibraryApp(
    onThemeModeChange: (YomuThemeMode) -> Unit = {},
) {
    LaunchedEffect(Unit) {
        onThemeModeChange(YomuThemeMode.Light)
    }
    YomuDesignTheme(themeMode = YomuThemeMode.Light) {
        LibraryScreen()
    }
}

@Composable
fun LibraryScreen() {
    var selectedBook by remember { mutableStateOf<LibraryBook?>(null) }
    YomuAppSurface {
        Box(Modifier.fillMaxSize()) {
            LibraryContent(onBookLongPress = { selectedBook = it })
            FloatingResumeButton(
                modifier = Modifier.align(Alignment.BottomEnd),
                book = activeBook,
            )
            selectedBook?.let { book ->
                BookContextPanel(
                    book = book,
                    onDismiss = { selectedBook = null },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            SystemBarTopScrim(Modifier.align(Alignment.TopCenter))
            SystemBarBottomScrim(Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun LibraryContent(
    onBookLongPress: (LibraryBook) -> Unit,
) {
    val contentPadding = libraryContentPadding(horizontal = 20.dp, top = 0.dp, bottom = 28.dp)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(30.dp),
    ) {
        item { LibraryHeader() }
        item { ContinueReadingSection(onBookLongPress = onBookLongPress) }
        item { ShelfSection(title = "Unread", books = unreadBooks, onBookLongPress = onBookLongPress) }
        item { ShelfSection(title = "Recently added", books = recentBooks, onBookLongPress = onBookLongPress) }
    }
}

@Preview(widthDp = 390, heightDp = 900, showBackground = true)
@Composable
private fun LibraryScreenPreview() {
    YomuDesignTheme(themeMode = YomuThemeMode.Light) {
        LibraryScreen()
    }
}
