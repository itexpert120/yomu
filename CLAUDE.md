# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

Yomu is a native Android EPUB reader (Kotlin + Jetpack Compose). The product intent is a polished, reader-first, tablet-optimized app that deliberately does **not** look like a default Material app. See `docs/` for the full product/design/architecture specs — `docs/README.md` is the entry point.

Current state: a **mature, feature-rich EPUB reader** — well past a minimal MVP. One `:app` module contains the custom design system, a Room-backed library (SAF import, Coil covers, search/sort/group, multi-select bulk actions, continue-reading hero), book details (two-pane on tablets, virtualized TOC with per-chapter read tracking and "mark to here"), and a Readium-backed reader with themes/fonts/brightness/extra-dim, in-reader TOC, **highlights**, **advanced typography** (line height, page margins, paragraph spacing, alignment), **dictionary word-lookup + TTS**, footnote popups, and saved custom reader themes. Also built: a **reading-statistics** dashboard (Vico charts + GitHub-style activity heatmap + session history), **external "Open with"/share** EPUB import, and two **Glance home-screen widgets** (library grid + activity heatmap — present but currently disabled in the manifest). **Hilt, Room, DataStore, Navigation Compose, Coil, Vico (charts), Glance (widgets), and Readium are all present and wired.**

Genuinely **not yet built**: **bookmarks** and **in-book search** (no persistence, no UI) — these are the real remaining gaps. The only network feature is the dictionary lookup (Free Dictionary API; the reason for the `INTERNET` permission); everything else is offline. The `docs/` describe the product/design intent and are kept current — each doc has an "Implementation status (current)" note, and `docs/roadmap.md` (Phases 0–12) is the authoritative done-vs-pending source. **Keep this CLAUDE.md in sync with the code as features land.**

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

Toolchain: AGP 9.2.1, Kotlin 2.4.0, Compose BOM 2026.06.00, **Java 17** (+ core-library desugaring), KSP. `compileSdk 37 / minSdk 24 / targetSdk 36`. DI is Hilt (with `hilt { enableAggregatingTask = false }` — a workaround for Kotlin 2.4.0 metadata vs Hilt's javac aggregator); Room uses KSP with schema export to `app/schemas`. The app theme parent is `Theme.AppCompat.DayNight.NoActionBar` so it can host the Readium navigator Fragment.

## Architecture

### Package layout (inside the single `:app` module)
```
com.itexpert120.yomu
├── MainActivity.kt              # @AndroidEntryPoint, extends FragmentActivity (hosts the Readium
│                                #   navigator); splash; edge-to-edge; external-open + widget deep links
├── YomuApplication.kt           # @HiltAndroidApp
├── app/                         # shell: YomuApp (theme resolution), navigation/{YomuNavHost,
│                                #   YomuDestinations}, di/*, AppViewModel, ExternalOpenViewModel,
│                                #   EdgeToEdge, devgallery
├── core/
│   ├── designsystem/            # custom design system (Yomu* primitives, CompositionLocals, HSV color
│   │                            #   picker, responsive YomuWidthClass)
│   ├── model/                   # Book, ReaderSettings, LibraryPreferences, AccentColor, ThemePreference,
│   │                            #   CustomReaderTheme, ReadingStats, …
│   ├── database/                # Room: YomuDatabase (v8) + BookEntity, ChapterReadEntity,
│   │                            #   ReaderSettingsEntity, BookTocEntity, ReadingDayEntity,
│   │                            #   ReadingSessionEntity, HighlightEntity, BookDao, HighlightDao, migrations
│   ├── datastore/ · storage/    # DataStore prefs ; FileStorage (app-private epubs/covers)
│   └── reader/                  # ReaderEngine/ReaderSession/ReaderLocator/ReaderTocItem/ReaderHighlight
│                                #   (Yomu-owned; no Readium types)
├── data/
│   ├── books/                   # BookRepository + RoomBookRepository + mappers
│   ├── reader/readium/          # ReadiumReaderEngine (+ ReadiumMetadataExtractor, ReadiumFragmentRestore)
│   │                            #   — the ONLY package that imports Readium
│   ├── settings/                # AppSettingsRepository, LibraryPrefsRepository, ReaderSettingsRepository
│   ├── highlights/              # HighlightRepository + RoomHighlightRepository
│   ├── stats/                   # StatsRepository (reading-time sessions + derived stats/heatmap)
│   └── dictionary/              # DictionaryRepository (Free Dictionary API — the only network call)
├── domain/imports/              # ImportBooksUseCase (SAF + external-open import pipeline)
├── feature/                     # library, bookdetails, bookedit, reader, settings, stats, about
└── widget/                      # Glance home-screen widgets (library grid + activity heatmap; disabled)
```
Type-safe nav destinations: `Library`, `BookDetails(bookId)`, `EditBook(bookId)`, `Settings`, `Stats`, `ReaderDefaults`, `About`, `Reader(bookId, locator?)`. The genuinely-unbuilt feature areas (**bookmarks**, **in-book search**) can be added following `docs/app-architecture.md` — see the sibling Readium test-app under "Related projects" below for worked reference implementations.

### Design system is the foundation — use it, don't reach for Material
`core/designsystem` defines the visual language. Theming is delivered through **CompositionLocals**, not `MaterialTheme`:
- `YomuDesignTheme { ... }` wraps the app and provides colors/type/spacing/radius.
- Access tokens inside composables via the `YomuTheme` object: `YomuTheme.colors`, `YomuTheme.type`, `YomuTheme.space`, `YomuTheme.radius`.
- Token data classes: `YomuColors`, `YomuType`, `YomuSpacing`, `YomuRadius` (all `@Immutable`). Theme variants: `YomuThemeMode.{Light, Dark, Oled}`.
- Build UI from the `Yomu*` primitives in `YomuSurfaces.kt`, `YomuControls.kt`, `YomuCards.kt`, `YomuReusable.kt` (e.g. `YomuAppSurface`). Do **not** introduce Material `Scaffold`/`MaterialTheme`/raw Material components as the app shell or product surface. `material3`/`material-icons-extended` are on the classpath only as building blocks for the custom system.
- Controls are hand-drawn (Canvas/gestures, ripples suppressed): segmented control, range/slider rows, toggle pill, an HSV `YomuColorPicker`. `YomuColors` also carries four highlight colors; accent supports both presets (`AccentColor`) and arbitrary custom ARGB. Tablet/expanded layouts use the `YomuWidthClass` responsive breakpoints + `YomuContentMaxWidth`.
- The design system package must not depend on `feature/*`.

### Theme ↔ system bars
`MainActivity` owns the window insets controller and flips status/nav bar icon appearance on theme-mode change so bar icons stay legible. The **reader** additionally takes over the system bars while open (`ReaderScreen`): it hides both bars for full-screen reading, colours them to the reading theme, and restores them on exit.

### Reader engine boundary
The EPUB engine is Readium, but Readium types must **not** leak. All reader access goes through Yomu-owned interfaces in `core/reader` (`ReaderEngine`, `ReaderSession`, `ReaderLocator`, `ReaderTocItem`, `ReaderHighlight`); only `data/reader/readium/*` imports Readium directly — `ReadiumReaderEngine` (reading), `ReadiumMetadataExtractor` (import-time metadata/cover), and `ReadiumFragmentRestore` (the config-change/process-death navigator-restoration guard; see commit 07ac465). The Readium navigator is an `EpubNavigatorFragment` hosted inside Compose by `feature/reader/ReaderNavigatorHost` (commits the fragment on attach, consumes window insets for edge-to-edge). `ReaderSettings` → `EpubPreferences` mapping (scroll/paged, fontSize, theme, bg/text colour, fontFamily, lineHeight/margins/paragraph-spacing, `publisherStyles = false`) and navigation live in the engine. The engine surfaces center-taps, dictionary look-up requests, footnotes, and highlight selection/taps via `SharedFlow`s, and renders highlights as Readium `Decoration`s. Reader settings resolve as a **global default (DataStore) ⊕ per-book override (Room `reader_settings`)**, written per-book-on-edit — the global default is edited on the `ReaderDefaults` screen. Six reading fonts are bundled in `app/src/main/assets/fonts/` and registered with Readium. The reader additionally owns reading-time tracking (writes `reading_sessions` via `StatsRepository`), TTS read-aloud, and extra-dim/brightness. See `docs/app-architecture.md` and `docs/reader-feature-spec.md`.

## Conventions

- Each feature follows a **Route (stateful, `hiltViewModel()`) → Screen (stateless, `On*` callbacks)** split; the `Screen` is preview-driven.
- Compose state flows down (immutable UI-state data classes), events flow up (explicitly named `On*` events). Keep composables stateless except for small ephemeral UI state.
- Do not pass repositories into composables, and do not run import/database/DataStore/Readium work from composables.
- Name composables by product role, not the widget they render.
- The Book Details TOC must stay a `LazyColumn` (can be thousands of entries) **with no item key** — chapter hrefs can legitimately repeat and a keyed list would crash.
- Comment only non-obvious behavior.
- Use `@Preview` composables for design iteration; the `app/devgallery` gallery exists to validate design primitives in isolation.

## Related projects (local siblings — reference material, not dependencies)

Two other repos live next to Yomu on this machine. Neither is built or imported by Yomu, but both are high-value references — especially for Yomu's genuinely-missing features (bookmarks, in-book search). Mine them for **feature logic**, but re-implement behind Yomu's `core/reader` boundary and custom design system rather than copying verbatim.

### `C:\Users\itexp\kotlin-toolkit\test-app` — the Readium reference app (the engine Yomu sits on)
The official **Readium Kotlin Toolkit** monorepo and its demo app (`org.readium.r2.testapp`, versioned in lockstep at **3.3.0** — the exact Readium version Yomu targets). It is the canonical, un-abstracted reference for the library under Yomu's reader. Style is the *opposite* of Yomu: classic **Views/Fragments/RecyclerView + Material**, hand-rolled DI (no Hilt), and Readium types used directly everywhere. Exercises the whole toolkit (EPUB / PDF via PDFium / audiobook via ExoPlayer+media3 / image-DiViNa / OPDS / LCP DRM / TTS / search / bookmarks / highlights / TOC / preferences).

Best copy-from source for **Yomu's two missing features**:
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

## Notes

- `index.html`, `script.js`, `styles.css` at the repo root are an exported IntelliJ inspection report — not application code; ignore them.
- `docs/roadmap.md` defines implementation phases and acceptance criteria; consult it before starting a new feature area.
- The two Glance widgets in `widget/` are committed but currently **disabled** in `AndroidManifest.xml` (`android:enabled="false"`) — re-enable the receivers to ship them.
- The version catalog also includes **Vico** (stats charts), **Glance** (widgets), `androidx.core-splashscreen`, and `kotlinx-serialization-json`. The `INTERNET` permission exists solely for the dictionary lookup; the app is otherwise offline.
