# Yomu Planning Docs

Yomu is a native Android EPUB reader built with Kotlin and Jetpack Compose, but it should not look like a default Material Android app. These docs define the product, design language, architecture, build patterns, reader model, data model, and implementation phases before feature work begins.

## Current Project State

The repository contains a single Android application module at `:app` with a custom design system and a static library screen using fake data.

Current technical baseline:

- Android application module: `:app`
- Package namespace: `com.itexpert120.yomu`
- UI toolkit: Jetpack Compose
- Custom design system: `core/designsystem` (YomuDesignTheme, custom tokens, surface/control primitives)
- Library screen: static library grid with fake books, continue-reading section, book context panel
- DevGallery: component gallery for validating design primitives (`app/devgallery`)
- Edge-to-edge support with theme-aware system bar icons
- compileSdk: 37, minSdk: 24, targetSdk: 36

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

## Non-Goals For The First Build

- Do not build a custom EPUB renderer from scratch before validating Readium.
- Do not implement every advanced setting before the reading surface is stable.
- Do not over-modularize the Gradle project before the first working reader prototype.
- Do not let Material components define the visible product identity.

## First Engineering Goal

Build a static Compose prototype using fake books and fake chapter text, backed by the custom design primitives. EPUB parsing/rendering should come after the app language and reader interaction model are visible.
