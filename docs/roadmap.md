# Roadmap

This roadmap keeps Yomu from starting with EPUB complexity before the product language is clear.

**Overall status (current):** Phases 0–6 are complete — design system, real Room-backed library with SAF import, settings models, and the Readium integration are all done. Phase 7 (core reader) is largely done (themes, fonts, brightness, scroll/paged, chrome, persistent chapter read-state) with **bookmarks, highlights, in-book search, and an in-reader Contents panel still pending**. Phases 8–12 (advanced typography, library depth beyond author grouping, advanced reading modes, performance, optional expansion) are mostly not started.

## Phase 0: Project Baseline

Goal:

- Clean up the default template into a named Yomu app shell.

Deliverables:

- Package structure created.
- Default Material sample screen removed.
- Custom `YomuTheme` placeholder created.
- Fake data source added.
- Basic previews added.

Acceptance criteria:

- App launches to a Yomu-branded shell.
- No default Android greeting/template UI remains.

**Status: Complete.** Default template removed. Custom `YomuDesignTheme` with tokens, surfaces, controls, and cards is in place. Library screen with fake data launches. DevGallery activity for component validation.

## Phase 1: Design System Prototype

Goal:

- Establish the custom visual language before real EPUB work.

Deliverables:

- Foundation tokens.
- Surface primitives.
- Control primitives.
- Reader primitives.
- Library primitives.
- Phone and tablet previews.

Acceptance criteria:

- Reader mock does not look Material/default Android.
- Library mock has a strong custom identity.
- Tablet preview uses extra space intentionally.

**Status: Complete.** Foundation tokens (colors, typography, spacing, radius), surface primitives, control primitives (YomuButton, YomuChip, YomuSegmentedControl, YomuTogglePill, YomuColorSwatch, YomuColorPicker), cards (YomuCard, YomuSettingGroup), scaffold/header (YomuScreenHeader, YomuScreenScaffold), and the bottom sheet are implemented and used across the real library, book-details, reader, settings, and about screens — not just previews.

## Phase 2: Static Reader Mock

Goal:

- Validate reader UI, controls, themes, and settings using fake chapter text.

Deliverables:

- Fake paged reader.
- Fake scrolled reader.
- Reader chrome toggle.
- TOC panel.
- Progress scrubber.
- Appearance Studio panel.
- Theme presets applied to fake content.

Acceptance criteria:

- Paged/scrolled mock modes can be switched.
- Font/theme/page/status settings visibly affect fake reader.
- Reader chrome motion feels clean and non-intrusive.

**Status: Superseded — done with the real engine.** Rather than a fake reader, the reader was built directly on Readium (Phase 6). Paged/scrolled modes switch, font/theme/brightness/chrome settings visibly affect real content, and the custom chrome (permanent top bar, optional footer, controls sheet) is in place.

## Phase 3: Static Library Mock

Goal:

- Validate library browsing, grouping, search, and book detail structure.

Deliverables:

- Fake book grid/list.
- Adjustable grid columns.
- Cover crop/fit control.
- Search command surface.
- Sort/group controls.
- Author/series/group sections.
- Book details panel/screen.

Acceptance criteria:

- Library feels closer to a polished media app than a file picker.
- Tablet layout has sidebar, main content, and optional inspector.

**Status: Complete (now backed by real data).** Library grid/list, continue-reading hero, search, sort (Recent/Title/Author/Unread), group (None/Author), adaptive grid columns (Auto + manual override), multi-select with bulk actions, and a full book-details screen are all implemented and backed by Room rather than fake data.

## Phase 4: Settings And Theme Model

Goal:

- Build real settings models and persistence before connecting EPUB engine.

Deliverables:

- Reader settings Kotlin models.
- Theme preset models.
- Settings resolver.
- DataStore or fake repository persistence.
- Custom theme editing UI scaffold.

Acceptance criteria:

- Settings resolution supports global, theme, book, mode, and session layers.
- UI can show inactive/unsupported setting states.

**Status: Complete (simplified layering).** `ReaderSettings` (+ `ReaderLayout`/`ReaderThemeMode`/`ReaderFont`), `LibraryPreferences`, and app theme/accent models exist. Reader settings resolve as global default (DataStore) ⊕ per-book override (Room `reader_settings`), per-book-on-edit. Theme presets (Light/Dark/Sepia/Black/Custom with custom bg/text). The full global→theme→book→mode→session stack is collapsed to global + per-book for now.

## Phase 5: Data Layer And Import

Goal:

- Create the local library database and import pipeline.

Deliverables:

- Room schema.
- Repositories.
- SAF import.
- File copy to app-private storage.
- Duplicate hash check.
- Cover/metadata extraction spike.

Acceptance criteria:

- User can import an EPUB file into local library storage.
- Imported book appears in library with metadata/cover if available.
- Duplicate imports are detected.

**Status: Complete.** Room schema (`books`, `chapter_reads`, `reader_settings`) with migrations and schema export; `RoomBookRepository`; SAF multi-file import via `domain/imports/ImportBooksUseCase` copying into app-private storage with sha256 dedup; cover/metadata extraction through Readium; covers rendered with Coil.

## Phase 6: Readium Integration Spike

Goal:

- Prove Readium works with the architecture and custom UI.

Deliverables:

- Readium dependencies added.
- `ReaderEngine` interface.
- Readium implementation.
- Compose reader host for `EpubNavigatorFragment`.
- Open imported EPUB.
- Observe and persist current locator.
- Restore last locator.
- Basic TOC navigation.
- Basic progression seek.

Acceptance criteria:

- Real EPUB opens inside Yomu reader screen.
- Yomu chrome remains custom and Compose-owned.
- Progress persists after closing/reopening.
- TOC navigation works.

**Status: Complete.** `ReaderEngine`/`ReaderSession` interfaces with a Readium implementation (`data/reader/readium`, the only Readium importer); `EpubNavigatorFragment` hosted in Compose (`ReaderNavigatorHost`); locator persisted/restored; TOC extraction + jump-to-chapter; progression seek. Chrome is fully custom/Compose-owned.

## Phase 7: Core Reader Features

Goal:

- Make the reader useful for daily reading.

Deliverables:

- Bookmarks.
- Highlights.
- In-book search.
- Font settings mapped to engine preferences.
- Theme settings mapped to engine preferences.
- Scrolled/paged mode support.
- Header/footer status options.

Acceptance criteria:

- User can read, bookmark, highlight, resume, search, and customize appearance.
- Settings persist correctly.

**Status: Partial.** Done: font settings (6 bundled fonts with live previews + size), theme settings (Light/Dark/Sepia/Black/Custom bg+text), scrolled/paged, header/footer status options (clock + battery + progress, toggleable), resume, full-screen chrome. Also: persistent per-chapter read-state on the book-details TOC. **Pending: bookmarks, highlights, in-book search, and an in-reader Contents/Bookmarks panel.**

## Phase 8: Advanced Layout And Themes

Goal:

- Deliver the deep customization that differentiates Yomu.

Deliverables:

- Paragraph margin.
- Line spacing.
- Word spacing.
- Letter spacing.
- Text indent.
- Full justification.
- Hyphenation.
- Margins.
- Column gap/count/width/height.
- Background patterns/images.
- Image inversion in dark mode.
- Custom themes.
- Custom fonts.

Acceptance criteria:

- Advanced settings are organized and understandable.
- Unsupported settings show clear inactive state.
- Tablet multi-column reading is comfortable.

**Status: Mostly pending.** Custom themes and bundled fonts are done (custom user-supplied fonts are not). Advanced typography — line/word/letter spacing, text indent, justification, hyphenation, margins, multi-column, dark-mode image inversion — is not yet built.

## Phase 9: Library Management Depth

Goal:

- Make the library powerful for large collections.

Deliverables:

- Nested groups.
- Author grouping.
- Series grouping.
- Bulk group assignment.
- Sort/filter polish.
- Metadata editing later.
- Optional Room FTS indexing.

Acceptance criteria:

- User can manage a large local EPUB collection without feeling like they are managing files manually.

**Status: Partial.** Author grouping, sort/filter, search, and multi-select bulk actions are done. Nested groups, series grouping (intentionally removed for now), bulk group assignment, and optional Room FTS indexing are pending.

## Phase 10: Advanced Reading Modes

Goal:

- Add differentiated reader modes after normal reading is excellent.

Deliverables:

- Paragraph mode.
- Speed reading mode.
- Mode-specific settings.
- Locator synchronization.
- Reading statistics improvements.

Acceptance criteria:

- Paragraph/speed modes are reliable and resumable.
- Switching modes does not lose reading position.

**Status: Not started.**

## Phase 11: Performance And Polish

Goal:

- Make the app feel fast and premium.

Deliverables:

- Macrobenchmarks.
- Baseline profiles.
- Large library test fixture.
- Large EPUB test fixture.
- Animation tuning.
- Startup optimization.
- Reader open latency optimization.

Acceptance criteria:

- Library scrolling is smooth.
- Reader opens quickly enough for daily use.
- Settings/theme changes do not visibly jank.

**Status: Not started.**

## Phase 12: Optional Expansion

Possible later features:

- OPDS catalogs.
- PDF support.
- Audiobook support.
- TTS/read aloud.
- Sync.
- Export/import settings.
- Notes system.
- Reading goals/stats.
- LCP DRM if required.

These should not distract from the first EPUB reader product.
