# App Architecture

Yomu should use native Android architecture, but the app architecture must protect the product design from both Material defaults and third-party reader engine constraints.

## Implementation status (current)

The core of this architecture is now built, not just planned. Inside the single `:app` module:

- Built: Hilt DI, Room (schema v3, exported schemas, tested migrations), Preferences DataStore, type-safe Navigation Compose (with Material shared-axis X transitions), Coil 3 image loading, and the Readium 3.x EPUB engine behind a Yomu-owned `ReaderEngine` boundary.
- Built features: `library`, `bookdetails`, `bookedit`, `reader`, `settings`, `about`.
- Built layers: `core/{model, database, datastore, reader, storage, designsystem}`, `data/{books, reader/readium, settings}`, and `domain/imports`.
- Settings resolution is implemented as a two-layer merge (global default in DataStore, optional per-book override in Room) rather than the full multi-layer resolver described below.
- Pending: bookmarks, highlights, in-book search, advanced typography/page layout, reading statistics/sessions, nested groups and series/author grouping beyond a flat author group, multi-Gradle-module split, and the richer settings-layering resolver. Sections describing these remain forward-looking specs.

The package and Gradle-module layouts further down still describe the eventual destination; the "Current package structure" block below reflects what exists today.

## Core Decisions

- UI toolkit: Jetpack Compose for app UI, panels, library, settings, and reader chrome. (Built.)
- EPUB engine: Readium Kotlin Toolkit (3.x) behind a Yomu-owned interface. (Built — `core/reader` interfaces, `data/reader/readium` adapter.)
- Visual reader host: Compose screen containing a Readium navigator fragment where required. (Built — `feature/reader/ReaderNavigatorHost`.)
- Persistence: Room for structured app data, DataStore for app preferences/settings profiles where appropriate. (Built — `core/database` Room v3, Preferences DataStore.)
- State: Kotlin coroutines, Flow, StateFlow, and immutable UI state. (Built.)
- DI: Hilt. (Built — `YomuApplication`, `MainActivity`, and the `app/di` modules.)
- Navigation: type-safe Navigation Compose routes. (Built — `app/navigation`.)
- Import: Android Storage Access Framework, then copy imported files into app-private storage. (Built — `domain/imports/ImportBooksUseCase` + `core/storage/FileStorage`.)

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

## Current Package Structure

Everything lives inside `:app` with enforced package boundaries; no Gradle-module split yet. This keeps iteration fast. The structure below reflects what exists today.

```text
com.itexpert120.yomu
|-- MainActivity.kt              # @AndroidEntryPoint, extends FragmentActivity, edge-to-edge
|-- YomuApplication.kt           # @HiltAndroidApp
|-- app
|   |-- YomuApp.kt
|   |-- AppViewModel.kt
|   |-- EdgeToEdge.kt
|   |-- navigation
|   |   |-- YomuDestinations.kt  # @Serializable routes: Library, BookDetails, EditBook, Settings, About, Reader
|   |   `-- YomuNavHost.kt       # shared-axis (X) transitions
|   |-- di
|   |   |-- DataStoreModule.kt
|   |   |-- DatabaseModule.kt
|   |   |-- ReaderModule.kt
|   |   `-- RepositoryModule.kt
|   `-- devgallery
|       |-- DevGalleryActivity.kt
|       `-- YomuGalleryScreen.kt
|-- core
|   |-- model                    # Book/BookId, ReadingState, LibraryPreferences, ReaderSettings, AccentColor, ThemePreference
|   |-- database                 # YomuDatabase (v3), BookEntity, ChapterReadEntity, ReaderSettingsEntity, BookDao
|   |-- datastore                # YomuPreferences
|   |-- reader                   # ReaderEngine/ReaderSession/ReaderLocator/ReaderTocItem (no Readium types)
|   |-- storage                  # FileStorage
|   `-- designsystem             # Yomu* primitives + theme tokens
|-- data
|   |-- books                    # BookRepository + RoomBookRepository + mappers + ImportedBook
|   |-- reader
|   |   `-- readium              # ReadiumReaderEngine + ReadiumMetadataExtractor (the only Readium importers)
|   `-- settings                 # AppSettingsRepository, LibraryPrefsRepository, ReaderSettingsRepository
|-- domain
|   `-- imports                  # ImportBooksUseCase
`-- feature
    |-- library
    |-- bookdetails
    |-- bookedit
    |-- reader
    |-- settings
    `-- about
```

Planned packages (not yet created):

```text
|-- core
|   `-- common
|-- data
|   `-- themes                   # custom theme persistence (built-in themes are code-defined today)
|-- domain
|   |-- books
|   |-- reader                   # session/settings-resolution use cases (logic is in repositories/VMs today)
|   `-- settings
`-- feature
    |-- search                   # in-book / library search
    `-- appearance               # dedicated appearance feature (reader settings live in feature/reader today)
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

- Phase 1 (current): keep single `:app` module but enforce packages. The Room schema, `:data:*`-equivalent packages, and most feature packages already exist here; the module split has intentionally not happened yet.
- Phase 2: extract `:core:designsystem` once custom primitives stabilize.
- Phase 3: extract `:core:model`, `:core:database`, and `:data:*`.
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

This boundary is built. The actual interfaces live in `core/reader/Reader.kt`; `data/reader/readium/ReadiumReaderEngine` is the only package that imports Readium. The implemented shape is leaner than the illustrative target below (no separate publication/progression/search types yet), opening by file path rather than `BookId` and exposing the navigator as a `FragmentFactory` so the Compose host can place it without referencing engine types:

```kotlin
interface ReaderEngine {
    suspend fun open(filePath: String, initialLocatorJson: String?): ReaderSession?
    suspend fun tableOfContents(filePath: String): List<ReaderTocItem>
}

interface ReaderSession {
    val title: String
    val currentLocator: StateFlow<ReaderLocator?>
    val centerTaps: SharedFlow<Unit>
    val fragmentFactory: FragmentFactory
    val fragmentClassName: String
    fun onFragmentHosted(fragmentManager: FragmentManager, tag: String)
    fun applySettings(settings: ReaderSettings)
    fun goForward(); fun goBackward()
    fun nextChapter(); fun previousChapter()
    fun goToProgression(totalProgression: Double)
    fun close()
}
```

The illustrative target shape (some of this is still planned):

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

- `ReaderLocator` — built (`locatorJson` + `totalProgression` + `chapterTitle` + `href`).
- `ReaderTocItem` — built (flattened `id`/`title`/`locatorJson`/`depth`).
- `ReaderPublication` — planned (publication is held internally by the session today).
- `ReaderProgression` — planned (progression is read off `ReaderLocator.totalProgression`).
- `ReaderSearchResult` — planned (in-book search not built).
- `ResolvedReaderSettings` — planned (the engine consumes `ReaderSettings` directly today).
- `ReaderDecoration` — planned (no bookmarks/highlights yet).

Readium adapter maps (built where noted):

- Readium `Publication` opened internally from the EPUB file path. (Built.)
- Readium `Locator` JSON to/from `ReaderLocator`. (Built.)
- `ReaderSettings` to Readium `EpubPreferences` — scroll, fontSize, fontFamily, lineHeight, theme, backgroundColor, textColor, `publisherStyles = false`. (Built.)
- Custom bundled fonts registered via `EpubNavigatorFragment.Configuration` font-family declarations (TTFs in `app/src/main/assets/fonts`). (Built.)
- Bookmarks/highlights to Readium decorations. (Planned.)

## Reader Fragment Interop

Readium visual navigators are fragments. Compose reader screens should host the fragment behind a controlled boundary.

Structure (built):

- `ReaderRoute`: Compose route and ViewModel binding.
- `ReaderScreen`: Compose layout, chrome (`ReaderChrome`), and the controls sheet (`ReaderSheet`).
- `ReaderNavigatorHost`: interop container that hosts the Readium `EpubNavigatorFragment` (commits the fragment on attach and consumes window insets for edge-to-edge).
- `ReaderViewModel`: owns session state, locator persistence, and settings submission.

The adapter between events and navigator APIs lives inside `ReadiumReaderEngine`'s session (driven by `ReaderViewModel`) rather than a separate `ReadiumReaderController`. `MainActivity` extends `FragmentActivity` (with an AppCompat DayNight theme) so the navigator fragment can be hosted.

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

Current implementation (`data/settings/ReaderSettingsRepository`): a two-layer merge — a global default `ReaderSettings` in DataStore, plus an optional per-book override stored as a JSON blob in Room (`reader_settings` table). When a book has an override it **fully supersedes** the global default (`per-book ?: global`), set on-edit; there is no field-by-field merge or unsupported-setting reasoning yet.

The fuller layering below is still the target. Reader settings should eventually layer:

1. Built-in defaults.
2. App defaults.
3. Theme preset values.
4. Book-specific overrides.
5. Mode-specific overrides.
6. Temporary session adjustments.

Planned resolver:

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

Opening a book (built; the dedicated `OpenReaderSessionUseCase` is not extracted — `ReaderViewModel` calls the repository and engine directly):

```text
ReaderRoute(bookId)
-> ReaderViewModel.open(bookId)
-> BookRepository resolves file path
-> ReaderEngine.open(filePath, initialLocatorJson)
-> ReaderViewModel emits reader state
-> ReaderNavigatorHost displays content
-> currentLocator StateFlow updates Room progress
```

Importing a book (built — note metadata only; no separate authors/series/file tables yet):

```text
LibraryScreen import action
-> SAF picker returns Uri
-> ImportBooksUseCase
-> FileStorage copies file into app storage
-> sha256 hash duplicate check
-> ReadiumMetadataExtractor opens publication
-> cover extracted + saved
-> Room inserts a single `books` row
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
