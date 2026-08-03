# Planning Context

## Intent
- Work on new `v2` branch created from clean `main` at `49dccb0` (`update animation`).
- Migrate the native Jetpack Compose app toward Material 3 Expressive and a recognizably native Android app feel.
- Improve the UI quality by adopting the platform's established Material language rather than designing another bespoke system from scratch.
- Preserve the mature reader/library behavior, offline-first model, Readium boundary, Room/DataStore data, and navigation unless the migration explicitly requires UI-only changes.

## Decisions
- Branch: `v2` (created locally; no prior local or remote `v2` branch existed).
- Current visual source of truth is the custom `core/designsystem` Yomu layer: CompositionLocal tokens and hand-built primitives.
- Material 3 is already a dependency and is imported by 32 Kotlin files, but `YomuDesignTheme`/Yomu primitives remain the app-level visual contract.
- The migration should make native Material 3 Expressive behavior and theming intentional, not merely add another dependency.
- User chose an incremental compatibility migration: Material 3 Expressive becomes the source of truth behind a temporary thin Yomu compatibility layer; screens migrate in stages and unused wrappers are removed later.
- Initial visual direction: native Android app feel, with Material 3 Expressive components, expressive motion/shapes, and dynamic/system-aware theming rather than another bespoke visual language.
- Prototype feedback: user wants a more conventional Material treatment, reducing custom/asymmetric shapes and moving closer to recognizable standard Android Material components while keeping the native feel.

## Constraints
- Native Android/Jetpack Compose app; current toolchain is the repository's Kotlin/AGP/Compose BOM setup.
- The app is mature: library, book details, reader, settings, annotations, search, statistics, import, and reader-engine integration must remain functional.
- `data/reader/readium` is the only Readium-importing package; UI changes must not leak Readium types.
- Existing Yomu names may have source/data compatibility value, so removal or renaming needs an explicit migration strategy.
- Visual work requires a prototype and user feedback before the final implementation plan is submitted.

## Open questions
- Which Material 3 Expressive APIs are available and stable in the repository's Compose BOM needs verification before implementation.
- Exact first-wave acceptance surface (app shell/library/settings/reader chrome versus every screen) needs confirmation after the visual direction is reviewed.
- Whether reader-specific surfaces retain only behavior-focused wrappers or also keep some custom visual treatment needs explicit acceptance during prototype review.
- User stopped further prototype iteration and asked implementation to proceed directly; the v2 baseline is an implementation starting point, and future screen-level redesign will be guided interactively.
- The resolved Material3 dependency currently marks `MaterialExpressiveTheme`, `MotionScheme`, and `expressiveLightColorScheme` as internal, so the implementation uses the public `MaterialTheme` entry point with dynamic Material colour roles, Material typography/shapes, native components, and the existing expressive motion bridge rather than calling inaccessible APIs.

## Discarded options
- Starting implementation directly on `main`: rejected; the user explicitly requested an isolated `v2` branch.
- Designing a second bespoke visual system: rejected; the stated goal is native Android feel via Material 3 Expressive.
- Treating the existing Material 3 imports as completion: rejected; the custom Yomu theme/primitives still define most product surfaces.
- A one-shot full replacement: rejected for now; it creates a large regression surface and removes the ability to validate each screen while the app is mature.
