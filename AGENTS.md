# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## What This Is

Open Reader (formerly Yomu) is a native Android EPUB reader (Kotlin + Jetpack Compose). The product intent is a polished, reader-first, tablet-optimized app with a recognizably native Android feel, following Material 3 Expressive principles while retaining reader-specific comfort surfaces. Internal Kotlin packages and `Yomu*` design-system identifiers retain their established names for source and data compatibility. See `docs/` for the full product/design/architecture specs — `docs/README.md` is the entry point.

Current state: a **mature, feature-rich EPUB reader** — well past a minimal MVP. The `:app` module plus a small `:benchmark` macrobenchmark module contain the custom design system, a Room-backed library (SAF import, Coil covers, search/sort/group, Comfortable/Compact/Cover-only grid and List modes, orientation-specific columns, multi-select bulk actions, scroll-responsive Resume FAB), book details (adaptive two-pane on tablets with a 176dp cover, virtualized TOC with per-chapter read tracking and "mark to here"), and a Readium-backed reader with themes/fonts/brightness/extra-dim, in-reader TOC, **bookmarks**, **in-book full-text search**, **highlights** (with an optional colour palette), **advanced typography** (line height, page margins, paragraph spacing, alignment), **dictionary word-lookup + TTS**, footnote popups, and saved custom reader themes. Also built: streamlined **reading statistics** (tablet overview/history panes, aggregate metric cards, and recent reading history), tablet Settings master–detail, and **external "Open with"/share** EPUB import. **Hilt, Room, DataStore, Navigation Compose, Coil, and Readium are all present and wired.** (Home-screen Glance widgets were removed.)

Network access is limited to dictionary lookups, explicit user-initiated Google web searches, and explicit user-initiated Google Fonts downloads; reading, library management, annotations, in-book search, and statistics remain offline. Remaining gaps are later-phase roadmap items (e.g. OPDS catalogs, non-EPUB formats like PDF/audiobook, cross-device sync) — see `docs/roadmap.md`. The `docs/` describe the product/design intent and are kept current — each doc has an "Implementation status (current)" note, and `docs/roadmap.md` (Phases 0–13) is the authoritative done-vs-pending source. **Keep this AGENTS.md in sync with the code as features land.**

## Commands

Use the Gradle wrapper. On this Windows/PowerShell environment use `./gradlew` (Bash tool) or `.\gradlew.bat` (PowerShell).

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build + install on connected device/emulator
./gradlew test                   # JVM unit tests (src/test)
./gradlew connectedAndroidTest   # instrumented tests (src/androidTest, needs device)
./gradlew lint                   # Android lint
./gradlew :app:compileDebugKotlin  # fast compile-only check

# run a single JVM unit test
./gradlew test --tests "com.itexpert120.yomu.ExampleUnitTest"
./gradlew test --tests "com.itexpert120.yomu.ExampleUnitTest.addition_isCorrect"
```

There is no separate "run tests" vs "lint" toolchain beyond Gradle. All external dependency versions live in `gradle/libs.versions.toml` (version catalog) — add dependencies there, referenced as `libs.*`, never inline in `build.gradle.kts`.

Toolchain versions are authoritative in `gradle/libs.versions.toml`; SDK levels and variants are in `app/build.gradle.kts`. Use **Java 17** with core-library desugaring. DI is Hilt (`enableAggregatingTask = false` works around the javac aggregator's Kotlin metadata limit); Room uses KSP with schema export to `app/schemas`. The app theme parent is `Theme.AppCompat.DayNight.NoActionBar` so it can host the Readium navigator Fragment. For fixture-backed launch checks and the distinction between emulator evidence and performance acceptance, read `docs/reader-benchmarks.md`.

## Architecture

### Package layout (inside the single `:app` module)
```
com.itexpert120.yomu
├── MainActivity.kt              # @AndroidEntryPoint, extends FragmentActivity (hosts the Readium
│                                #   navigator); splash; edge-to-edge; external-open deep links
├── YomuApplication.kt           # @HiltAndroidApp
├── app/                         # shell: YomuApp (theme resolution), navigation/{YomuNavHost,
│                                #   YomuDestinations}, di/*, AppViewModel, ExternalOpenViewModel,
│                                #   EdgeToEdge, devgallery
├── core/
│   ├── designsystem/            # custom design system (Yomu* primitives, YomuTwoPane, CompositionLocals, HSV color
│   │                            #   picker, responsive YomuWidthClass)
│   ├── model/                   # Book, ReaderSettings, LibraryPreferences, AccentColor, ThemePreference,
│   │                            #   CustomReaderTheme, ReadingStats, …
│   ├── database/                # Room: YomuDatabase (v16) + BookEntity, ChapterReadEntity,
│   │                            #   ChapterProgressEntity, ReaderSettingsEntity, BookTocEntity, ReadingDayEntity,
│   │                            #   ReadingSessionEntity, ReadingTotalEntity, ReadingWriteReceipt,
│   │                            #   HighlightEntity, BookmarkEntity,
│   │                            #   compatibility-only legacy sync entities,
│   │                            #   BookDao, HighlightDao, BookmarkDao, migrations
│   ├── datastore/ · storage/    # DataStore prefs ; FileStorage (app-private epubs/covers)
│   └── reader/                  # ReaderEngine/ReaderSession + open/cache/render contracts (no Readium)
│                                #   (Yomu-owned; no Readium types)
├── data/
│   ├── books/                   # BookRepository + RoomBookRepository + mappers
│   ├── reader/readium/          # ReadiumReaderEngine (+ ReadiumMetadataExtractor, ReadiumFragmentRestore)
│   │                            #   — the ONLY package that imports Readium
│   ├── settings/                # AppSettingsRepository, LibraryPrefsRepository, ReaderSettingsRepository
│   ├── highlights/              # HighlightRepository + RoomHighlightRepository
│   ├── bookmarks/               # BookmarkRepository + RoomBookmarkRepository
│   ├── fonts/                   # FontRepository + recoverable FontInstallationStore
│   ├── stats/                   # StatsRepository, ReadingCalendar, application-owned ReadingWriteQueue
│   └── dictionary/              # DictionaryRepository (Free Dictionary API)
├── domain/imports/              # ImportBooksUseCase (SAF + external-open import pipeline; also
│                                #   extracts metadata, cover, and TOC in one Readium pass)
└── feature/                     # library, bookdetails, bookedit, reader, settings, stats, about
```
Type-safe nav destinations: `Home` (the top-level shell), `Library`, `BookDetails(bookId)`, `EditBook(bookId)`, `Settings`, `Stats`, `ReaderDefaults`, `About`, `Reader(bookId, locator?)`. The Library, Statistics, and Settings destinations live in a nested navigator inside an adaptive Material `NavigationBar`/`NavigationRail` Home shell; root transitions move that entire shell to focused child routes. Bookmarks and in-book search are built **behind the `core/reader` boundary** (search via Readium's `SearchService`, surfaced as `ReaderSession.search`/`applySearchDecorations`; bookmarks are Room-backed and reuse `currentLocator`/`goToLocator`). New reader capabilities should follow the same boundary pattern — the sibling Readium test-app under "Related projects" below has worked references.

### Design system is the foundation — use Material 3 Expressive through the compatibility layer
`core/designsystem` owns the Material 3 Expressive theme boundary and temporary Yomu compatibility wrappers:
- `YomuDesignTheme { ... }` owns `MaterialExpressiveTheme`, explicit light/dark/OLED colour schemes with opt-in Android 12+ dynamic colours for light/dark modes, Material typography/shapes/motion, and the existing Yomu CompositionLocals.
- Access compatibility tokens inside composables via `YomuTheme.colors`, `YomuTheme.type`, `YomuTheme.space`, and `YomuTheme.radius`; migrate call sites incrementally rather than inventing a second token system.
- Token data classes remain `YomuColors`, `YomuType`, `YomuSpacing`, and `YomuRadius` (all `@Immutable`). Theme variants remain `YomuThemeMode.{Light, Dark, Oled}`.
- Prefer native Material components through the Yomu wrappers (`Button`, chips, tabs, switches, sliders, text fields, cards, top app bars, dialogs, FABs, lists, and sheets). Keep custom Canvas/gesture code only where Material has no equivalent or the EPUB page requires reader-specific behaviour (for example the HSV picker and engine-driven reading canvas).
- Preserve native touch targets, semantics, ripples, reduced-motion support, accessible Material colour roles, and adaptive compact/medium/expanded layouts. Tablet layouts should add context or side-by-side navigation rather than stretching phone UI.
- The design system package must not depend on `feature/*`.
- Library, Statistics, Settings, and About share large padded, collapsing Material headings. Library uses a directly editable search field and the Resume FAB; keep its collection area compact. Appearance, page Theme, Reader chrome, and About use grouped tonal list surfaces. Home uses native expressive short navigation and a wide navigation rail. See `docs/design-language.md` for the current treatment.

### Theme ↔ system bars
`MainActivity` owns the window insets controller and flips status/nav bar icon appearance on theme-mode change so bar icons stay legible. The **reader** additionally takes over the system bars while open (`ReaderScreen`): it hides both bars for full-screen reading, colours them to the reading theme, and restores them on exit.

### Reader engine boundary
The EPUB engine is Readium, but Readium types must **not** leak. All reader access goes through Yomu-owned interfaces in `core/reader` (`ReaderEngine`, `ReaderSession`, `ReaderNavigator`, `ReaderOpenRequest`, `ReaderOpenResult`, `ReaderPublicationCache`, `ReaderRenderState`, `ReaderLocator`, `ReaderTocItem`, `ReaderHighlight`, `ReaderBookmark`, `ReaderSearchResult`); only `data/reader/readium/*` imports Readium directly — `ReadiumReaderEngine` (reading), `ReadiumMetadataExtractor` (import-time metadata/cover), and `ReadiumFragmentRestore` (the config-change/process-death navigator-restoration guard; see commit 07ac465). `ReaderSession` is implementation-only outside `feature/reader/ReadingExperience`; Compose receives its restricted `ReaderNavigator` facet through state. `ReadingExperience` owns book-scoped opening/retry/readiness, navigation, settings, locator/progress, search, lookup/TTS, annotations, and foreground reading-time attribution through one state stream plus `ReadingExperienceAction`; `ReaderViewModel` adapts that state and owns only Compose chrome plus app-global font/theme lists. `ReaderSettings` → `EpubPreferences` mapping (scroll/paged, fontSize, theme, bg/text colour, fontFamily, lineHeight/margins/paragraph-spacing, `publisherStyles = false`) lives in the engine. Reader settings resolve as a **global default (DataStore) ⊕ per-book override (Room `reader_settings`)**, written per-book-on-edit — the global default is edited on the `ReaderDefaults` screen. Six reading fonts are bundled in `app/src/main/assets/fonts/`; only the active bundled upright face is preloaded. See `docs/app-architecture.md` and `docs/reader-feature-spec.md`.

Reader chrome has a chapter-title/bookmark top bar, optional battery/clock/progress footer, and bottom controls. **Browse** has exactly Contents, Bookmarks, and Highlights tabs; **Search** is a separate sheet, alongside the More overflow sheet. `ReaderChromeToggles` supplies the shared keep-screen-on, immersive, and footer options to both in-reader controls and global defaults.

Immersive reading uses `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` plus injected `viewport-fit=cover`; Android WebViews otherwise letterbox below the cutout. Scroll mode retains the reader-specific rubberband chapter gesture. Use `core/designsystem/YomuMotion` for chrome, popup, content-swap, and API-31+ blur transitions.

For changes to persistence or lifecycle ownership, read the reliability section of `docs/app-architecture.md`: final reading writes survive reader closure in an application-scoped retry queue, but pending writes are not process-death durable. Keep the existing reading-experience/reading-session meanings from `CONTEXT.md`.

## Conventions

- Each feature follows a **Route (stateful, `hiltViewModel()`) → Screen (stateless, `On*` callbacks)** split; the `Screen` is preview-driven.
- Compose state flows down (immutable UI-state data classes), events flow up (explicitly named `On*` events). Keep composables stateless except for small ephemeral UI state.
- Do not pass repositories into composables, and do not run import/database/DataStore/Readium work from composables.
- Name composables by product role, not the widget they render.
- The Book Details TOC must stay a `LazyColumn` (can be thousands of entries) **with no item key** — chapter hrefs can legitimately repeat and a keyed list would crash.
- Comment only non-obvious behavior.
- Use `@Preview` composables for design iteration; the `app/devgallery` gallery exists to validate design primitives in isolation.

## Related projects (local siblings — reference material, not dependencies)

Two other repos live next to Yomu on this machine. Neither is built or imported by Yomu. Use their bookmark, search, and other feature implementations as references while preserving Yomu's `core/reader` boundary and design system; bookmarks and in-book search are already built here.

### `C:\Users\itexp\kotlin-toolkit\test-app` — the Readium reference app (the engine Yomu sits on)
The official **Readium Kotlin Toolkit** monorepo and its demo app (`org.readium.r2.testapp`, versioned in lockstep at **3.3.0** — the exact Readium version Yomu targets). It is the canonical, un-abstracted reference for the library under Yomu's reader. Style is the *opposite* of Yomu: classic **Views/Fragments/RecyclerView + Material**, hand-rolled DI (no Hilt), and Readium types used directly everywhere. Exercises the whole toolkit (EPUB / PDF via PDFium / audiobook via ExoPlayer+media3 / image-DiViNa / OPDS / LCP DRM / TTS / search / bookmarks / highlights / TOC / preferences).

References for maintaining Yomu's bookmark and search features:
- **Bookmarks** — Room entity stores the Readium `Locator` as JSON (`locations` + `text`); spine index via `publication.readingOrder.indexOfFirstWithHref(href)`; idempotency via a unique index + `OnConflictStrategy.IGNORE`; `BookmarksFragment` lists then returns a `Locator` → `navigator.go(locator)`.
- **In-book search** — `publication.search(query)` → `SearchIterator`, paged lazily with an AndroidX Paging 3 `SearchPagingSource`; hits rendered live in-text as `Decoration.Style.Underline` via `DecorableNavigator.applyDecorations(list, group)`.
- **Highlights** (Yomu's are built, but this is the textbook version) — text-selection `ActionMode` → `SelectableNavigator.currentSelection()` → Room `Highlight` → `Decoration` (group `"highlights"`); `Decoration.extras` round-trips the DB id so `onDecorationActivated` can look the row back up for tap-to-edit.
- Also worth knowing: multi-format navigator selection by `Publication.Profile`; custom font declaration at navigator-config time (same mechanism Yomu uses); the `createDummyFactory()` process-death guard — the same navigator-restoration crash class Yomu fixed in 07ac465.
- **Caveat for porting:** the test-app reaches into Readium from its ViewModels/Fragments. A Yomu port must thread these through `core/reader` (e.g. add `search()` / `applyDecorations()` to `ReaderSession`), not import Readium in `feature/reader`.

### `C:\Users\itexp\vaachak` — sibling indie Compose EPUB reader
Another single-module native EPUB reader (`io.github.piyushdaiya.vaachak`, v2.0.1) on the **same core stack** (Compose, Hilt, Room, DataStore, Coil, Readium) but **older versions**: Kotlin 2.0.21, AGP 8.11.1, Compose BOM 2024.05, **Readium 3.1.2** (vs Yomu's 3.3.0 — APIs are not always drop-in), minSdk 30, Room v9. It ships several things Yomu lists as pending, plus features Yomu has no plans for — so it's the most useful *feature* reference, but **not** an architecture reference.

- **Built features to study:** in-book search (Readium `SearchService`), highlights + page bookmarks (unified into one Room `HighlightEntity` discriminated by a string `tag`; bookmark identity by href + `abs(progression delta) < 0.01`), flattened TOC overlay, fragment-in-Compose with a `FragmentLifecycleCallbacks` re-apply-decorations-on-resume pattern.
- **Beyond Yomu's scope:** an **AI assistant** — Google **Gemini** (`gemini-2.5-flash`) for explain / spoiler-free character ID / chapter recap / session "recall", plus **Cloudflare Workers AI** for text→image (both BYO-key in DataStore); an **offline dictionary** — embedded `dictionary.json` (~20MB) + `inflections.json` lemma map + a from-scratch `StarDictParser` (`.ifo/.idx/.dict.dz`, 32/64-bit offsets, gzip via commons-compress); **catalog acquisition** — OPDS 1.x/2.0 (`readium-opds`) + **Gutendex** (Project Gutenberg) browse + in-app download; **accessibility fonts** (OpenDyslexic, iA Writer Duospace, accessible DfA) and a first-class **E-Ink** theme mode with a contrast slider.
- **Architectural contrasts (why not to copy its structure):** stock **Material3** with a single global `isEink` boolean instead of a design system; **no Readium boundary** (Readium types leak into Compose UI + ViewModels); **no `NavHost`** — a monolithic `MainActivity` holds all navigation as Compose state; the data layer even depends on a `ui` class (`LibraryRepository` → `ui.reader.ReadiumManager`); a ~550-line god `ReaderViewModel`.
- **Do not copy (security):** `app/build.gradle.kts` hardcodes the release **keystore password in plaintext** in the signing config; user AI secrets are stored unencrypted in DataStore; OPDS offers a trust-all-TLS path. Treat vaachak as a feature blueprint only.

## Agent skills

### Issue tracker

Issues are tracked in GitHub Issues for `itexpert120/yomu` using the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Use the default canonical triage labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, and `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

This repo uses the single-context domain-doc layout. See `docs/agents/domain.md`.

## Notes

- `index.html`, `script.js`, `styles.css` at the repo root are an exported IntelliJ inspection report — not application code; ignore them.
- `docs/roadmap.md` defines implementation phases and acceptance criteria; consult it before starting a new feature area.
- Code style is enforced with **Spotless + ktlint** (`./gradlew spotlessApply` to format, `spotlessCheck` to verify), configured in the root `build.gradle.kts` with `.editorconfig` (IntelliJ style; `function-naming`/`property-naming` disabled for Compose/design-system PascalCase). Kotlin sources are LF (`.gitattributes`).
- The version catalog also includes `androidx.core-splashscreen` and `kotlinx-serialization-json`. `INTERNET` is used for dictionary lookups and user-requested Google Fonts downloads; explicit Google web lookup opens the external browser.
