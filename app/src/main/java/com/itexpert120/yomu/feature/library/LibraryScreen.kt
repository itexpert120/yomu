package com.itexpert120.yomu.feature.library

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuAppSurface
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuThemeMode

@Composable
fun YomuLibraryApp(
    onThemeModeChange: (YomuThemeMode) -> Unit = {},
) {
    YomuDesignTheme {
        var themeMode by remember { mutableStateOf<YomuThemeMode?>(null) }
        val context = LocalContext.current

        val safLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
        }

        LibraryScreen(
            onImport = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/epub+zip"
                }
                safLauncher.launch(intent)
            },
            onThemeToggle = {
                themeMode = when (themeMode) {
                    null, YomuThemeMode.Light -> YomuThemeMode.Dark
                    YomuThemeMode.Dark -> YomuThemeMode.Oled
                    YomuThemeMode.Oled -> YomuThemeMode.Light
                }
                onThemeModeChange(themeMode ?: YomuThemeMode.Light)
            },
        )
    }
}

@Composable
fun LibraryScreen(
    onImport: () -> Unit = {},
    onThemeToggle: () -> Unit = {},
) {
    var books by remember { mutableStateOf(allBooks.toList()) }
    var sortMode by remember { mutableStateOf(SortMode.Recent) }
    var groupMode by remember { mutableStateOf(GroupMode.None) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedBook by remember { mutableStateOf<LibraryBook?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }

    val lastRead = books.maxByOrNull { it.lastOpenedAt }
    val gridBooks = if (lastRead != null) {
        books.filter { it.id != lastRead.id }
    } else {
        books
    }

    val filteredBooks = gridBooks.filter { book ->
        if (searchQuery.isBlank()) true
        else book.title.contains(searchQuery, ignoreCase = true) ||
            book.author.contains(searchQuery, ignoreCase = true)
    }

    val sortedBooks = when (sortMode) {
        SortMode.Recent -> filteredBooks.sortedByDescending { it.lastOpenedAt }
        SortMode.Title -> filteredBooks.sortedBy { it.title.lowercase() }
        SortMode.Author -> filteredBooks.sortedBy { it.authorLastName.lowercase() }
        SortMode.Unread -> filteredBooks.sortedBy { it.progress }
    }

    val groupedBooks: Map<String, List<LibraryBook>> = when (groupMode) {
        GroupMode.None -> mapOf("" to sortedBooks)
        GroupMode.Author -> sortedBooks.groupBy { it.authorLastName }
        GroupMode.Series -> sortedBooks.groupBy { it.authorLastName }
    }

    YomuAppSurface {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                LibraryHeader(
                    bookCount = books.size,
                    searchActive = searchActive,
                    searchQuery = searchQuery,
                    sortMode = sortMode,
                    groupMode = groupMode,
                    showSortMenu = showSortMenu,
                    showGroupMenu = showGroupMenu,
                    onSearchToggle = {
                        searchActive = !searchActive
                        if (!searchActive) searchQuery = ""
                    },
                    onSearchQueryChange = { searchQuery = it },
                    onSortModeChange = { sortMode = it; showSortMenu = false },
                    onGroupModeChange = { groupMode = it; showGroupMenu = false },
                    onSortMenuToggle = { showSortMenu = !showSortMenu; showGroupMenu = false },
                    onGroupMenuToggle = { showGroupMenu = !showGroupMenu; showSortMenu = false },
                    onDismissMenus = { showSortMenu = false; showGroupMenu = false },
                    onImport = onImport,
                    onThemeToggle = onThemeToggle,
                )

                LibraryGrid(
                    lastRead = lastRead,
                    groupedBooks = groupedBooks,
                    onBookClick = { selectedBook = it },
                    onBookLongPress = { selectedBook = it },
                    onImport = onImport,
                )
            }

            if (lastRead != null) {
                FloatingResumeButton(
                    modifier = Modifier.align(Alignment.BottomEnd),
                    book = lastRead,
                )
            }

            selectedBook?.let { book ->
                BookContextPanel(
                    book = book,
                    onDismiss = { selectedBook = null },
                    onMarkRead = {
                        books = books.map {
                            if (it.id == book.id) it.copy(progress = 1.0f, remaining = "Finished")
                            else it
                        }
                        selectedBook = null
                    },
                    onRemove = {
                        books = books.filter { it.id != book.id }
                        selectedBook = null
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            SystemBarTopScrim(Modifier.align(Alignment.TopCenter))
            SystemBarBottomScrim(Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun LibraryGrid(
    lastRead: LibraryBook?,
    groupedBooks: Map<String, List<LibraryBook>>,
    onBookClick: (LibraryBook) -> Unit,
    onBookLongPress: (LibraryBook) -> Unit,
    onImport: () -> Unit,
) {
    val contentPadding = libraryContentPadding(horizontal = 16.dp, top = 0.dp, bottom = 100.dp)

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (lastRead != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ContinueReadingCard(book = lastRead)
            }
        }

        groupedBooks.forEach { (groupLabel, groupBooks) ->
            if (groupLabel.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    GroupSectionHeader(title = groupLabel)
                }
            }
            items(groupBooks, key = { it.id }) { book ->
                GridBookCard(
                    book = book,
                    onClick = { onBookClick(book) },
                    onLongPress = { onBookLongPress(book) },
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            ImportEmptyCard(onImport = onImport)
        }
    }
}

@Preview(widthDp = 390, heightDp = 900, showBackground = true)
@Composable
private fun LibraryScreenPreview() {
    YomuDesignTheme {
        LibraryScreen()
    }
}
