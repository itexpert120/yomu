# Android Build Patterns

This document defines how Yomu should be built as the app grows from a single Compose template into a serious EPUB reader.

## Current Build Baseline

Current files:

- `settings.gradle.kts`
- `build.gradle.kts`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`

Current module:

- `:app`

Current stack:

- Android Gradle Plugin declared in version catalog.
- Kotlin Compose plugin declared in version catalog.
- Compose enabled in `:app`.
- Material 3 currently present from the template.

## Build Philosophy

- Use Gradle version catalogs for all external dependency versions.
- Keep dependency declarations grouped by capability.
- Prefer KSP over kapt where supported.
- Do not create many Gradle modules until package boundaries are stable.
- Keep design system compile dependencies minimal.
- Treat the reader engine adapter as replaceable.
- Add benchmark/profile tooling after the UI prototype starts to stabilize.

## Initial Single-Module Package Pattern

Before Gradle modularization, enforce internal boundaries with packages:

```text
app/src/main/java/com/itexpert120/yomu/
|-- app/
|-- core/
|-- data/
|-- domain/
`-- feature/
```

Rules:

- `feature/*` can depend on `core/designsystem`, `core/model`, and ViewModels/use cases.
- `core/designsystem` cannot depend on feature packages.
- `data/reader/readium` is the only package allowed to directly import Readium APIs.
- `data/database` is the only package allowed to define Room DAOs/entities.
- `data/datastore` is the only package allowed to access DataStore directly.

## Future Multi-Module Pattern

When splitting modules, use feature and layer modules together:

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
:feature:appearance
:feature:settings
:feature:bookdetails
:benchmark
```

Module dependency examples:

```text
:feature:reader -> :core:designsystem
:feature:reader -> :core:reader-api
:feature:reader -> :domain:reader
:data:reader-readium -> :core:reader-api
:data:reader-readium -> Readium libraries
:app -> all feature modules
```

Avoid:

- Feature modules depending on other feature modules directly.
- Design system depending on app/data/domain.
- Readium imports outside `data:reader-readium` or current single-module equivalent.

## Version Catalog Structure

Organize `gradle/libs.versions.toml` like this when dependencies are added:

```toml
[versions]
agp = "..."
kotlin = "..."
composeBom = "..."
readium = "..."
room = "..."
hilt = "..."
datastore = "..."
navigation = "..."
coil = "..."
work = "..."
ksp = "..."
serialization = "..."

[libraries]
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
readium-shared = { group = "org.readium.kotlin-toolkit", name = "readium-shared", version.ref = "readium" }
readium-streamer = { group = "org.readium.kotlin-toolkit", name = "readium-streamer", version.ref = "readium" }
readium-navigator = { group = "org.readium.kotlin-toolkit", name = "readium-navigator", version.ref = "readium" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

## Compose Patterns

Use:

- Stateless composables where practical.
- State holders/ViewModels for screen state.
- Preview parameter providers for design iteration.
- Custom primitives from `core/designsystem`.
- `LazyVerticalGrid`/adaptive grid for library views.
- Custom layouts only when needed for reader/page behavior.

Avoid:

- Passing repositories into composables.
- Running import/database work from composables.
- Reading DataStore directly from composables.
- Calling Readium APIs directly from arbitrary UI components.
- Using Material `Scaffold` as the permanent app shell.

## Reader Interop Pattern

Because Readium visual navigators are fragments, use a thin interop layer.

Preferred boundary:

```text
feature/reader
|-- ReaderRoute.kt
|-- ReaderScreen.kt
|-- ReaderViewModel.kt
|-- ReaderNavigatorHost.kt
`-- ReaderChrome.kt

data/reader/readium
|-- ReadiumReaderEngine.kt
|-- ReadiumReaderSession.kt
|-- ReadiumNavigatorController.kt
`-- ReadiumPreferenceMapper.kt
```

`ReaderNavigatorHost` responsibilities:

- Host the Readium fragment.
- Forward lifecycle-safe callbacks to controller/ViewModel.
- Stay visually invisible except for publication content.

Not responsible for:

- Bookmarks UI.
- Settings UI.
- Theme UI.
- Library navigation.
- Database writes.

## Hilt Pattern

Introduce Hilt when these exist:

- Room database.
- Repositories.
- Reader engine implementation.
- File storage implementation.
- Import use cases.

Suggested components:

- `DatabaseModule`
- `DataStoreModule`
- `RepositoryModule`
- `ReaderEngineModule`
- `StorageModule`
- `DispatcherModule`

Guidelines:

- Inject dispatchers for testability.
- Keep Hilt annotations out of pure model/design classes.
- Prefer constructor injection.
- Use fake repositories/engines in tests.

## Data Persistence Pattern

Room:

- Structured and relational data.
- Books, authors, series, groups, bookmarks, highlights, progress, files.
- Migrations required from version 1 onward.

DataStore:

- App-level preferences.
- Reader default settings if stored as a typed profile.
- Last library display preference.
- Do not use for relational library data.

Files:

- EPUBs in app-private file storage.
- Covers in app-private image cache/storage.
- Custom fonts in app-private or assets-like managed storage.
- Custom theme background images in app-private storage.

## Import Pattern

1. User selects file with SAF.
2. App opens URI stream.
3. App copies EPUB into app-private storage.
4. App computes hash.
5. App checks duplicate hash.
6. App opens metadata through reader engine/import extractor.
7. App extracts cover if available.
8. App writes Room transaction.
9. App indexes text later if search requires it.

Do not rely on raw filesystem paths from external storage.

## Reader Settings Pattern

Define Yomu setting models first:

- `FontSettings`
- `ParagraphSettings`
- `PageSettings`
- `StatusSettings`
- `ThemeSettings`
- `ReadingModeSettings`

Resolve them into engine settings:

```text
Yomu settings -> ResolvedReaderSettings -> Readium preference mapper -> Readium preferences
```

Store raw Yomu settings, not Readium implementation details, wherever possible.

Exception:

- Store serialized Readium `Locator` JSON if that is the most accurate representation for navigation positions.

## Testing Pattern

Unit test packages:

```text
src/test/java/.../domain
src/test/java/.../data
src/test/java/.../core
```

Android test packages:

```text
src/androidTest/java/.../database
src/androidTest/java/.../reader
src/androidTest/java/.../feature
```

Recommended fakes:

- `FakeReaderEngine`
- `FakeReaderSession`
- `FakeBookRepository`
- `FakeSettingsRepository`
- `FakeThemeRepository`

Test priorities:

- Settings resolution before UI wiring.
- Import duplicate detection before bulk import.
- Room migrations before shipping any schema update.
- Reader state restoration before advanced reader settings.

## Performance Pattern

Critical performance paths:

- App startup.
- Library grid scrolling.
- Book open time.
- Reader pagination/navigation latency.
- Settings panel animation.
- Theme changes.
- Search/indexing.

Build later:

- Macrobenchmark module.
- Baseline profile generation.
- Sample large library fixture.
- Sample large EPUB fixture.
- Scroll jank benchmarks.

## Code Style Rules

- Keep composables small and named by product role, not implementation widget.
- Prefer immutable data classes for UI state.
- Use explicit event names.
- Keep use cases single-purpose.
- Keep repositories free of UI models.
- Keep mapping functions close to boundaries.
- Add comments only for non-obvious behavior.

## Implementation Order

1. Create design system package.
2. Create fake data models and fake repositories.
3. Build static library and reader screens.
4. Add settings models and resolver.
5. Add Room schema.
6. Add import pipeline.
7. Add Readium spike behind reader engine boundary.
8. Replace fake reader content with real EPUB session.
9. Add bookmarks/highlights/progress persistence.
10. Add advanced modes and performance work.
