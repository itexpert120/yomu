# Yomu Planning Docs

Yomu is a native Android EPUB reader built with Kotlin and Jetpack Compose, but it should not look like a default Material Android app. These docs define the product, design language, architecture, build patterns, reader model, data model, and implementation phases. They began as a pre-build plan; several sections describe a destination that is now substantially built — each doc carries an "Implementation status (current)" note where it has diverged from the plan.

## Current Project State

Yomu is now a **working EPUB reader**, not a static prototype. A single `:app` module contains the custom design system plus a real, persisted library and a Readium-backed reader.

Current technical baseline:

- Android application module: `:app`, namespace `com.itexpert120.yomu`, Jetpack Compose UI
- Toolchain: AGP 9.2.1, Kotlin 2.4.0, Java 17 (+ core-library desugaring), Compose BOM 2026.06.00, KSP; compileSdk 37 / minSdk 24 / targetSdk 36
- DI: **Hilt** (`@HiltAndroidApp`, `@HiltViewModel`, modules in `app/di/`)
- Persistence: **Room** (`books`, `chapter_reads`, `reader_settings`; migrations 1→3) and **DataStore** (app + library + global reader settings)
- Navigation: **Navigation Compose** with type-safe `@Serializable` routes and Material shared-axis (X) transitions
- Reader engine: **Readium 3.3.0** behind a Yomu `ReaderEngine` boundary (only `data/reader/readium` imports Readium); `EpubNavigatorFragment` hosted in Compose
- Images: **Coil 3** for covers; **SAF** import with sha256 dedup
- Custom design system: `core/designsystem` (`YomuDesignTheme`, tokens, surface/control/card primitives) applied across every screen
- Features: library (grid/list, search, sort, group, adaptive columns, multi-select, import), book details (TOC + persistent per-chapter read-state, multi-select, cover viewer + save-to-gallery, edit), reader (themes, six bundled fonts, brightness, scroll/paged, full-screen chrome, global + per-book settings), settings, about
- DevGallery component harness (`app/devgallery`); edge-to-edge with theme-aware system bar icons; real app launcher icon

**Not yet built:** bookmarks, highlights, in-book search, advanced typography (line/word/letter spacing, margins, justification, hyphenation, columns), Room FTS, performance profiling, reading stats. See the [Roadmap](roadmap.md) for status per phase.

## Planning Documents

- [Library Research](library-research.md): EPUB, Android, UI, storage, persistence, image, search, and testing library decisions.
- [Design Language](design-language.md): visual direction, tokens, custom primitives, motion, responsive behavior, and anti-Material rules.
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
- Custom visual system, not Material-shaped UI.
- Smooth, restrained animations.
- Fast library browsing inspired by media apps.
- Deep typography and theme controls for serious readers.

## Non-Goals

- Do not build a custom EPUB renderer from scratch — Readium is validated and in use.
- Do not implement every advanced setting before the reading surface is stable.
- Do not over-modularize the Gradle project; the single `:app` module is intentional for now.
- Do not let Material components define the visible product identity.

## Next Engineering Goal

With the library, book details, and a themeable Readium-backed reader working, the next focus is finishing the core reader for daily use: an in-reader Contents/Bookmarks panel, bookmarks, highlights, and in-book search (Roadmap Phase 7), followed by advanced typography (Phase 8).
