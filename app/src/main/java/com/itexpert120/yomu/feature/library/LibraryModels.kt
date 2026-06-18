package com.itexpert120.yomu.feature.library

import androidx.compose.ui.graphics.Color

internal data class LibraryBook(
    val title: String,
    val shortTitle: String,
    val author: String,
    val authorLastName: String,
    val progress: Float,
    val remaining: String,
    val coverColors: List<Color>,
)

internal val activeBook = LibraryBook(
    title = "The Left Hand of Darkness",
    shortTitle = "Left Hand\nof Darkness",
    author = "Ursula K. Le Guin",
    authorLastName = "LE GUIN",
    progress = 0.42f,
    remaining = "3h 12m left",
    coverColors = listOf(Color(0xFF17202B), Color(0xFF53697E)),
)

internal val unreadBooks = listOf(
    LibraryBook(
        title = "Deep Work",
        shortTitle = "Deep\nWork",
        author = "Cal Newport",
        authorLastName = "NEWPORT",
        progress = 0.0f,
        remaining = "Unread",
        coverColors = listOf(Color(0xFF263A30), Color(0xFF587A5F)),
    ),
    LibraryBook(
        title = "Designing Type",
        shortTitle = "Designing\nType",
        author = "Karen Cheng",
        authorLastName = "CHENG",
        progress = 0.0f,
        remaining = "Unread",
        coverColors = listOf(Color(0xFF35211E), Color(0xFF9B5948)),
    ),
    LibraryBook(
        title = "Invisible Cities",
        shortTitle = "Invisible\nCities",
        author = "Italo Calvino",
        authorLastName = "CALVINO",
        progress = 0.0f,
        remaining = "Unread",
        coverColors = listOf(Color(0xFF352D46), Color(0xFF8D7BA8)),
    ),
    LibraryBook(
        title = "The Dispossessed",
        shortTitle = "The\nDispossessed",
        author = "Ursula K. Le Guin",
        authorLastName = "LE GUIN",
        progress = 0.0f,
        remaining = "Unread",
        coverColors = listOf(Color(0xFF2E2E2E), Color(0xFF9C8B6C)),
    ),
)

internal val recentBooks = listOf(
    activeBook,
    LibraryBook(
        title = "A Philosophy of Software Design",
        shortTitle = "Software\nDesign",
        author = "John Ousterhout",
        authorLastName = "OUSTERHOUT",
        progress = 0.18f,
        remaining = "5h left",
        coverColors = listOf(Color(0xFF202830), Color(0xFF6A7D8E)),
    ),
    LibraryBook(
        title = "The Passenger",
        shortTitle = "The\nPassenger",
        author = "Cormac McCarthy",
        authorLastName = "MCCARTHY",
        progress = 0.73f,
        remaining = "1h left",
        coverColors = listOf(Color(0xFF2F2621), Color(0xFFA76F55)),
    ),
    LibraryBook(
        title = "Thinking in Systems",
        shortTitle = "Thinking\nin Systems",
        author = "Donella Meadows",
        authorLastName = "MEADOWS",
        progress = 0.31f,
        remaining = "4h left",
        coverColors = listOf(Color(0xFF24352B), Color(0xFF89A179)),
    ),
)
