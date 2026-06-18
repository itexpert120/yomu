# Library Research

Last researched: 2026-06-18

This document records the initial library choices for a native Android EPUB reader. The goal is to move fast without locking the product UI to a third-party reader UI.

## Summary Recommendation

Use Readium Kotlin Toolkit as the EPUB engine, but hide it behind our own `ReaderEngine` interface. Use Jetpack Compose for the app chrome, library, panels, settings, and custom design system. Use Room for structured library data, DataStore for lightweight preferences, SAF for importing books, Coil for covers/background images, Hilt for dependency injection, and Kotlin coroutines/Flow for state.

The key architectural decision is that Yomu owns the UI. Readium should open, parse, navigate, locate, search, and render EPUB content. It should not define the app's visual language.

## EPUB Engine

### Recommended: Readium Kotlin Toolkit

Source links:

- https://readium.org/kotlin-toolkit/3.2.0/
- https://readium.org/kotlin-toolkit/3.2.0/guides/getting-started/
- https://readium.org/kotlin-toolkit/3.2.0/guides/navigator/navigator/
- https://github.com/readium/kotlin-toolkit

Why:

- Supports EPUB 2 and EPUB 3.
- Supports reflowable EPUB pagination and scrolling.
- Supports highlighting through decoration APIs.
- Provides `Locator` for stable reading positions, bookmarks, highlights, and search results.
- Provides publication metadata, cover extraction, table of contents, reading order, resources, and search/content services.
- Provides EPUB navigator preferences such as font, color, scrolling/paging, and layout behavior.
- Supports custom EPUB font families through served assets.
- Supports Android/ChromeOS, which fits tablet optimization.
- Latest researched stable docs show Readium Kotlin Toolkit 3.2.0.

Important constraints:

- Readium provides low-level reading tools, not a full bookshelf/library UI.
- Readium visual navigators are Android `Fragment` implementations, so Compose interop is required.
- Readium does not persist reading progress. The app must store the last locator in its own database.
- Readium does not treat reflowable EPUBs as fixed pages. It uses stable positions and total progression, which is the correct model for font/layout changes.
- Current Readium preference APIs are experimental, so wrap them in Yomu-owned setting models.
- Verify Kotlin/AGP compatibility during the dependency spike before committing to a version.

Initial modules to use:

- `org.readium.kotlin-toolkit:readium-shared`
- `org.readium.kotlin-toolkit:readium-streamer`
- `org.readium.kotlin-toolkit:readium-navigator`

Optional later modules:

- `readium-navigator-media-tts` for read-aloud / speed-adjacent workflows.
- `readium-opds` for catalog support later.
- `readium-lcp` only if DRM support becomes a product requirement.
- PDF/image navigator adapters only if the product expands beyond EPUB.

### Alternative: FolioReader Android

Source link:

- https://github.com/folioreader/folioreader-android

Why not first choice:

- It is closer to a bundled reader product than a clean low-level toolkit.
- It risks forcing its UI and internal architecture onto Yomu.
- It is useful as a reference for features, not as the core engine for a custom design language.

### Alternative: custom parser plus WebView/Compose renderer

Why not first choice:

- EPUB layout is more complex than ZIP plus HTML.
- Correct pagination, CFI/locator behavior, images, CSS, tables, links, RTL, writing modes, accessibility, and edge cases are expensive.
- A custom renderer should only be considered after Readium's limits are proven unacceptable.

## EPUB Validation

### Optional: EPUBCheck

Source links:

- https://github.com/w3c/epubcheck
- https://www.w3.org/publishing/epubcheck/docs/java-library/

Use case:

- Validate EPUB files during import or as a diagnostic tool.
- Produce user-facing warnings for broken books.

Recommendation:

- Do not include in the first reader prototype.
- Consider it for an advanced import validation phase because it can add dependency size and complexity.

## HTML/Text Processing

### Optional: jsoup

Source links:

- https://jsoup.org/
- https://jsoup.org/download

Use case:

- Extract text snippets for library search if Readium content extraction is insufficient.
- Clean or inspect XHTML for custom summaries, diagnostics, or indexing.

Recommendation:

- Prefer Readium content extraction first.
- Add jsoup only if our indexing/search pipeline needs direct HTML parsing.

## Android UI Stack

### Required: Jetpack Compose

Source links:

- https://developer.android.com/develop/ui/compose/architecture
- https://developer.android.com/develop/ui/compose/state-hoisting
- https://developer.android.com/develop/ui/compose/build-adaptive-apps

Use case:

- App shell, library, custom panels, controls, settings, reader chrome, animation, adaptive layouts, and previews.

Guidance:

- Use unidirectional data flow: state down, events up.
- Hoist state to the lowest owner that needs it.
- Keep business logic in ViewModels/domain/data, not composables.
- Build adaptive layouts for phone, tablet, foldable, landscape, and multi-window.

### Material 3

Use case:

- Material may remain as a dependency for base utilities or temporary internal controls.

Constraint:

- Do not expose default Material visual language in product-critical UI.
- Avoid default `Scaffold`, `TopAppBar`, Material-shaped cards, default sliders, default switches, and standard Material bottom sheets for final app surfaces.
- If a Material component is used, it must be visually wrapped or replaced before design freeze.

## Persistence

### Required: Room

Source link:

- https://developer.android.com/training/data-storage/room

Use case:

- Books.
- Authors.
- Series.
- Groups and nested groups.
- Book files.
- Book progress.
- Bookmarks.
- Highlights.
- Notes later.
- Per-book settings overrides.
- Theme records if user-created themes need queries.

Why:

- Structured local data.
- SQL query verification.
- Migration support.
- Works well with Flow.

### Required: DataStore

Source link:

- https://developer.android.com/topic/libraries/architecture/datastore

Use case:

- App-wide preferences.
- Last selected library view.
- Global appearance defaults.
- Quick toggles.
- Potentially serialized setting profiles.

Recommendation:

- Use Proto DataStore for typed preference structures if settings become complex.
- Use Room for relational settings or user-created theme records.
- Do not read/write DataStore directly from composables. Access through repositories/ViewModels.

## Import And File Access

### Required: Storage Access Framework

Source link:

- https://developer.android.com/training/data-storage/shared/documents-files

Use case:

- Pick EPUB files.
- Pick folders later.
- Import custom background images.
- Import custom fonts.

Recommendation:

- Use `ACTION_OPEN_DOCUMENT` for one or more EPUB files.
- Copy imported EPUBs into app-private storage for stable access, hashing, indexing, and deletion control.
- Store the original source URI only as metadata.
- Use persisted URI permissions only where copying is intentionally avoided.

## Image Loading

### Required: Coil 3

Source links:

- https://github.com/coil-kt/coil
- https://coil-kt.github.io/coil/compose/

Use case:

- Book covers.
- Background images.
- Theme preview images.
- Cached local images.

Recommendation:

- Use `coil-compose`.
- Keep cover extraction in data/import pipeline.
- Feed Compose stable local file/URI models.

## Dependency Injection

### Recommended: Hilt

Source links:

- https://developer.android.com/training/dependency-injection/hilt-android
- https://developer.android.com/training/dependency-injection

Use case:

- Repositories.
- DAOs.
- Reader engine factory.
- Import workers.
- Settings stores.
- File storage abstractions.

Why:

- Official Android recommendation.
- Compile-time DI graph checks.
- Good ViewModel integration.

## Navigation

### Recommended: Navigation Compose with type-safe routes

Source link:

- https://developer.android.com/guide/navigation/design/type-safety

Use case:

- Library.
- Reader.
- Book details.
- Settings.
- Appearance studio.

Recommendation:

- Use serializable route objects.
- Keep reader session identifiers lightweight in routes. Do not pass full book/session objects through navigation.

## Background Work

### Recommended: WorkManager

Source links:

- https://developer.android.com/develop/background-work/background-tasks
- https://developer.android.com/develop/background-work/background-tasks/persistent

Use case:

- Import processing.
- Cover extraction.
- Metadata extraction.
- Library indexing.
- EPUB validation later.
- Optional background cleanup.

Recommendation:

- Initial import can be foreground/in-process for simplicity.
- Move long-running import/index work to WorkManager when library management becomes real.

## Large Library Lists

### Optional: Paging 3

Source link:

- https://developer.android.com/topic/libraries/architecture/paging/v3-overview

Use case:

- Very large libraries.
- Author/series/group listings backed by Room.

Recommendation:

- Not required for the first prototype.
- Add when library data size makes normal Room Flow lists inefficient.

## Serialization

### Recommended: kotlinx.serialization

Source links:

- https://kotlinlang.org/docs/serialization.html
- https://github.com/Kotlin/kotlinx.serialization

Use case:

- Type-safe navigation route objects.
- Theme import/export.
- Reader setting profiles.
- Serialized Readium preferences/locators where appropriate.

## I/O Helpers

### Optional: Okio

Source links:

- https://square.github.io/okio/
- https://github.com/square/okio

Use case:

- Hashing files.
- Efficient file copying.
- Stream handling for imports.

Recommendation:

- Use if Android platform APIs become verbose or error-prone.
- Avoid unnecessary abstraction until import code needs it.

## Testing And Performance

### Recommended: AndroidX test stack

Use cases:

- Unit tests for domain/data logic.
- Compose UI tests for custom controls.
- Reader engine fake tests.
- Room migration tests.

### Recommended later: Macrobenchmark and Baseline Profiles

Source link:

- https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview

Use case:

- Startup time.
- Library scroll jank.
- Reader open latency.
- Settings panel animation jank.

## Initial Dependency Spike

Before implementation, run a short spike that answers:

- Can current project Kotlin/AGP consume Readium 3.2.0 from Maven Central without metadata/compiler issues?
- Can `EpubNavigatorFragment` be hosted cleanly inside a Compose reader screen?
- Can we observe `currentLocator` and persist/restore it?
- Can we submit typography/theme preferences without fighting Readium?
- Can edge taps and scrolling behavior be fully controlled by Yomu chrome?

If Readium 3.2.0 conflicts with the current Kotlin setup, either update Kotlin/AGP deliberately or pin to the latest compatible Readium release after testing.
