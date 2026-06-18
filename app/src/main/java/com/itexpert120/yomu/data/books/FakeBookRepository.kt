package com.itexpert120.yomu.data.books

import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.core.model.BookId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory book source seeded with sample data. State lives in a [MutableStateFlow] so library
 * mutations (mark read / remove) reactively update every observer. A single instance should be
 * shared app-wide (created in the app graph) so edits persist for the process lifetime.
 */
class FakeBookRepository : BookRepository {

    private val books = MutableStateFlow(sampleBooks)

    override fun observeBooks(): Flow<List<Book>> = books.asStateFlow()

    override fun observeBook(id: BookId): Flow<Book?> =
        books.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun markRead(id: BookId) = books.update { list ->
        list.map { if (it.id == id) it.copy(progress = 1f, remainingLabel = "Finished") else it }
    }

    override suspend fun remove(id: BookId) = books.update { list ->
        list.filterNot { it.id == id }
    }
}

private val sampleBooks = listOf(
    Book(
        id = BookId("1"),
        title = "The Left Hand of Darkness",
        author = "Ursula K. Le Guin",
        series = "Hainish Cycle",
        progress = 0.42f,
        remainingLabel = "3h 12m left",
        coverPalette = listOf(0xFF17202B, 0xFF53697E),
        lastOpenedAt = 7L,
    ),
    Book(
        id = BookId("2"),
        title = "Deep Work",
        author = "Cal Newport",
        progress = 0.0f,
        remainingLabel = "Unread",
        coverPalette = listOf(0xFF263A30, 0xFF587A5F),
    ),
    Book(
        id = BookId("3"),
        title = "Designing Type",
        author = "Karen Cheng",
        progress = 0.0f,
        remainingLabel = "Unread",
        coverPalette = listOf(0xFF35211E, 0xFF9B5948),
    ),
    Book(
        id = BookId("4"),
        title = "Invisible Cities",
        author = "Italo Calvino",
        progress = 0.0f,
        remainingLabel = "Unread",
        coverPalette = listOf(0xFF352D46, 0xFF8D7BA8),
    ),
    Book(
        id = BookId("5"),
        title = "The Dispossessed",
        author = "Ursula K. Le Guin",
        series = "Hainish Cycle",
        progress = 0.0f,
        remainingLabel = "Unread",
        coverPalette = listOf(0xFF2E2E2E, 0xFF9C8B6C),
    ),
    Book(
        id = BookId("6"),
        title = "A Philosophy of Software Design",
        author = "John Ousterhout",
        progress = 0.18f,
        remainingLabel = "5h left",
        coverPalette = listOf(0xFF202830, 0xFF6A7D8E),
        lastOpenedAt = 5L,
    ),
    Book(
        id = BookId("7"),
        title = "The Passenger",
        author = "Cormac McCarthy",
        progress = 0.73f,
        remainingLabel = "1h left",
        coverPalette = listOf(0xFF2F2621, 0xFFA76F55),
        lastOpenedAt = 3L,
    ),
    Book(
        id = BookId("8"),
        title = "Thinking in Systems",
        author = "Donella Meadows",
        progress = 0.31f,
        remainingLabel = "4h left",
        coverPalette = listOf(0xFF24352B, 0xFF89A179),
        lastOpenedAt = 4L,
    ),
    Book(
        id = BookId("9"),
        title = "Sapiens",
        author = "Yuval Noah Harari",
        progress = 0.55f,
        remainingLabel = "2h left",
        coverPalette = listOf(0xFF2A1F1A, 0xFFB08968),
        lastOpenedAt = 2L,
    ),
    Book(
        id = BookId("10"),
        title = "Project Hail Mary",
        author = "Andy Weir",
        progress = 0.88f,
        remainingLabel = "30m left",
        coverPalette = listOf(0xFF1A1A2E, 0xFF6C63FF),
        lastOpenedAt = 1L,
    ),
    Book(
        id = BookId("11"),
        title = "Atomic Habits",
        author = "James Clear",
        progress = 0.0f,
        remainingLabel = "Unread",
        coverPalette = listOf(0xFF1B3A2D, 0xFF4CAF50),
    ),
    Book(
        id = BookId("12"),
        title = "The Name of the Wind",
        author = "Patrick Rothfuss",
        progress = 0.65f,
        remainingLabel = "1h 30m left",
        coverPalette = listOf(0xFF2C1810, 0xFFC17817),
        lastOpenedAt = 6L,
    ),
)
