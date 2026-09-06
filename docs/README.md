# Open Reader Planning Docs

Open Reader is a native Android EPUB reader built with Kotlin, Jetpack Compose, and Material 3 Expressive, with reader-specific comfort surfaces. These docs distinguish implemented behavior from future product, architecture, and performance targets; consult each document's current-status section before using its planning sections.

## Current Project State

Open Reader is now a **working EPUB reader**, not a static prototype. The `:app` module contains the custom design system plus a real, persisted library and a Readium-backed reader; `:benchmark` contains macrobenchmark harnesses and generated EPUB fixtures.

Current technical baseline:

- Android application module: `:app`, namespace `com.itexpert120.yomu`, Jetpack Compose UI
- Toolchain: Java 17 with core-library desugaring, Kotlin, Compose Material 3 Expressive, and KSP. Use `gradle/libs.versions.toml` and `app/build.gradle.kts` for current versions and SDK levels.
- DI: **Hilt** (`@HiltAndroidApp`, `@HiltViewModel`, modules in `app/di/`)
- Persistence: **Room v16**, including independent lifetime reading totals and session-write receipts, plus compatibility-only v13 sync tables; additive migrations 1→16 and **DataStore**. See [Data Model](data-model.md).
- Navigation: **Navigation Compose** with type-safe `@Serializable` routes, seamless horizontal
  screen transitions, and ordinary back callbacks for local transient states
- Reader engine: **Readium 3.3.0** behind a Yomu `ReaderEngine` boundary (only `data/reader/readium` imports Readium); `EpubNavigatorFragment` hosted in Compose
- Images: **Coil 3** for covers; **SAF** import with sha256 dedup
- Custom design system: `core/designsystem` (`YomuDesignTheme`, tokens, surface/control/card primitives) applied across every screen
- Features: mature library and book details; reader with contents, bookmarks, highlights, full-text search, dictionary/TTS, advanced typography, custom themes/fonts, and immersive chrome; settings, about, and reading statistics with history
- DevGallery component harness (`app/devgallery`); edge-to-edge with theme-aware system bar icons; real app launcher icon

**Not yet built:** OPDS catalogs, non-EPUB formats, cross-device sync, Room FTS metadata search, and baseline profiles. Reader-open tracing and fixture-backed launch benchmarks are implemented; reference-hardware performance targets remain unmeasured. See [Reader Benchmarks](reader-benchmarks.md) and the [Roadmap](roadmap.md).

## Planning Documents

- [Library Research](library-research.md): EPUB, Android, UI, storage, persistence, image, search, and testing library decisions.
- [Design Language](design-language.md): Material 3 Expressive direction, compatibility tokens, reader-specific surfaces, motion, and adaptive behavior.
- [App Architecture](app-architecture.md): layers, package/module boundaries, UI state flow, reader engine boundary, and feature ownership.
- [Android Build Patterns](android-build-patterns.md): Gradle/module conventions, dependency governance, Compose conventions, DI, testing, and performance setup.
- [Reader Feature Spec](reader-feature-spec.md): reader UI, reading modes, settings panels, TOC, highlights, bookmarks, progress, themes, and quick actions.
- [Data Model](data-model.md): entities, relationships, settings layering, theme model, and persistence strategy.
- [Roadmap](roadmap.md): implementation phases and acceptance criteria.

## North Star

The app should feel like a polished custom-native reading product:

- Clean and minimalist.
- Strongly reader-first.
- Tablet optimized from the start.
- Native Material 3 Expressive UI with reader-specific comfort surfaces.
- Smooth, restrained animations.
- Fast library browsing inspired by media apps.
- Deep typography and theme controls for serious readers.

## Non-Goals

- Do not build a custom EPUB renderer from scratch — Readium is validated and in use.
- Do not implement every advanced setting before the reading surface is stable.
- Do not over-modularize the Gradle project; the single `:app` module is intentional for now.
- Preserve EPUB comfort while using native Material interactions for app chrome.

## Next Engineering Goal

The next focus is performance hardening and optional expansion such as OPDS, additional formats, and sync, without weakening the offline-first reader experience.
