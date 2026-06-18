# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

Yomu is a native Android EPUB reader (Kotlin + Jetpack Compose). The product intent is a polished, reader-first, tablet-optimized app that deliberately does **not** look like a default Material app. See `docs/` for the full product/design/architecture specs — `docs/README.md` is the entry point.

Current state: an early static prototype. There is one `:app` module containing a custom design system and a static library screen backed by fake data. **Readium, Room, DataStore, Hilt, and Navigation are planned but not yet present** — `docs/` describes the target architecture, not the current code. Treat package/module layouts in the docs as the destination, not what exists today.

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

Toolchain: AGP 9.2.1, Kotlin 2.4.0, Compose BOM 2026.06.00, Java 11. `compileSdk 37 / minSdk 24 / targetSdk 36`.

## Architecture

### Package layout (inside the single `:app` module)
```
com.itexpert120.yomu
├── MainActivity.kt              # entry point; sets edge-to-edge, hosts YomuLibraryApp
├── app/                         # app shell: EdgeToEdge, devgallery (component preview harness)
├── core/designsystem/           # the custom design system (Yomu* primitives)
└── feature/library/             # the library screen + its models/overlays
```
Planned but absent: `core/{model,database,datastore,storage,reader}`, `data/*`, `domain/*`, `feature/{reader,settings,appearance,bookdetails,search}`. Create these as features land, following `docs/app-architecture.md`.

### Design system is the foundation — use it, don't reach for Material
`core/designsystem` defines the visual language. Theming is delivered through **CompositionLocals**, not `MaterialTheme`:
- `YomuDesignTheme { ... }` wraps the app and provides colors/type/spacing/radius.
- Access tokens inside composables via the `YomuTheme` object: `YomuTheme.colors`, `YomuTheme.type`, `YomuTheme.space`, `YomuTheme.radius`.
- Token data classes: `YomuColors`, `YomuType`, `YomuSpacing`, `YomuRadius` (all `@Immutable`). Theme variants: `YomuThemeMode.{Light, Dark, Oled}`.
- Build UI from the `Yomu*` primitives in `YomuSurfaces.kt`, `YomuControls.kt`, `YomuCards.kt`, `YomuReusable.kt` (e.g. `YomuAppSurface`). Do **not** introduce Material `Scaffold`/`MaterialTheme`/raw Material components as the app shell or product surface. `material3`/`material-icons-extended` are on the classpath only as building blocks for the custom system.
- The design system package must not depend on `feature/*`.

### Theme ↔ system bars
`MainActivity` owns the window insets controller and flips status/nav bar icon appearance via the `onThemeModeChange` callback threaded down through `YomuLibraryApp`. When theme mode changes, that callback must fire so bar icons stay legible.

### Reader engine boundary (when it lands)
The EPUB engine will be Readium, but Readium types must **not** leak. All reader access goes through Yomu-owned interfaces (`ReaderEngine`, `ReaderSession`, `ReaderLocator`, etc.); only a future `data/reader/readium` package may import Readium directly. Readium navigators are Fragments hosted inside Compose behind a thin interop host. See `docs/app-architecture.md` and `docs/reader-feature-spec.md`.

## Conventions

- Compose state flows down (immutable UI-state data classes), events flow up (explicitly named `On*` events). Keep composables stateless except for small ephemeral UI state.
- Do not pass repositories into composables, and do not run import/database/DataStore/Readium work from composables.
- Name composables by product role, not the widget they render.
- Comment only non-obvious behavior.
- Use `@Preview` composables for design iteration; the `app/devgallery` gallery exists to validate design primitives in isolation.

## Notes

- `index.html`, `script.js`, `styles.css` at the repo root are an exported IntelliJ inspection report — not application code; ignore them.
- `docs/roadmap.md` defines implementation phases and acceptance criteria; consult it before starting a new feature area.
