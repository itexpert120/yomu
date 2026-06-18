# Design Language

Yomu should be a native Android app that does not look like a default Android app. The UI should be built from custom Compose primitives and guided by a reader-first product identity.

## Implementation status (current)

The custom design system is built and is the foundation of every product surface — it is no longer just a gallery prototype. It is applied across the real library, book-details, reader, settings, and about screens.

What exists in `core/designsystem`:

- Theming is delivered through CompositionLocals, not `MaterialTheme`. `YomuDesignTheme { }` provides colours/type/spacing/radius, accessed via the `YomuTheme` object (`YomuTheme.colors/type/space/radius`).
- Token data classes `YomuColors`, `YomuType`, `YomuSpacing`, `YomuRadius` (all `@Immutable`). Theme variants live in `YomuThemeMode { Light, Dark, Oled }`. (Note: the actual token sets differ in naming/coverage from the aspirational `YomuColor.*` / `YomuType.*` lists further down this doc — treat those lists as direction, the code as source of truth.)
- Built primitives: `YomuAppSurface` / `YomuPanel` / `YomuFloatingPanel` (`YomuSurfaces.kt`); `YomuButton` / `YomuChip` / `YomuSegmentedControl` / `YomuTogglePill` (`YomuControls.kt`, `YomuSettingsControls.kt`); `YomuCard` / `YomuSettingGroup` (`YomuCards.kt`); `YomuScreenHeader` / `YomuScreenScaffold` (`YomuScaffold.kt`); `YomuColorPicker` (HSV), `YomuColorSwatch`, `YomuBottomSheet`, plus reusable bits in `YomuReusable.kt`.
- `MainActivity` owns the window insets controller and flips status/nav-bar icon appearance on theme change.
- An `app/devgallery` harness validates primitives in isolation.
- There is now a real app launcher icon (`ic_yomu_mark` / adaptive icon).

The anti-Material rules below still hold: no Material `Scaffold`/`TopAppBar`/bottom sheet as product surfaces; `material3` is only a building block.

Reader/library design surfaces in place: a working EPUB reader with themes (incl. custom background/text colours), six bundled fonts with live previews, brightness, scroll/paged modes, and global + per-book settings; library with search/sort/group/multi-select; book details with virtualized TOC, per-chapter read state, and a cover viewer.

Still pending design work: bookmarks, highlights, in-book search, and advanced typography. Many of the aspirational primitive names listed below (e.g. `ReaderSurface`, `GlassPanel`, `SidePanel`, `HighlightMenu`, `AppearanceStudioPanel`, the full theme-preset families, and the named motion tokens) are not yet built — keep them as planned targets, not current API.

## Visual Direction

The target look is clean, restrained, responsive, and serious. It should feel closer to a premium reading/media tool than a generic utility app.

Influences:

- Codex: calm surfaces, focused work area, compact command panels, low noise.
- Spotify: strong library browsing, rich dark surfaces, confident density, fast controls.
- Telegram: highly custom native feel, responsive interactions, practical settings density.

Do not copy these products. Use them as proof that native Android can feel custom and branded.

## Design Principles

1. Reading canvas first.
2. Controls appear when needed and disappear when reading resumes.
3. Advanced settings must be deep but not chaotic.
4. Tablet layouts must be intentional, not stretched phone screens.
5. Dark and light themes must both feel first-class.
6. Motion must clarify structure, not decorate the screen.
7. Blur and glass are accents, not the whole identity.
8. Material defaults must not define the product shape.

## Anti-Material Rules

Avoid these in final visible UI:

- Default `Scaffold` layout behavior.
- Default Material top app bars.
- Default Material bottom bars.
- Default Material sliders for reader settings.
- Default Material switches for important settings.
- Default Material cards with standard rounded shape/elevation.
- Default purple theme tokens.
- Generic Android ripples when they clash with the custom language.

Allowed:

- Compose runtime/foundation/layout primitives.
- Material icons if restyled or replaced later.
- Material components as temporary scaffolding during early implementation.
- Material theme only as an internal compatibility bridge if needed.

## Foundation Tokens

Yomu-owned tokens are built and in use (see Implementation status — the shipped token shapes are `YomuColors` / `YomuType` / `YomuSpacing` / `YomuRadius`). The lists below are the original aspirational naming; the implemented tokens cover the same intent but differ in names and exact coverage.

Color:

- `YomuColor.Background`
- `YomuColor.Surface`
- `YomuColor.SurfaceRaised`
- `YomuColor.SurfaceSunken`
- `YomuColor.TextPrimary`
- `YomuColor.TextSecondary`
- `YomuColor.TextMuted`
- `YomuColor.Link`
- `YomuColor.Accent`
- `YomuColor.Danger`
- `YomuColor.HighlightYellow`
- `YomuColor.HighlightGreen`
- `YomuColor.HighlightBlue`
- `YomuColor.HighlightPink`

Typography:

- `YomuType.Display`
- `YomuType.Title`
- `YomuType.Section`
- `YomuType.Body`
- `YomuType.Reader`
- `YomuType.Caption`
- `YomuType.Mono`
- `YomuType.Control`

Spacing:

- `YomuSpace.xs`
- `YomuSpace.sm`
- `YomuSpace.md`
- `YomuSpace.lg`
- `YomuSpace.xl`
- `YomuSpace.page`
- `YomuSpace.panel`
- `YomuSpace.readerMargin`

Shape:

- `YomuRadius.none`
- `YomuRadius.xs`
- `YomuRadius.sm`
- `YomuRadius.md`
- `YomuRadius.lg`
- `YomuRadius.panel`
- `YomuRadius.pill`

Stroke:

- `YomuStroke.hairline`
- `YomuStroke.focus`
- `YomuStroke.divider`
- `YomuStroke.selection`

Motion:

- `YomuMotion.quick`
- `YomuMotion.standard`
- `YomuMotion.slow`
- `YomuMotion.panelEnter`
- `YomuMotion.panelExit`
- `YomuMotion.readerChrome`
- `YomuMotion.pageTurn`

## Surface Primitives

A core subset is built (`YomuAppSurface`, `YomuPanel`, `YomuFloatingPanel`); the remaining entries below are still planned.

- `YomuAppSurface`: root app surface with theme-aware background. (built)
- `ReaderSurface`: book reading canvas; supports color, image, noise, paper, and pattern backgrounds.
- `LibrarySurface`: browsing area with richer cover-forward treatment.
- `PanelSurface`: opaque settings/details panel.
- `FloatingPanel`: elevated translucent panel for reader controls.
- `CommandPanel`: compact command/search surface.
- `GlassPanel`: optional blurred panel for dark immersive reader chrome.
- `SidePanel`: tablet TOC/settings/inspector panel.
- `BottomDock`: reader action dock.
- `ScrimLayer`: custom dimming layer for focused controls.
- `BookPageSurface`: simulated page/paper surface when page mode wants a contained page.

## Control Primitives

Reader settings need controls that feel custom.

- `YomuButton`
- `YomuIconButton`
- `YomuIconAction`
- `YomuTogglePill`
- `YomuSegmentedControl`
- `YomuRangeControl`
- `YomuStepper`
- `YomuSliderRail`
- `YomuVerticalBrightnessRail`
- `YomuProgressScrubber`
- `YomuColorSwatch`
- `YomuThemeSwatch`
- `YomuFontPreviewCard`
- `YomuSettingRow`
- `YomuSettingGroup`
- `YomuQuickActionChip`
- `YomuSearchField`

## Reader Primitives

- `ReadingCanvas`
- `ReaderChrome`
- `ReaderHeader`
- `ReaderFooter`
- `ReaderBottomDock`
- `ReaderQuickActions`
- `ReaderProgressOverlay`
- `TOCPanel`
- `BookmarkMarker`
- `HighlightMenu`
- `SelectionToolbar`
- `AppearanceStudioPanel`
- `ReadingModeSwitcher`

## Library Primitives

- `BookCover`
- `BookCard`
- `BookListRow`
- `BookGrid`
- `LibraryRail`
- `LibrarySidebar`
- `GroupCard`
- `SeriesRow`
- `AuthorSection`
- `SortControl`
- `SearchCommandSurface`
- `ImportDropZone`
- `BookInspectorPanel`

## Theme Presets

Built-in theme families:

- Default
- Gray
- Sepia
- Grass
- Cherry
- Sky
- Solarized
- Gruvbox
- Nord
- Contrast
- Sunset
- Custom

Each theme should define both light and dark variants:

- Reader text color
- Reader background color
- Reader link color
- App surface color
- Panel surface color
- Muted text color
- Accent color
- Highlight color set
- Optional image inversion behavior
- Optional background texture/pattern

Background modes:

- Plain
- Noise
- Paper
- Sand pattern
- Moon
- Custom image

## Typography Direction

Reader typography is separate from app chrome typography.

App chrome:

- Use a modern, compact sans or humanist sans.
- Avoid default Android font stack as the only identity.
- Keep labels crisp and command-like.

Reader content:

- Support default, serif, sans-serif, monospace, and custom fonts.
- Favor high readability and long-session comfort.
- Provide preview cards with real paragraph samples.

Bundled reader fonts (shipped):

- Six families live in `app/src/main/assets/fonts/` and are registered with Readium: Lora, Karla, Rubik, Cardo, Nunito, and Merriweather. Each is exposed in the reader with a live preview.

The original evaluation shortlist (Literata, Atkinson Hyperlegible, IBM Plex Sans, JetBrains Mono) was not adopted; revisit only if the reader needs accessibility/mono coverage beyond the current set. Licensing must be checked before bundling any additional fonts.

## Layout Behavior

Phone:

- Full-screen reader.
- Tap center toggles chrome.
- Bottom dock and sheets overlay content.
- TOC/settings use full-height panels.
- Library defaults to cover grid with search command surface.

Tablet:

- Reader can show side panels without covering the reading column.
- Library uses left sidebar, main grid/list, optional right inspector.
- Appearance settings can sit side-by-side with live reader preview.
- Multi-column reading becomes a first-class mode.

Large/foldable/desktop window:

- Respect window size and posture.
- Avoid locked orientation as a primary design crutch.
- Prefer adaptive component placement over entirely separate screens.

## Motion System

Core transitions:

- Reader chrome fade/slide.
- Bottom dock reveal.
- Side panel slide and settle.
- Settings group expand/collapse.
- Theme preview crossfade.
- Library grid reflow.
- Page turn: none, slide, fade initially; curl-like only later.
- Bookmark marker pulse.
- Highlight color picker reveal.

Rules:

- Motion duration should be short and intentional.
- Reader text should never feel unstable during normal reading.
- Disable or reduce motion when system reduced-motion settings apply.

## Blur Usage

Use blur sparingly:

- Reader chrome over image/pattern backgrounds.
- Library inspector overlay.
- Command/search overlay.

Avoid:

- Blurring every panel.
- Low-contrast glass panels over text.
- Expensive blur on scrolling surfaces if it harms performance.

## Accessibility And Comfort

Required design support:

- Scalable type.
- High contrast theme.
- Reduced motion behavior.
- Large touch targets for reader controls.
- Clear selected/focused states.
- Screen-reader labels for controls.
- Avoid controls that depend only on color.
- Respect RTL where engine support allows.

## Design Validation Checklist

A screen is not ready if:

- It looks like a default Android template.
- The reader content is visually secondary to controls.
- Tablet just stretches the phone layout.
- Text controls are hidden behind one huge settings dump.
- A theme looks good only in dark mode.
- Animations make reading feel unstable.
