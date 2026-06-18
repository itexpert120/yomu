# App Architecture

Yomu should use native Android architecture, but the app architecture must protect the product design from both Material defaults and third-party reader engine constraints.

## Core Decisions

- UI toolkit: Jetpack Compose for app UI, panels, library, settings, and reader chrome.
- EPUB engine: Readium Kotlin Toolkit behind a Yomu-owned interface.
- Visual reader host: Compose screen containing a Readium navigator fragment where required.
- Persistence: Room for structured app data, DataStore for app preferences/settings profiles where appropriate.
- State: Kotlin coroutines, Flow, StateFlow, and immutable UI state.
- DI: Hilt once real data/engine dependencies are introduced.
- Navigation: type-safe Navigation Compose routes.
- Import: Android Storage Access Framework, then copy imported files into app-private storage.

## Architecture Style

Use a pragmatic layered architecture:

- UI layer: Compose screens, custom primitives, ViewModels, state holders.
- Domain layer: reusable business logic and use cases where complexity is real.
- Data layer: repositories, DAOs, data sources, file storage, Readium integration adapters.
- Engine boundary: EPUB reader APIs exposed through Yomu interfaces.

The domain layer is optional per feature. Use it where the logic is reused or complex, such as import, settings resolution, reading progress, and theme resolution.

## Dependency Direction

Dependencies should point inward or downward:

- UI depends on domain/data abstractions through ViewModels.
- Domain depends on repository interfaces or concrete repositories, depending on module stage.
- Data depends on Room, DataStore, file system, Readium, Coil, SAF helpers.
- Design system depends only on Compose foundation/runtime and minimal Android resources.
- Reader feature depends on the reader engine abstraction, not Readium directly except in the engine adapter package/module.

## Initial Package Structure

Start inside `:app` with package boundaries before creating many Gradle modules. This keeps early iteration fast.

```text
com.itexpert120.yomu
|-- app
|   |-- EdgeToEdge.kt
|   `-- devgallery
|       |-- DevGalleryActivity.kt
|       `-- YomuGalleryScreen.kt
|-- core
|   `-- designsystem
|       |-- YomuTheme.kt
|       |-- YomuSurfaces.kt
|       |-- YomuControls.kt
|       `-- YomuCards.kt
`-- feature
    `-- library
        |-- LibraryModels.kt
        |-- LibraryHeader.kt
        |-- LibraryBooks.kt
        |-- LibraryOverlays.kt
        |-- LibraryInsets.kt
        `-- LibraryScreen.kt
```

Planned packages (not yet created):

```text
|-- core
|   |-- common
|   |-- model
|   |-- storage
|   |-- database
|   |-- datastore
|   `-- reader
|-- data
|   |-- books
|   |-- import
|   |-- reading
|   |-- settings
|   `-- themes
|-- domain
|   |-- books
|   |-- import
|   |-- reader
|   |-- settings
|   `-- themes
`-- feature
    |-- reader
    |-- bookdetails
    |-- search
    |-- settings
    `-- appearance
```

## Future Gradle Module Structure

Split into Gradle modules when package boundaries are stable or build times become painful.

```text
:app
:core:common
:core:model
:core:designsystem
:core:ui
:core:database
:core:datastore
:core:storage
:core:reader-api
:data:books
:data:import
:data:reader-readium
:data:settings
:data:themes
:domain:books
:domain:reader
:domain:settings
:feature:library
:feature:reader
:feature:bookdetails
:feature:search
:feature:settings
:feature:appearance
:testing:fixtures
:benchmark
```

Recommended timing:

- Phase 1: keep single `:app` module but enforce packages.
- Phase 2: extract `:core:designsystem` once custom primitives stabilize.
- Phase 3: extract `:core:model`, `:core:database`, and `:data:*` once Room schema lands.
- Phase 4: extract feature modules if screen ownership becomes large.

## Feature Ownership

Library feature owns:

- Library home.
- Book grid/list.
- Group views.
- Author/series views.
- Search entry points.
- Sort/filter controls.
- Add/import triggers.

Reader feature owns:

- Reading screen shell.
- Reader chrome.
- TOC panel.
- Bookmarks/highlights panels.
- Progress scrubber.
- Reading mode controls.
- Selection/highlight UI.

Appearance feature owns:

- Font settings.
- Paragraph settings.
- Page settings.
- Theme settings.
- Background settings.
- Live previews.

Settings feature owns:

- App-level settings.
- Defaults.
- Custom fonts.
- Custom themes.
- Import/export of settings later.

Data/import owns:

- SAF import.
- File copying.
- Hashing.
- Metadata extraction.
- Cover extraction.
- Indexing.

Reader engine owns:

- Open publication.
- Create navigator/session.
- Resolve TOC.
- Observe locator/progress.
- Navigate to locator/progression/link.
- Submit reader preferences.
- Search publication text.
- Apply decorations for highlights/bookmarks.

## Reader Engine Boundary

Do not let Readium types leak everywhere. Create Yomu-owned interfaces and map to Readium internally.

```kotlin
interface ReaderEngine {
    suspend fun openBook(bookId: BookId): ReaderSession
}

interface ReaderSession {
    val publication: ReaderPublication
    val currentLocator: Flow<ReaderLocator>
    val progression: Flow<ReaderProgression>
    suspend fun goTo(locator: ReaderLocator)
    suspend fun goToProgression(totalProgression: Double)
    suspend fun submitSettings(settings: ResolvedReaderSettings)
    suspend fun search(query: String): List<ReaderSearchResult>
    suspend fun close()
}
```

Yomu model types:

- `ReaderPublication`
- `ReaderLocator`
- `ReaderProgression`
- `ReaderTocItem`
- `ReaderSearchResult`
- `ResolvedReaderSettings`
- `ReaderDecoration`

Readium adapter maps:

- Readium `Publication` to `ReaderPublication`.
- Readium `Locator` JSON to `ReaderLocator`.
- Readium preferences to `ResolvedReaderSettings`.
- Bookmarks/highlights to Readium decorations.

## Reader Fragment Interop

Readium visual navigators are fragments. Compose reader screens should host the fragment behind a controlled boundary.

Structure:

- `ReaderRoute`: Compose route and ViewModel binding.
- `ReaderScreen`: Compose layout, chrome, panels, settings.
- `ReaderNavigatorHost`: interop container that hosts Readium fragment.
- `ReadiumReaderController`: adapter between ViewModel events and navigator APIs.

Rules:

- The Readium fragment displays only publication content.
- Yomu Compose UI owns all chrome and controls.
- Fragment setup/recovery logic stays isolated.
- The ViewModel owns session state, locator persistence, and settings submission.

## UI State Pattern

Each screen exposes immutable state plus events.

```kotlin
data class ReaderUiState(
    val book: BookSummaryUi,
    val chromeVisible: Boolean,
    val mode: ReadingMode,
    val progress: ReadingProgressUi,
    val activePanel: ReaderPanel?,
    val settings: ResolvedReaderSettingsUi,
    val toc: List<TocItemUi>,
    val bookmarks: List<BookmarkUi>,
    val highlights: List<HighlightUi>,
    val isLoading: Boolean,
    val error: ReaderErrorUi?
)
```

Events:

- `OnToggleChrome`
- `OnOpenToc`
- `OnOpenAppearance`
- `OnToggleBookmark`
- `OnSeekProgression`
- `OnChangeReadingMode`
- `OnChangeTheme`
- `OnChangeFontSetting`
- `OnTextSelected`
- `OnHighlightSelectedText`

Rules:

- State flows down from ViewModel to composables.
- Events flow up from composables to ViewModel.
- Composables should be mostly stateless, except small ephemeral UI state.
- Persisted settings and reading progress are not owned by composables.

## Settings Resolution

Reader settings must be layered:

1. Built-in defaults.
2. App defaults.
3. Theme preset values.
4. Book-specific overrides.
5. Mode-specific overrides.
6. Temporary session adjustments.

Create a resolver:

```kotlin
class ResolveReaderSettingsUseCase(
    private val appSettingsRepository: AppSettingsRepository,
    private val bookSettingsRepository: BookSettingsRepository,
    private val themeRepository: ThemeRepository
)
```

Output:

- `ResolvedReaderSettings`
- `ResolvedTheme`
- `InactiveSettingReason` for unsupported settings.

This matters because some settings are not valid for fixed-layout books, some are mode-specific, and dark-mode image inversion depends on theme state.

## Data Flow Examples

Opening a book:

```text
ReaderRoute(bookId)
-> ReaderViewModel.open(bookId)
-> OpenReaderSessionUseCase
-> BookRepository resolves file
-> ReaderEngine opens publication
-> ReaderViewModel emits ReaderUiState
-> ReaderNavigatorHost displays content
-> currentLocator Flow updates Room progress
```

Importing a book:

```text
LibraryScreen import action
-> SAF picker returns Uri
-> ImportBooksUseCase
-> FileStorage copies file
-> hash duplicate check
-> Reader metadata extractor opens publication
-> cover extracted
-> Room transaction inserts book/authors/series/file
-> LibraryUiState updates
```

Changing theme:

```text
AppearancePanel event
-> ReaderViewModel updates session override
-> ResolveReaderSettingsUseCase recomputes resolved settings
-> ReaderEngine submits mapped preferences
-> App optionally persists override
```

## Error Handling

Reader errors:

- File missing.
- File unreadable.
- Unsupported EPUB.
- Corrupt EPUB.
- Readium open failure.
- Navigator restoration failure after process death.
- Unsupported setting for current publication.

Import errors:

- Duplicate book.
- Unsupported file type.
- Copy failed.
- Metadata extraction failed.
- Cover extraction failed.
- Validation warning later.

UI rule:

- Errors should be precise and recoverable.
- Avoid generic snackbars as the main error surface.
- Use custom inline notices or compact panels.

## Offline And Privacy Model

Yomu should be offline-first.

- EPUB files stay local.
- Library metadata stays local.
- No network access is needed for core reading.
- Custom fonts/backgrounds are imported locally.
- Optional future catalog/cloud features must be isolated.

## Testing Strategy

Unit tests:

- Settings resolution.
- Theme resolution.
- Progress calculations.
- Sort/group logic.
- Import duplicate detection.
- Repository behavior with fake DAOs.

Integration tests:

- Room migrations.
- Import pipeline with sample EPUBs.
- Readium engine smoke tests.

UI tests:

- Library grid/list state.
- Reader chrome toggle.
- Settings panel controls.
- Theme selection.
- Progress scrubber interactions.

Fakes:

- `FakeReaderEngine`
- `FakeReaderSession`
- `FakeBookRepository`
- `FakeSettingsRepository`

## Architecture Risks

- Readium fragment lifecycle may complicate pure Compose screens.
- Advanced layout controls may exceed what Readium preferences expose.
- True pages are unstable for reflowable EPUB; product copy must use pages carefully.
- Speed reading and paragraph mode may need text extraction instead of visual navigator control.
- Over-modularization too early can slow design iteration.

## Guardrails

- Build the design system before integrating full EPUB complexity.
- Wrap third-party APIs.
- Keep reader settings in Yomu models.
- Keep app UI custom and Compose-owned.
- Delay module splitting until boundaries are proven.
