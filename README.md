# Yomu

A native Android **EPUB reader** built with Kotlin and Jetpack Compose, deliberately designed **not** to look like a default Material app — polished, reader-first, and tablet-minded, with a custom design system.

## Status

Yomu is a working reader, not a prototype. It has a real persisted library, book details, and a Readium-backed reading experience with deep appearance controls.

**Built**

- **Library** — Room-backed, SAF multi-file import (copied to app-private storage, sha256 dedup), Coil cover art, search, sort (Recent/Title/Author/Unread), group by author, adaptive grid columns (Auto + manual) and list view, multi-select with bulk actions, continue-reading hero.
- **Book details** — cover viewer (full-screen + save to gallery), metadata + edit, reading progress, a virtualized **table of contents** with persistent **per-chapter read state**, per-chapter and multi-select read/unread (including "mark up to here"), and jump-to-chapter.
- **Reader** — EPUB rendered via **Readium** behind a Yomu engine boundary; locator persisted/restored; full-screen immersive chrome (permanent sleek top bar + optional clock/battery footer); a controls sheet with **themes** (Light/Dark/Sepia/Black/Custom with background + text colours), **six bundled fonts** with live previews + size, **brightness**, and **scroll/paged**. Settings resolve as a global default overridden **per-book**.
- **Settings / About**, a custom design system, and a component **DevGallery**.

**Not yet** — bookmarks, highlights, in-book search, advanced typography (line/word/letter spacing, margins, justification, hyphenation, columns), Room FTS, performance profiling, reading stats. See [`docs/roadmap.md`](docs/roadmap.md).

## Tech

AGP 9.2.1 · Kotlin 2.4.0 · Java 17 (core-library desugaring) · Compose BOM 2026.06.00 · `compileSdk 37 / minSdk 24 / targetSdk 36`. Single `:app` module. **Hilt** DI · **Room** · **DataStore** · **Navigation Compose** (type-safe routes) · **Coil 3** · **Readium 3.3.0**. All versions live in `gradle/libs.versions.toml`.

## Build

Use the Gradle wrapper (`./gradlew` on Bash, `.\gradlew.bat` on PowerShell):

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build + install on a connected device/emulator
./gradlew :app:compileDebugKotlin  # fast compile-only check
./gradlew test                   # JVM unit tests
./gradlew lint                   # Android lint
```

## Structure & docs

The `:app` module is layered `core/` → `data/`/`domain/` → `feature/`, with the EPUB engine confined to `data/reader/readium`. See [`docs/`](docs/README.md) for the product, design language, architecture, data model, reader spec, and roadmap — each carries an "Implementation status (current)" note. Contributor guidance lives in [`CLAUDE.md`](CLAUDE.md).
