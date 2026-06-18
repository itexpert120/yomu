# Reader Feature Spec

This document defines the EPUB reader user experience and feature model. It intentionally separates desired product behavior from implementation details.

## Reader Screen Philosophy

The reader is the product's center. Text should own the screen. Controls should appear only when needed and should disappear quickly when the user returns to reading.

Default reader state:

- Full-screen reading canvas.
- No permanent Android app bar.
- Optional header/footer based on user settings.
- Tap center toggles chrome.
- Edge taps navigate in paged mode.
- Swipe/scroll behavior depends on reading mode.
- Long press selects text.
- Bottom progress gesture opens scrubber.

## Reader Chrome

Visible chrome includes:

- Top strip: back, title/chapter, search or more.
- Bottom dock: TOC, progress, brightness, appearance, bookmark, mode.
- Floating quick actions: bookmark, highlight, theme, reading mode.
- Progress scrubber overlay.
- Side panels on tablet.

Chrome states:

- `Hidden`
- `Peek`
- `Visible`
- `PanelOpen`
- `SelectionActive`
- `Scrubbing`

## Reader Navigation

Actions:

- Previous page/position.
- Next page/position.
- Jump to TOC item.
- Jump to bookmark.
- Jump to highlight.
- Seek by total progression.
- Search within book.
- Restore last position.

Progress display should support:

- Percentage.
- Stable positions where available.
- Remaining pages estimate.
- Remaining time estimate.
- Current chapter progress later.

Important: for reflowable EPUB, true page count changes with device size, font, margins, and mode. Internally treat Readium positions/locators as canonical and present page counts as estimates where necessary.

## Reading Modes

### Paged Mode

Classic page-like navigation.

Controls:

- Edge tap previous/next.
- Horizontal swipe.
- Page animation setting.
- Column settings on tablet.
- Header/footer optional.

### Scrolled Mode

Continuous vertical reading.

Controls:

- Vertical scroll.
- Scroll progress indicator.
- Optional sticky minimal footer.
- Tap to show chrome.

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

TOC panel should show:

- Nested chapter hierarchy.
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

Brightness controls:

- Quick vertical rail.
- System brightness or app overlay dimming option.
- Night minimum brightness mode later.

Initial recommendation:

- Start with app-level dim overlay because it is simpler and does not require system brightness mutation.
- Consider system brightness later if product needs it.

## Progress Control

Progress controls:

- Bottom scrubber.
- Percentage label.
- Chapter label.
- Remaining time/pages estimate.
- Preview tooltip later.

Actions:

- Drag to seek.
- Tap progress bar to seek.
- Cancel seek returns to previous position until committed if preview mode is added.

## Font Settings

Panel: `Font`

Settings:

- Font size.
- Font family.
- Default font.
- Serif font.
- Sans-serif font.
- Monospace font.
- Manage custom fonts.

Font source types:

- Built-in generic.
- Bundled app fonts.
- User-imported fonts.

Custom font management:

- Import `.ttf` / `.otf`.
- Show font preview.
- Delete custom font.
- Assign to serif/sans/mono roles.

## Paragraph Settings

Panel: `Paragraph`

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

Panel: `Page`

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

Panel: `Status`

Settings:

- Show header.
- Show footer.
- Tap to toggle footer.
- Show remaining time.
- Show remaining pages.
- Show reading progress.
- Show current time.
- Show current battery status.
- Show chapter title.
- Show book title.

Rules:

- Header/footer must never dominate the reader.
- Footer should be compact and dim.
- Battery/time need permission-safe platform access.

## Theme Settings

Panel: `Theme`

Settings:

- Auto theme.
- Dark theme.
- Light theme.
- Override book colors.
- Invert images in dark mode.
- Text color.
- Background color.
- Link color.
- Highlight colors.
- Background image/pattern.

Theme presets:

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

Background presets:

- Plain.
- Noise.
- Paper.
- Sand pattern.
- Moon.
- Custom image.

## Highlighting

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

Later:

- In-book search using Readium search/content services.
- Library full-text indexing.

## Tablet-Specific Reader

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

- Fake content can display in paged-like and scrolled-like screens.
- Reader chrome toggles cleanly.
- TOC panel opens and closes.
- Appearance panel shows font, paragraph, page, status, and theme groups.
- Theme presets visually change the reader mock.
- Phone and tablet previews are intentionally different.
- No visible default Material app-bar/bottom-bar identity remains.
