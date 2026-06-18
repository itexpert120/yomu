# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

Yomu is a native Android EPUB reader (Kotlin + Jetpack Compose). The product intent is a polished, reader-first, tablet-optimized app that deliberately does **not** look like a default Material app. See `docs/` for the full product/design/architecture specs — `docs/README.md` is the entry point.

Current state: a **working EPUB reader**. One `:app` module contains the custom design system, a real Room-backed library (SAF import, Coil covers), a book-details screen, and a Readium-backed reader with themes/fonts/brightness. **Hilt, Room, DataStore, Navigation Compose, Coil, and Readium are all present and wired.** Built but **not yet done**: bookmarks, highlights, in-book search, advanced typography. The `docs/` describe the product/design intent; each doc has an "Implementation status (current)" note where the build has caught up to or diverged from the plan.

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
│                                #   navigator); edge-to-edge; hosts the nav host
├── YomuApplication.kt           # @HiltAndroidApp
├── app/                         # shell: navigation/{YomuNavHost, YomuDestinations}, di/*, AppViewModel,
│                                #   EdgeToEdge, devgallery
├── core/
│   ├── designsystem/            # custom design system (Yomu* primitives, CompositionLocals)
│   ├── model/                   # Book, ReaderSettings, LibraryPreferences, AccentColor, …
│   ├── database/                # Room: YomuDatabase (v3), BookEntity, ChapterReadEntity,
│   │                            #   ReaderSettingsEntity, BookDao, migrations
│   ├── datastore/ · storage/    # DataStore prefs ; FileStorage (app-private epubs/covers)
│   └── reader/                  # ReaderEngine/ReaderSession/ReaderLocator/ReaderTocItem (no Readium types)
├── data/
│   ├── books/                   # BookRepository + RoomBookRepository + mappers
│   ├── reader/readium/          # ReadiumReaderEngine — the ONLY package that imports Readium
│   └── settings/                # AppSettingsRepository, LibraryPrefsRepository, ReaderSettingsRepository
├── domain/imports/              # ImportBooksUseCase (SAF import pipeline)
└── feature/                     # library, bookdetails, bookedit, reader, settings, about
```
Type-safe nav destinations: `Library`, `BookDetails(bookId)`, `EditBook(bookId)`, `Settings`, `About`, `Reader(bookId, locator?)`. Still unbuilt feature areas (bookmarks, in-book search) can be added following `docs/app-architecture.md`.

### Design system is the foundation — use it, don't reach for Material
`core/designsystem` defines the visual language. Theming is delivered through **CompositionLocals**, not `MaterialTheme`:
- `YomuDesignTheme { ... }` wraps the app and provides colors/type/spacing/radius.
- Access tokens inside composables via the `YomuTheme` object: `YomuTheme.colors`, `YomuTheme.type`, `YomuTheme.space`, `YomuTheme.radius`.
- Token data classes: `YomuColors`, `YomuType`, `YomuSpacing`, `YomuRadius` (all `@Immutable`). Theme variants: `YomuThemeMode.{Light, Dark, Oled}`.
- Build UI from the `Yomu*` primitives in `YomuSurfaces.kt`, `YomuControls.kt`, `YomuCards.kt`, `YomuReusable.kt` (e.g. `YomuAppSurface`). Do **not** introduce Material `Scaffold`/`MaterialTheme`/raw Material components as the app shell or product surface. `material3`/`material-icons-extended` are on the classpath only as building blocks for the custom system.
- The design system package must not depend on `feature/*`.

### Theme ↔ system bars
`MainActivity` owns the window insets controller and flips status/nav bar icon appearance on theme-mode change so bar icons stay legible. The **reader** additionally takes over the system bars while open (`ReaderScreen`): it hides both bars for full-screen reading, colours them to the reading theme, and restores them on exit.

### Reader engine boundary
The EPUB engine is Readium, but Readium types must **not** leak. All reader access goes through Yomu-owned interfaces in `core/reader` (`ReaderEngine`, `ReaderSession`, `ReaderLocator`, `ReaderTocItem`); only `data/reader/readium/ReadiumReaderEngine` imports Readium directly. The Readium navigator is an `EpubNavigatorFragment` hosted inside Compose by `feature/reader/ReaderNavigatorHost` (commits the fragment on attach, consumes window insets for edge-to-edge). `ReaderSettings` → `EpubPreferences` mapping (scroll, fontSize, theme, bg/text colour, fontFamily, lineHeight, `publisherStyles = false`) and navigation live in the engine. Reader settings resolve as a **global default (DataStore) ⊕ per-book override (Room `reader_settings`)**, written per-book-on-edit. Bundled reading fonts are in `app/src/main/assets/fonts/` and registered with Readium. See `docs/app-architecture.md` and `docs/reader-feature-spec.md`.

## Conventions

- Compose state flows down (immutable UI-state data classes), events flow up (explicitly named `On*` events). Keep composables stateless except for small ephemeral UI state.
- Do not pass repositories into composables, and do not run import/database/DataStore/Readium work from composables.
- Name composables by product role, not the widget they render.
- Comment only non-obvious behavior.
- Use `@Preview` composables for design iteration; the `app/devgallery` gallery exists to validate design primitives in isolation.

## Notes

- `index.html`, `script.js`, `styles.css` at the repo root are an exported IntelliJ inspection report — not application code; ignore them.
- `docs/roadmap.md` defines implementation phases and acceptance criteria; consult it before starting a new feature area.
