# Roadmap

This roadmap keeps Yomu from starting with EPUB complexity before the product language is clear.

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

**Status: Partially complete.** Foundation tokens (colors, typography, spacing, radius), surface primitives (YomuAppSurface, YomuPanel, YomuFloatingPanel, ReaderPageSurface), and control primitives (YomuButton, YomuChip, YomuSegmentedControl, YomuRangeRow, YomuColorSwatch) are implemented. Library primitives (YomuBookCard, YomuSettingGroup) exist. Reader and remaining library primitives are pending.

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

**Status: Partially complete.** Static library screen with fake books, continue-reading section, shelf sections, book context panel, and system bar scrim is implemented. Search, sort/group controls, adjustable grid columns, and book details are pending.

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
