# Open Reader

A native Android **EPUB reader** built with Kotlin and Jetpack Compose — polished, reader-first, and tablet-minded, using Material 3 Expressive through an app-owned design system.

## Status

Open Reader is a working reader, not a prototype. It has a real persisted library, book details, and a Readium-backed reading experience with deep appearance controls.

**Built**

- **Library** — Room-backed, SAF multi-file import (copied to app-private storage, sha256 dedup), Coil cover art, search, sort (Recent/Title/Author/Unread), group by author, adaptive grid columns (Auto + manual) and list view, multi-select with bulk actions, continue-reading hero.
- **Book details** — cover viewer (full-screen + save to gallery), metadata + edit, reading progress, a virtualized **table of contents** with persistent logical-chapter percentages and read/unread counts, multi-select read/unread (including "mark up to here"), and jump-to-chapter.
- **Reader** — EPUB rendered via **Readium** behind the app-owned reader boundary; locator persisted/restored; immersive chrome; contents, bookmarks, highlights, full-text search, dictionary/TTS, footnotes, advanced typography, custom themes/fonts, brightness, and scroll/paged layouts. Settings resolve as a global default overridden **per-book**.
- **Statistics** — streamlined overview and entry metrics with retained recent-reading history.
- **Settings / About**, a custom design system, and a component **DevGallery**.

**Not yet** — OPDS catalogs, non-EPUB formats, cross-device sync, Room FTS metadata search, baseline profiles, and reference-hardware performance acceptance. Fixture-backed reader benchmarks are implemented; see [`docs/reader-benchmarks.md`](docs/reader-benchmarks.md) and [`docs/roadmap.md`](docs/roadmap.md).

## Tech

Java 17 with core-library desugaring; Kotlin, Compose Material 3 Expressive, Hilt, Room, DataStore, Navigation Compose, Coil, and Readium. The application lives in `:app`, with a separate `:benchmark` test module. Pinned dependency versions live in `gradle/libs.versions.toml`; SDK levels and variants live in `app/build.gradle.kts`.

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

The `:app` module is layered `core/` → `data/`/`domain/` → `feature/`, with the EPUB engine confined to `data/reader/readium`. See [`docs/`](docs/README.md) for product, design, architecture, and roadmap guidance. Contributor guidance lives in [`AGENTS.md`](AGENTS.md).
