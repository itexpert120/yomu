# Data Model

This document defines the first-pass data model for Yomu. It is intentionally implementation-oriented so Room/DataStore work can follow without rediscovering relationships.

## Identity Types

Use strongly typed IDs in Kotlin models even if Room stores strings/longs.

- `BookId`
- `BookFileId`
- `AuthorId`
- `SeriesId`
- `GroupId`
- `BookmarkId`
- `HighlightId`
- `ThemeId`
- `FontId`
- `ReadingSessionId`

## Core Entities

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

Persist lightweight library UI preferences:

- `libraryViewMode`: grid/list.
- `gridColumnsCompact`.
- `gridColumnsExpanded`.
- `coverFitMode`: crop/fit.
- `groupingMode`: none/author/series/group/status.
- `sortMode`.
- `sortDirection`.
- `showArchived`.

These can live in DataStore unless per-group persistence becomes relational.

## Search Index

Initial search:

- Query Room metadata directly.

Later full-text search options:

- Room FTS table for book metadata and extracted text snippets.
- Store `BookTextIndex(bookId, href, locatorJson, text, normalizedText)`.

Do not build full-text indexing before import and reader are stable.

## Room Migration Rules

- Start Room schema at version 1 only when initial entities are stable enough.
- Add migration tests from the first schema.
- Never use destructive migrations for user library data.
- Use transactions for import writes.

## Deletion Rules

Deleting a book should optionally delete:

- Book DB row.
- Imported EPUB file.
- Extracted cover.
- Book progress.
- Book-specific settings.
- Bookmarks/highlights only after explicit confirmation if needed.

Archiving should hide the book without deleting files/data.
