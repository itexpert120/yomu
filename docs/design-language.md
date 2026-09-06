# Design Language

Open Reader should feel like a native Android app while keeping a reader-first identity. The v2 UI follows Material 3 Expressive principles — a fixed app-owned palette by default, optional dynamic colours for light and dark modes, clear Material roles, adaptive layouts, accessible native interactions, purposeful motion, and expressive typography/shapes — rather than inventing a second visual language.

## Implementation status (current)

The Material 3 Expressive foundation is now active across the shared design system, library, book details, reader chrome, settings, statistics, and supporting screens. `YomuDesignTheme` owns the resolved light, dark, dynamic, and OLED schemes; Yomu locals remain a compatibility bridge while feature call sites converge on shared components.

What exists in `core/designsystem`:

- `YomuDesignTheme { }` now owns `MaterialExpressiveTheme` with explicit light/dark/OLED colour schemes, optional Android 12+ dynamic colours, expressive Material motion, tuned Material typography/shapes, and Yomu CompositionLocals as a temporary compatibility bridge (`YomuTheme.colors/type/space/radius`).
- Token data classes `YomuColors`, `YomuType`, `YomuSpacing`, `YomuRadius` (all `@Immutable`). Theme variants live in `YomuThemeMode { Light, Dark, Oled }`. (Note: the actual token sets differ in naming/coverage from the aspirational `YomuColor.*` / `YomuType.*` lists further down this doc — treat those lists as direction, the code as source of truth.)
- Shared primitives retain their Yomu names for call-site compatibility, but now delegate to native Material components where equivalents exist: `Button`, `FilterChip`, `PrimaryTabRow`, `Switch`, `Slider`, `OutlinedTextField`, `Card`, `TopAppBar`, `AlertDialog`, `ExtendedFloatingActionButton`, `ListItem`, `NavigationBar`, `NavigationRail`, and `ModalBottomSheet`. Shared labeled actions and settings rows add consistent semantics and touch targets. The HSV picker, EPUB canvas, and cover gestures remain custom because Material has no equivalent.
- `MainActivity` owns the window insets controller and flips status/nav-bar icon appearance on theme change.
- An `app/devgallery` harness validates primitives in isolation.
- There is now a real app launcher icon (`ic_yomu_mark` / adaptive icon).

The initial migration deliberately uses native Material product surfaces. Reader pages and reader-specific colour themes remain custom where EPUB comfort requires it, while chrome and supporting surfaces use Material semantics, touch targets, ripples, fixed Material 3 colour roles by default, optional Android 12+ dynamic colours for both light and dark modes, and adaptive sheet patterns.

Reader/library design surfaces in place: a working EPUB reader with themes (incl. custom background/text colours), six bundled fonts with live previews, brightness, scroll/paged modes, and global + per-book settings; library with search/sort/group/multi-select; book details with virtualized TOC, per-chapter read state, and a cover viewer.

Expressive refinement now starts with Library: a collapsing heading and labeled search/import actions. Shared buttons use Material's expressive press shapes and size-aware typography/padding; the theme explicitly selects expressive motion. Resume remains a floating action button with its scroll-responsive label; there is no large Continue Reading card. Existing collection modes remain available. Rendered device validation remains separate from compilation.

Settings adds visual light/dark/system choices, closely grouped tonal appearance rows and tablet navigation, and a discoverable Wallpaper colors option. Shared mutually exclusive settings use connected Material toggle shapes with radio semantics and wrapping labels. Existing appearance defaults, stored preferences, and OLED behavior are preserved.

Statistics, Settings, and About share Library's large padded, collapsing Material heading. Theme choices and Reader chrome use the same grouped tonal list treatment as Appearance, including native radio/switch semantics. Wallpaper colors has no leading icon. About facts and policy sections also use tonal list surfaces.

Padded top bars keep fixed expanded/background and collapsed/surface-container color endpoints. Material interpolates between them from the bar's collapsed fraction; separate content-scroll flags and delayed elevation animations must not override the expanded color.

Library search is a persistent rounded field with live results, an inline clear action, and a result count. Import stays in the top bar, and keyboard search dismisses the keyboard without discarding the query. Back retains the existing search-exit behavior.

Book details uses a tonal identity surface, stronger book-title typography, a 128dp compact cover and the existing 176dp tablet cover. Large text stacks the identity to retain readable width. Read/Resume stays a FAB, and chapter navigation remains a virtualized, unkeyed list.

Book Details header actions use expressive tonal icon buttons. Edit stays visible; Book options contains Reading timeline and the existing confirmed removal action. The description uses one content inset without a nested card; its text height animates so Show more/less follows the expansion. Progress labels wrap when needed, and Contents has a stronger heading with a tonal sort control.

Statistics emphasizes lifetime total reading time in a tertiary container, using the existing aggregate value. Supporting metrics share an adaptive grid whose columns respond to available width and font scale; values wrap rather than truncate. Tablet overview/history panes and all existing metric definitions are retained.

Reading chrome uses Material expressive tonal actions for Browse, Search, and Display. Their colors derive from the reading palette, while Theme and Reader chrome preferences reuse grouped settings rows. The EPUB page, saved themes, and reading ownership remain unchanged.

The bottom controls have no border and join the visible footer as one reading-colored surface. With the footer disabled, the controls float with 12dp side/bottom spacing, additional visible system-bar/cutout clearance, and rounded bottom corners. An opaque reading-colored backing covers the spacing so EPUB text cannot show below the controls.

The reader title bar uses native-height Material layout, compact title-medium chapter typography, and expressive tonal back/bookmark controls. The bookmark's checked state uses the reading palette's inverse pair. Non-immersive page padding follows its complete measured height, including the solid status/cutout area, immediately on phones, tablets, and font-size changes. Readium's additional native cutout padding is disabled because it reads the window inset directly and would duplicate that reservation. Immersive chrome still overlays the page without reflowing it.

Home uses Material ShortNavigationBar and WideNavigationRail with primary-container selection indicators, stronger selected labels, and native navigation-item motion. Screen navigation retains the original short shared-axis X transition and fade-through timing in both directions, including the return from About. Tab/content swaps retain their original restrained spring. Material components use the expressive motion scheme, with reduced-motion handling preserved.

## Visual Direction

The target look is conventional Material 3 Expressive with a calm, reader-focused hierarchy. It should feel recognizably Android on phones and first-class on tablets, without becoming a generic utility dashboard.

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
8. Material roles and defaults should define the interaction foundation; product-specific reader surfaces may layer on top only when reading comfort requires it.

## Material 3 Expressive Rules

Use these as the baseline for every redesign:

- Prefer native Material components and semantics for app chrome, controls, dialogs, lists, cards, sheets, and navigation.
- Use the app-owned static Material 3 light/dark colour schemes by default. Dynamic wallpaper colours are an explicit Android 12+ opt-in that applies to both modes; custom accent controls are not exposed.
- Use the Material typography hierarchy, readable line lengths, clear emphasis, and expressive type only where it improves the reading task.
- Use role-specific shapes: generous featured containers, connected choices, and expressive pressed/selected controls. Preserve compact book covers and use shape changes to communicate interaction and hierarchy.
- Use native touch targets, focus/selection semantics, ripples, reduced-motion support, and accessible contrast.
- Make compact, medium, and expanded layouts intentional. Tablets should gain side-by-side context and navigation rather than merely stretching phone content.
- Keep the EPUB reading canvas and saved reader themes distinct from app chrome when custom page colours/fonts are required for reading comfort.

Avoid:

- Bespoke replacements for a Material component that already satisfies the interaction.
- Decorative motion, excessive elevation, or shape changes that compete with the text.
- Treating a tablet as a scaled phone or letting a wide reading surface become uncomfortable to scan.

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
- Material `primary` and `secondary` roles from the active system scheme
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
- Material primary/secondary roles
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

- Navigation rails activate at 600dp of available width; phone layouts remain unchanged below that breakpoint.
- Shared two-pane layouts activate at 720dp of actual content width. The leading pane receives half the width up to 450dp and the trailing pane receives the remainder; both panes own their scroll state and follow RTL layout direction.
- Library, Settings, Book Details, and Statistics add context with side-by-side panes instead of stretching a phone column.
- Reader Browse, Search, and short actions use centered tablet dialogs capped at 460dp; no tablet sheet stretches to a percentage of the landscape window.
- The library supports Comfortable, Compact, Cover-only, and List modes with independent portrait and landscape column preferences.

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
