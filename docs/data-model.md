# Data Model

This document defines the first-pass data model for Yomu. It is intentionally implementation-oriented so Room/DataStore work can follow without rediscovering relationships.

## Implementation status (current)

Room is live at **schema version 3** (`core/database/YomuDatabase`, schemas exported under `app/schemas`, with tested migrations 1→2 and 2→3). The as-built schema is deliberately simpler than the relational target described in the rest of this document:

- Built tables: `books` (one flat row per imported book, with the single EPUB file's storage info embedded — no separate `BookFile` table), `chapter_reads` (per-chapter read-state), and `reader_settings` (per-book settings override as a JSON blob).
- Reading progress is embedded on the `books` row (`progress`, `totalProgression`, `locatorJson`, `lastOpenedAt`) rather than a separate `BookProgress` table.
- Library view preferences and app settings (theme, OLED toggle, accent) live in Preferences DataStore.
- Reader settings: a global default lives in DataStore; per-book overrides live in `reader_settings`. Resolution is `per-book ?: global` (full override, not a field merge).
- Not built yet: separate `BookFile`/`Author`/`Series`/`Group` tables and their cross-refs, `ReadingSession`, `Bookmark`, `Highlight`, persisted custom `ThemePreset`, persisted `CustomFont`, the grouped/multi-layer reader settings models, and the FTS search index.

The "As-built schema (v3)" section below documents what exists today. Everything after it describes the eventual target and remains forward-looking.

## As-built schema (v3)

### `books` (BookEntity)

Single flat table; `id` is a `String` UUID primary key, with a unique index on `sha256`.

- `id`, `title`, `subtitle`, `author`, `description`, `language`, `publisher`, `series`
- `coverImagePath`, `storagePath`, `originalUri`, `originalDisplayName`
- `sha256`, `fileSizeBytes`
- `progress` (Float), `totalProgression` (Double?), `locatorJson` (String?)
- `addedAt`, `lastOpenedAt`

`author` and `series` are plain strings on the row (no join tables). The domain `Book` (`core/model/Book`) is mapped from this entity and additionally derives `currentHref` and `currentChapterProgress` by parsing `locatorJson`; `readingState` is derived from `progress`.

### `chapter_reads` (ChapterReadEntity)

Composite primary key `(bookId, chapterId)`. Presence of a row = that chapter is read; absence = unread. `chapterId` is the resource href, matching `ReaderTocItem.id` / `ReaderLocator.href`.

### `reader_settings` (ReaderSettingsEntity)

Primary key `bookId`; `json` holds a serialised per-book `ReaderSettings` override. Presence means the book overrides the global default.

### Migrations

- `1→2`: adds `chapter_reads` (books preserved).
- `2→3`: adds `reader_settings` (books preserved).

No destructive migrations are used for library data.

## Identity Types

Use strongly typed IDs in Kotlin models even if Room stores strings/longs.

- `BookId` (built — `@JvmInline value class` in `core/model/Book`)
- `BookFileId` (planned)
- `AuthorId` (planned)
- `SeriesId` (planned)
- `GroupId` (planned)
- `BookmarkId` (planned)
- `HighlightId` (planned)
- `ThemeId` (planned)
- `FontId` (planned)
- `ReadingSessionId` (planned)

## Core Entities

> Target model. Today only a single flat `books` table exists (see "As-built schema (v3)" above). The richer entities and relationships below — `BookFile`, `Author`, `Series`, `Group`, and their cross-refs — are planned and land as those features are built.

### Book

Fields:

- `id`
- `title`
- `subtitle`
- `sortTitle`
- `description`
- `language`
- `publisher`
- `publishedDate`
- `isbn`
- `coverImagePath`
- `addedAt`
- `updatedAt`
- `lastOpenedAt`
- `readingState`
- `favorite`
- `archived`

Relationships:

- many-to-many with authors.
- optional series membership.
- many-to-many with groups.
- one or more files if alternate copies are supported later.

### BookFile

Fields:

- `id`
- `bookId`
- `storagePath`
- `originalDisplayName`
- `originalUri`
- `mimeType`
- `fileSizeBytes`
- `sha256`
- `importedAt`
- `lastValidatedAt`
- `validationStatus`

Rules:

- App reads from `storagePath`, not external URI, after import.
- Hash is used for duplicate detection.

### Author

Fields:

- `id`
- `name`
- `sortName`

Join:

- `BookAuthorCrossRef(bookId, authorId, role, displayOrder)`

### Series

Fields:

- `id`
- `name`
- `sortName`

Join/fields:

- Book may have `seriesId` and `seriesIndex`.
- If multiple series are needed later, introduce `BookSeriesCrossRef`.

### Group

Fields:

- `id`
- `parentGroupId`
- `name`
- `sortName`
- `createdAt`
- `updatedAt`
- `manualOrder`

Join:

- `BookGroupCrossRef(bookId, groupId)`

Rules:

- Nested groups use `parentGroupId`.
- Prevent cycles in domain logic.

## Reading Progress

> Target model. Reading progress is currently embedded on the `books` row (`progress`, `totalProgression`, `locatorJson`, `lastOpenedAt`); the standalone `BookProgress` and `ReadingSession` tables below are planned.

### BookProgress

Fields:

- `bookId`
- `locatorJson`
- `totalProgression`
- `position`
- `positionsTotal`
- `chapterHref`
- `chapterTitle`
- `estimatedRemainingSeconds`
- `updatedAt`

Notes:

- `locatorJson` should preserve the engine-native position accurately.
- `totalProgression` is from 0.0 to 1.0 where available.
- Position counts are stable engine positions, not guaranteed visual pages.

### ReadingSession

Fields:

- `id`
- `bookId`
- `startedAt`
- `endedAt`
- `startLocatorJson`
- `endLocatorJson`
- `durationSeconds`
- `deviceClass`
- `readingMode`

Use case:

- Reading statistics and remaining-time estimates later.

## Bookmarks

> Planned. Bookmarks are not built yet.

### Bookmark

Fields:

- `id`
- `bookId`
- `locatorJson`
- `totalProgression`
- `position`
- `chapterTitle`
- `snippet`
- `createdAt`
- `updatedAt`

Rules:

- Unique bookmark per book/location threshold if needed.
- Use locator for navigation.

## Highlights

> Planned. Highlights are not built yet.

### Highlight

Fields:

- `id`
- `bookId`
- `locatorJson`
- `rangeJson`
- `colorKey`
- `selectedText`
- `note`
- `chapterTitle`
- `totalProgression`
- `createdAt`
- `updatedAt`

Rules:

- `rangeJson` is optional until engine support is confirmed.
- `selectedText` is stored for display and search.
- Notes can be null in the first version.

## Themes

### ThemePreset

> Partly built. App theme choice (`ThemePreference`: System/Light/Dark), a pure-black/OLED toggle, and the accent (`AccentColor` presets or a custom ARGB, via `AccentSelection`) are persisted in DataStore. Reader colour themes are a code-defined enum (`ReaderThemeMode`: Light/Dark/Sepia/Black/Custom). The persisted **custom** `ThemePreset` table below is planned.

Built-in presets can be code-defined. User themes should be persisted.

Fields for persisted custom theme:

- `id`
- `name`
- `basePresetKey`
- `lightTextColor`
- `lightBackgroundColor`
- `lightLinkColor`
- `darkTextColor`
- `darkBackgroundColor`
- `darkLinkColor`
- `accentColor`
- `highlightPaletteJson`
- `backgroundMode`
- `backgroundImagePath`
- `invertImagesInDarkMode`
- `overrideBookColors`
- `createdAt`
- `updatedAt`

Background mode values:

- `plain`
- `noise`
- `paper`
- `sand`
- `moon`
- `custom_image`

## Fonts

### CustomFont

> Planned. Custom user fonts are out of scope today; reading fonts are a bundled, code-defined enum (`ReaderFont`: Lora, Karla, Rubik, Cardo, Nunito, Merriweather — Lora is the default), with TTFs in `app/src/main/assets/fonts` registered on the Readium navigator.

Fields:

- `id`
- `displayName`
- `familyName`
- `style`
- `weight`
- `storagePath`
- `fileName`
- `sha256`
- `importedAt`

Font roles:

- default.
- serif.
- sans-serif.
- monospace.

Font role assignment can live in app settings or reader settings.

## Reader Settings Model

> Target model. The built `ReaderSettings` (`core/model/ReaderSettings`) is a single flat, `@Serializable` data class covering layout (`ReaderLayout`: Scroll/Paged), theme (`ReaderThemeMode`), optional custom background/text colours, font (`ReaderFont`) + `fontScale` + optional `lineHeight`, brightness, and footer/chrome toggles. It is persisted globally in DataStore and optionally per-book in Room. The grouped sub-models below (paragraph/page/status splits, advanced typography and page layout, reading modes) are planned; only a subset of their fields exists today.

Use grouped settings. Store as typed Kotlin models. Persist globally and optionally per book.

### FontSettings

Fields:

- `fontSizeScale`
- `fontFamilyKey`
- `defaultFontKey`
- `serifFontKey`
- `sansSerifFontKey`
- `monospaceFontKey`

### ParagraphSettings

Fields:

- `paragraphMargin`
- `lineSpacing`
- `wordSpacing`
- `letterSpacing`
- `textIndent`
- `fullJustification`
- `hyphenation`

### PageSettings

Fields:

- `topMargin`
- `bottomMargin`
- `leftMargin`
- `rightMargin`
- `columnGap`
- `maxColumns`
- `maxColumnWidthDp`
- `maxColumnHeightDp`
- `orientationPreference`
- `pagingAnimation`

### StatusSettings

Fields:

- `showHeader`
- `showFooter`
- `tapToToggleFooter`
- `showRemainingTime`
- `showRemainingPages`
- `showReadingProgress`
- `showCurrentTime`
- `showBatteryStatus`
- `showBookTitle`
- `showChapterTitle`

### ThemeSettings

Fields:

- `themeMode`
- `themePresetKey`
- `customThemeId`
- `overrideBookColors`
- `invertImagesInDarkMode`
- `textColorOverride`
- `backgroundColorOverride`
- `linkColorOverride`
- `backgroundMode`
- `backgroundImagePath`

### ReadingModeSettings

Fields:

- `readingMode`
- `pagedSettings`
- `scrolledSettings`
- `paragraphSettings`
- `speedReadingSettings`

## Settings Layering

> Current: a two-layer resolution — a global default in DataStore and an optional per-book override in Room, where the override fully replaces the global default (`per-book ?: global`). The fuller layering and `ResolvedReaderSettings` output (with per-setting source/supported/inactive-reason metadata) below are planned.

Settings resolve in this order:

1. Built-in defaults.
2. App-level reader defaults.
3. Theme preset.
4. Book-specific overrides.
5. Reading-mode overrides.
6. Session temporary overrides.

Output model:

- `ResolvedReaderSettings`

Each output setting should know:

- effective value.
- source layer.
- whether it is supported by current publication.
- inactive reason if unsupported.

Inactive reasons:

- `fixed_layout_publication`
- `publisher_styles_locked`
- `engine_unsupported`
- `mode_unsupported`
- `theme_override_disabled`

## Library View Preferences

Built (`core/model/LibraryPreferences`, persisted in DataStore). Implemented fields:

- `viewMode`: Grid/List (`LibraryViewMode`).
- `gridColumns`: 0 = Auto (adapts to width) or a forced count (3–7). A single column field, not separate compact/expanded values.
- `coverCrop`: crop vs fit.
- `groupMode`: None/Author only (`GroupMode`); series/group/status grouping is planned.
- `sortMode`: Recent/Title/Author/Unread (`SortMode`).

Planned (not yet persisted): `sortDirection`, `showArchived`, separate compact/expanded column counts, and richer grouping modes.

These can live in DataStore unless per-group persistence becomes relational.

## Search Index

> Planned. No search is built yet (neither direct metadata queries nor FTS).

Initial search:

- Query Room metadata directly.

Later full-text search options:

- Room FTS table for book metadata and extracted text snippets.
- Store `BookTextIndex(bookId, href, locatorJson, text, normalizedText)`.

Do not build full-text indexing before import and reader are stable.

## Room Migration Rules

The schema is live at version 3 with explicit, additive migrations (1→2, 2→3) and exported schema JSON under `app/schemas`. The rules below remain in force:

- Add migration tests from the first schema.
- Never use destructive migrations for user library data.
- Use transactions for import writes.

## Deletion Rules

> Current (`RoomBookRepository.remove`): deleting a book removes its `books` row and also deletes its `chapter_reads` and `reader_settings` rows, the imported EPUB file, and the extracted cover. Per-bookmark/highlight cleanup and archiving are planned (no bookmarks/highlights or `archived` flag exist yet).

Deleting a book should optionally delete:

- Book DB row.
- Imported EPUB file.
- Extracted cover.
- Book progress.
- Book-specific settings.
- Bookmarks/highlights only after explicit confirmation if needed.

Archiving should hide the book without deleting files/data.
