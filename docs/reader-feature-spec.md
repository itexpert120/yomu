# Reader Feature Spec

This document defines the EPUB reader user experience and feature model. It intentionally separates desired product behavior from implementation details.

## Implementation status (current)

The reader is partly built. EPUB content renders through Readium's `EpubNavigatorFragment`, hosted in Compose by `feature/reader/ReaderNavigatorHost` and kept behind the Yomu-owned `ReaderEngine`/`ReaderSession` boundary (`core/reader`), with the Readium adapter in `data/reader/readium`. No Readium types leak past that boundary. The list below maps each area of this spec to its current state. The detailed sections that follow still describe the full product target; treat anything marked **Pending** as planned, not present.

| Area | Status | Notes |
| --- | --- | --- |
| EPUB rendering (paged + scrolled) | Done | Readium `EpubNavigatorFragment` in Compose; `ReaderLayout.{Paged, Scroll}`. |
| Position persistence + restore | Done | Locator saved to Room on each move, restored on reopen. |
| Reader chrome (permanent compact top bar) | Done | Sleek top bar: back + current chapter name + a controls/tune button. Clears the camera cutout. |
| Full-screen immersive (both system bars hidden) | Done | Swipe to reveal; bar/chrome/system-bar colours all match the reading theme. |
| Optional footer | Done | Toggleable clock + custom horizontal battery indicator + reading %; each element individually toggleable. Optional edge-shadow fade behind the bars. |
| Center-tap to open controls | Done | Center tap opens the controls sheet; edge taps fall through to Readium's default swipe navigation. **No custom navigation tap zones** (an earlier L-zone design was removed). |
| Controls sheet (Controls / Theme / Fonts tabs) | Done | Bottom sheet with in-sheet tab bar, cross-fade + animated height. |
| Progress scrubber | Done | Whole-book progress slider (drag to seek) with prev/next **chapter** arrows, in the Controls tab. |
| Theme presets + custom colours | Partial | Presets: Light / Dark (default, soft `#16181D`, non-OLED) / Sepia / Black / Custom. Custom exposes background + text colour pickers. The longer preset/background-pattern lists below are pending. |
| Brightness | Done | "Use system brightness" toggle or a manual slider (live preview while dragging, commit on release) applied via window `screenBrightness`. |
| Fonts | Partial | Scroll/paged toggle; font-family chips that preview each typeface (Lora default, Karla, Rubik, Cardo, Nunito, Merriweather; bundled TTFs registered with Readium); font-size slider. Per-role serif/sans/mono assignment and custom-font import are pending. |
| TOC | Partial | Flattened TOC extracted from the publication; read-state shown on the Book Details screen and auto-marked as the reader leaves a resource. No in-reader TOC panel yet. |
| Chapter read-tracking | Done | A chapter is auto-marked read when the reader moves from it to the next resource. |
| Reader settings model | Done | One `ReaderSettings`: global default (DataStore) overridden per-book (Room `reader_settings`); editing inside the reader writes that book's override (per-book-on-edit). |
| Engine navigation | Done | `goForward`/`goBackward`, next/previous chapter (reading order), `goToProgression` (nearest position). |
| Bookmarks | Pending | Not built. |
| Highlighting | Pending | Not built. |
| In-book search | Pending | Not built. |
| Advanced typography (paragraph/page panels) | Pending | Line/word/letter spacing, margins, text-align/justify, hyphenation, multi-column, image inversion not built. |
| Paragraph / Speed-reading modes | Pending | Not built. |
| TTS / read-aloud, reading statistics | Pending | Not built. |
| Quick actions, side panels, tablet multi-column | Pending | Not built. |

Note on link colour: this is intentionally **not** offered. Readium's `EpubPreferences` exposes only background and text colours, so the reader maps exactly those (plus scroll, font size, font family, line height, `publisherStyles=false`).

## Reader Screen Philosophy

The reader is the product's center. Text should own the screen. Controls should appear only when needed and should disappear quickly when the user returns to reading.

Default reader state (current):

- Full-screen reading canvas; both system bars hidden (swipe to reveal).
- No Material app bar. A permanent, compact "sleek" Yomu top bar (back + chapter name + controls button) is always present; it never sits over content and clears the camera cutout.
- Optional footer based on user settings (the top bar is not optional today).
- Center tap opens the controls sheet. (Planned: a tap-to-toggle-chrome model where the bar can hide.)
- Edge taps fall through to Readium's default swipe page navigation — there are no custom navigation tap zones (an earlier L-zone design was removed).
- Swipe/scroll behavior depends on reading mode: paged turns a page, scroll scrolls.
- Long press selects text.
- Progress scrubber lives in the controls sheet rather than a dedicated bottom gesture today.

## Reader Chrome

Current chrome:

- Permanent top strip: back button, current chapter name (resolved from the TOC by href, falling back to the locator title), and a controls/tune button that opens the sheet. No search/more menu yet.
- Optional footer: clock + custom horizontal battery indicator + reading %, each element toggleable. Optional edge-shadow fade behind the bars.
- Controls sheet (bottom sheet, Controls / Theme / Fonts tabs) standing in for the planned bottom dock + appearance studio.

Planned chrome (target, not yet built):

- Bottom dock: TOC, progress, brightness, appearance, bookmark, mode.
- Floating quick actions: bookmark, highlight, theme, reading mode.
- Progress scrubber overlay (currently the scrubber is inside the controls sheet).
- Side panels on tablet.

Chrome states (target model; today only the sheet open/closed and selection are realized):

- `Hidden`
- `Peek`
- `Visible`
- `PanelOpen`
- `SelectionActive`
- `Scrubbing`

## Reader Navigation

Actions (✓ = built):

- Previous page/position. ✓ (`goBackward`)
- Next page/position. ✓ (`goForward`)
- Previous/next chapter by reading order. ✓ (`previousChapter`/`nextChapter`)
- Seek by total progression. ✓ (`goToProgression`, snaps to the nearest known position)
- Restore last position. ✓ (locator persisted to Room, restored on reopen)
- Jump to TOC item. Planned (TOC is extracted but there's no in-reader TOC navigation panel yet).
- Jump to bookmark. Planned.
- Jump to highlight. Planned.
- Search within book. Planned.

Progress display:

- Percentage. ✓ (whole-book progress in footer and controls sheet)
- Stable positions where available. ✓ (Readium positions back the seek)
- Remaining pages estimate. Planned.
- Remaining time estimate. Planned.
- Current chapter progress. Planned (the top bar shows the chapter name, not its progress).

Important: for reflowable EPUB, true page count changes with device size, font, margins, and mode. Internally treat Readium positions/locators as canonical and present page counts as estimates where necessary.

## Reading Modes

### Paged Mode

Classic page-like navigation. **Built** (`ReaderLayout.Paged`).

Controls:

- Horizontal swipe (Readium default) turns the page. ✓
- Header (permanent) and footer (optional). ✓
- Edge tap previous/next: not implemented as a custom zone — edge taps fall through to Readium's swipe navigation.
- Page animation setting. Planned.
- Column settings on tablet. Planned.

### Scrolled Mode

Continuous vertical reading. **Built** (`ReaderLayout.Scroll`, the current default).

Controls:

- Vertical scroll. ✓
- Sticky minimal footer (optional). ✓
- Center tap opens the controls sheet. ✓
- Scroll progress indicator. Partial — whole-book % is shown in the footer.

### Paragraph Mode

Focused paragraph-by-paragraph reading.

Behavior:

- One paragraph or small section gets focus.
- Surrounding text is muted or hidden depending on setting.
- Navigation moves by paragraph.
- Useful for focus and study.

Implementation note:

- May require text extraction/content service rather than only Readium visual navigation.
- Build after normal reader is stable.

### Speed Reading Mode

Rapid serial visual presentation or guided chunk reading.

Behavior options:

- RSVP single word/chunk.
- Phrase chunks.
- Adjustable words per minute.
- Pause on punctuation.
- Tap/hold to pause.
- Resume from current locator.

Implementation note:

- Likely uses extracted text plus locator mapping.
- Should not be part of first EPUB integration milestone.

## TOC

Current state: the TOC is extracted from the publication and flattened (each entry keyed on its resource href, retaining nesting depth), exposed as `ReaderTocItem`. Read-state is surfaced on the **Book Details** screen and chapters are auto-marked read as the reader leaves a resource. There is **no in-reader TOC panel yet** — the panel below is the target.

TOC panel should show:

- Nested chapter hierarchy. (Depth is already captured; the panel is pending.)
- Current chapter highlight.
- Progress per chapter later if available.
- Search/filter within TOC later.

Phone:

- Full-height panel.

Tablet:

- Left side panel beside reading canvas.

Actions:

- Tap item to navigate.
- Collapse/expand nested items.
- Long press optional actions later.

## Brightness Control

Current state: brightness lives in the controls sheet's Theme tab. A "use system brightness" toggle defers to the OS; turning it off reveals a manual slider that previews live while dragging and commits on release. The level is applied via the window's `screenBrightness` attribute (not a dim overlay). A standalone quick vertical rail and a night-minimum mode are still planned.

Brightness controls:

- System brightness or manual override. ✓
- Live-preview slider (commit on release). ✓
- Quick vertical rail. Planned.
- Night minimum brightness mode. Planned.

## Progress Control

Current state: the scrubber is the whole-book progress slider in the controls sheet's Controls tab, flanked by previous/next **chapter** arrows, with a "% through the book" label. A separate always-available bottom scrubber overlay is still planned.

Progress controls:

- Progress slider (in the controls sheet). ✓
- Percentage label. ✓
- Prev/next chapter arrows. ✓
- Chapter label (shown in the top bar). ✓
- Remaining time/pages estimate. Planned.
- Preview tooltip. Planned.

Actions:

- Drag to seek. ✓
- Tap progress bar to seek. ✓
- Cancel seek / preview-before-commit. Planned.

## Font Settings

Current state: the controls sheet's **Fonts** tab offers a scroll/paged layout toggle, font-family chips that preview each typeface, and a font-size slider. Six bundled families are registered with Readium (Lora default, Karla, Rubik, Cardo, Nunito, Merriweather), with the TTFs in `assets/fonts/`. Per-role (serif/sans/mono) assignment and custom-font import are not built.

Settings:

- Font size. ✓
- Font family (single active family, live-previewed chips). ✓
- Default font (Lora). ✓
- Serif / sans-serif / monospace role slots. Planned.
- Manage custom fonts. Planned.

Font source types:

- Bundled app fonts. ✓
- Built-in generic / user-imported fonts. Planned.

Custom font management (all planned):

- Import `.ttf` / `.otf`.
- Show font preview. (Bundled chips already preview.)
- Delete custom font.
- Assign to serif/sans/mono roles.

## Paragraph Settings

Panel: `Paragraph` — **not built.** Of these, only line height is wired through `ReaderSettings.lineHeight` (and not yet surfaced in the UI); the rest are planned.

Settings:

- Paragraph margin.
- Line spacing.
- Word spacing.
- Letter spacing.
- Text indent.
- Full justification.
- Hyphenation.

Need inactive-state handling when:

- Fixed-layout EPUB does not support the setting.
- Publisher styles override the setting.
- Engine does not expose the setting.

## Page Settings

Panel: `Page` — **not built.** All settings below are planned.

Settings:

- Top margin.
- Bottom margin.
- Left margin.
- Right margin.
- Column gap.
- Max number of columns.
- Max column width.
- Max column height.
- Orientation preference.
- Paging animation.

Column behavior:

- Phone defaults to one column.
- Tablet can use one or two columns depending on width and user max width.
- Large landscape can support more columns only if readable.

## Header And Footer Settings

Panel: `Status` — partially built. These toggles live in the controls sheet's **Theme** tab today (a dedicated Status panel is planned). The header is currently permanent (no show/hide toggle).

Settings:

- Show footer. ✓
- Show reading progress (footer). ✓
- Show current time (footer clock). ✓
- Show current battery status (custom horizontal indicator). ✓
- Show chapter title (in the permanent top bar). ✓
- Show header (toggle). Planned — the top bar is always present.
- Tap to toggle footer. Planned.
- Show remaining time. Planned.
- Show remaining pages. Planned.
- Show book title. Planned (the top bar shows chapter, not book).

Rules:

- Header/footer must never dominate the reader.
- Footer should be compact and dim.
- Battery/time need permission-safe platform access.

## Theme Settings

Panel: `Theme` — partially built (controls sheet's **Theme** tab). The reader background, chrome, and (where applicable) system-bar colours all derive from the active theme so there is no seam.

Settings:

- Light theme. ✓
- Dark theme. ✓ (default = soft dark `#16181D`, deliberately non-OLED; a separate pure-black `Black` theme also exists)
- Sepia theme. ✓
- Custom: text color + background color pickers (`YomuColorPicker`). ✓
- Override book colors. ✓ implicitly — the engine is given `publisherStyles=false` plus explicit bg/text colours.
- Auto theme. Planned.
- Invert images in dark mode. Planned.
- Link color. **Intentionally not offered** — Readium's `EpubPreferences` exposes only background and text colours.
- Highlight colors. Planned (depends on highlighting).
- Background image/pattern. Planned.

Theme presets — current set is **Light / Dark / Sepia / Black / Custom**. The richer palette below is the target:

- Default.
- Gray.
- Sepia.
- Grass.
- Cherry.
- Sky.
- Solarized.
- Gruvbox.
- Nord.
- Contrast.
- Sunset.
- Custom.

Background presets — **not built**, all planned:

- Plain.
- Noise.
- Paper.
- Sand pattern.
- Moon.
- Custom image.

## Highlighting

**Not built** — entire feature is planned.

Highlight actions:

- Select text.
- Choose highlight color.
- Save highlight.
- Delete highlight.
- Jump to highlight.
- Edit note later.

Highlight colors:

- Yellow.
- Green.
- Blue.
- Pink.
- Purple/neutral optional.
- Custom later.

Storage:

- Store selected text snippet.
- Store locator/range where available.
- Store color.
- Store note later.
- Store creation/update timestamps.

## Bookmarks

**Not built** — entire feature is planned.

Bookmark actions:

- Toggle current location.
- Show marker in reader chrome.
- List bookmarks.
- Jump to bookmark.
- Delete bookmark.

Bookmark metadata:

- Book ID.
- Locator JSON.
- Progression.
- Chapter title.
- Text snippet if available.
- Created timestamp.

## Quick Actions

**Not built.** The controls sheet currently covers theme/font/progress; a customizable quick-action set is planned.

Quick action candidates:

- Bookmark.
- Highlight recent color.
- Brightness.
- Theme toggle.
- TOC.
- Font size minus/plus.
- Reading mode.
- Search.
- Lock orientation.

Rules:

- Keep default quick actions short.
- Let users customize later.
- Quick actions should not replace the full Appearance Studio.

## Library Management

Library views:

- Grid.
- List.
- Adjustable grid columns.
- Cover crop/fit.
- Group by author.
- Group by series.
- Group by custom group.
- Nested groups.

Library features:

- Search books.
- Add/import books.
- Group books.
- Create group under group.
- Sort books.
- Filter books.
- Show reading state.
- Show progress.

Sort options:

- Recently opened.
- Date added.
- Title.
- Author.
- Series.
- Progress.
- Unread/reading/finished.

Book states:

- Unread.
- Reading.
- Finished.
- Abandoned/archived later.

## Book Details

Book details should show:

- Cover.
- Title.
- Author(s).
- Series.
- Groups.
- Progress.
- Last opened.
- Date added.
- File info.
- TOC preview.
- Bookmarks.
- Highlights.
- Reader settings override entry.

## Search

Search scopes:

- Library title/author/series/group.
- Book content.
- TOC.
- Highlights/bookmarks.

Initial scope:

- Library metadata search.

Later (none built yet):

- In-book search using Readium search/content services.
- Library full-text indexing.

## Tablet-Specific Reader

**Not built.** The reader is currently a single full-screen layout with no side panels or multi-column; the layouts below are the tablet target.

Tablet reader layouts:

- Content only.
- Content plus left TOC.
- Content plus right Appearance Studio.
- Content plus notes/highlights panel later.
- Multi-column reading.

Tablet rules:

- Panels should not cover text unless screen is narrow.
- Page width must stay readable.
- Column count should be constrained by max column width, not screen width alone.

## Phone-Specific Reader

Phone reader layouts:

- Full-screen content.
- Bottom dock overlay.
- Full-height TOC panel.
- Full-height settings panel.
- Compact quick brightness rail.

Phone rules:

- Avoid persistent side controls.
- Use gestures conservatively.
- Make tap zones forgiving.

## Acceptance Criteria For First Reader Mock

This milestone is largely **superseded**: the reader now renders real EPUBs via Readium rather than a fake-content mock. Status of the original criteria:

- Content displays in paged and scrolled modes. ✓ (real EPUB, not fake content)
- Reader chrome behaves cleanly. ✓ (permanent top bar + optional footer; full chrome show/hide is still planned)
- TOC panel opens and closes. Pending — TOC is extracted but there's no in-reader panel yet.
- Appearance panel shows font/theme groups. Partial — Controls/Theme/Fonts tabs exist; paragraph/page/status groups are pending.
- Theme presets visually change the reader. ✓ (Light/Dark/Sepia/Black/Custom)
- Phone and tablet previews are intentionally different. Pending — one layout today.
- No visible default Material app-bar/bottom-bar identity remains. ✓
